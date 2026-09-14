package com.qros.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.UserRole;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.JwtIssuer;

/**
 * Đăng nhập nhân viên và xoay vòng phiên ({@code FR-AUTH-01}, {@code FR-AUTH-02}, {@code FR-AUTH-03},
 * {@code FR-AUTH-05}, {@code TM-AUTH-01}, {@code TM-AUTH-03}).
 *
 * <p>Bốn điểm cưỡng chế đúng tiêu chí nghiệm thu của {@code BL-M0-08}/{@code BL-M0-09}:
 * <ul>
 *   <li><b>Argon2id đúng tham số</b> — {@code m=64 MiB, t=3, p=4};</li>
 *   <li><b>lỗi không liệt kê email</b> — email không tồn tại và mật khẩu sai của email có thật trả
 *       về cùng một {@code INVALID_CREDENTIALS} sau cùng một khoảng thời gian xử lý;</li>
 *   <li><b>khoá luỹ tiến</b> — uỷ quyền hẳn cho {@link AppUser#registerFailedAttempt(Instant)};</li>
 *   <li><b>MFA bắt buộc khi đã bật</b> — sai TOTP tính là một lần sai như sai mật khẩu (cùng đường
 *       khoá luỹ tiến), nếu không TOTP sẽ là kênh dò không giới hạn số lần thử.</li>
 * </ul>
 *
 * <p>Token phát ra chỉ thuộc vùng **staff** (audience {@code qros-staff}), bất kể vai trò — xem
 * {@code OPEN-07}. Mỗi lượt đăng nhập/refresh thành công phát cả access token (cookie
 * {@code qros_session}, 15 phút) lẫn refresh token (cookie {@code qros_refresh}, xem
 * {@link RefreshTokenService}).
 *
 * <p>Chỉ nạp khi có {@code DataSource} — cùng lý do với {@code OutboxConfiguration}.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class AuthenticationService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    /** Đại diện phần "toàn tổ chức" trong claim {@code stores} khi {@code user_role.store_id} null. */
    private static final String ORGANIZATION_WIDE = "*";

    /** {@code FR-AUTH-02}: hai vai trò này phải bật MFA trước khi được cấp quyền ghi. */
    private static final Set<String> ROLES_REQUIRING_MFA = Set.of("STORE_MANAGER", "ADMIN");

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtIssuer staffJwtIssuer;
    private final MfaService mfaService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final Argon2PasswordEncoder passwordEncoder =
            new Argon2PasswordEncoder(16, 32, 4, 65536, 3);
    // Hash hợp lệ nhưng không khớp mật khẩu nào — chạy Argon2id thật trên nó khi email không tồn
    // tại, để thời gian phản hồi giống hệt nhánh "email có thật nhưng sai mật khẩu".
    private final String decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());

    public AuthenticationService(AppUserRepository appUserRepository,
            UserRoleRepository userRoleRepository, JwtIssuer staffJwtIssuer, MfaService mfaService,
            RefreshTokenService refreshTokenService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.staffJwtIssuer = staffJwtIssuer;
        this.mfaService = mfaService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    // noRollbackFor có chủ ý: QrosException ở đây thường đi kèm một thay đổi đã lưu hợp lệ
    // (failed_attempts/lockout_count tăng lên) mà bản thân sự thay đổi đó KHÔNG sai — cái sai là
    // mật khẩu/TOTP. Rollback mặc định của Spring trên RuntimeException sẽ xoá luôn lần ghi nhận
    // sai đó, khiến khoá luỹ tiến không bao giờ tích luỹ được.
    @Transactional(noRollbackFor = QrosException.class)
    public StaffSession login(String email, String rawPassword, String totp) {
        Instant now = Instant.now(clock);
        Optional<AppUser> found = appUserRepository.findByEmailAndActiveTrue(email);
        AppUser user = found.orElse(null);

        if (user != null && user.isLocked(now)) {
            throw new QrosException(ErrorCode.ACCOUNT_LOCKED);
        }

        String hashToVerify = (user != null && user.getPasswordHash() != null)
                ? user.getPasswordHash() : decoyHash;
        boolean khopMatKhau = passwordEncoder.matches(rawPassword, hashToVerify);
        boolean hopLe = user != null && user.getPasswordHash() != null && khopMatKhau
                && (!user.isMfaEnabled() || mfaService.verifyTotp(user, totp));

        if (!hopLe) {
            if (user != null) {
                boolean vuaBiKhoa = user.registerFailedAttempt(now);
                appUserRepository.save(user);
                if (vuaBiKhoa) {
                    throw new QrosException(ErrorCode.ACCOUNT_LOCKED);
                }
            }
            throw new QrosException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.registerSuccessfulLogin();
        appUserRepository.save(user);

        return issueSession(user);
    }

    /**
     * @see RefreshTokenService#rotate(String) — phần "thất bại theo hai cách khác nhau" nằm ở đó.
     */
    // noRollbackFor bắt buộc ở CẢ HAI đầu: propagation REQUIRED của rotate() nhập chung giao dịch
    // đã mở ở đây, nên quy tắc rollback thật sự áp dụng là của phương thức MỞ giao dịch — tức là
    // hàm này. Thiếu dòng này ở đây thì noRollbackFor bên RefreshTokenService.rotate() vô nghĩa:
    // ngoại lệ đi qua lớp chặn giao dịch của refresh() vẫn bị đánh dấu rollback-only theo mặc định.
    @Transactional(noRollbackFor = QrosException.class)
    public StaffSession refresh(String rawRefreshToken) {
        IssuedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        AppUser user = appUserRepository.findById(rotated.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new QrosException(ErrorCode.UNAUTHENTICATED));

        String accessToken = staffJwtIssuer.issue(user.getId().toString(), ACCESS_TOKEN_TTL,
                claimsFor(user));
        return new StaffSession(accessToken, ACCESS_TOKEN_TTL, rotated);
    }

    private StaffSession issueSession(AppUser user) {
        String accessToken = staffJwtIssuer.issue(user.getId().toString(), ACCESS_TOKEN_TTL,
                claimsFor(user));
        IssuedRefreshToken refreshToken = refreshTokenService.issueNewFamily(user.getId());
        return new StaffSession(accessToken, ACCESS_TOKEN_TTL, refreshToken);
    }

    private Map<String, Object> claimsFor(AppUser user) {
        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(user.getId());

        Map<String, Object> claims = new LinkedHashMap<>();
        // Zone yêu cầu authority "SCOPE_staff" — JwtGrantedAuthoritiesConverter tách chuỗi này
        // theo khoảng trắng, mỗi phần thành một authority (StaffSecurityConfig, JwtAuthenticationConverter).
        claims.put("scope", "staff");
        // FR-AUTH-05: token cũ cầm token_version cũ bị CookieSessionAuthenticationFilter từ chối.
        claims.put("tv", user.getTokenVersion());
        claims.put("stores", storesOf(user));
        // FR-AUTH-02: MfaEnforcementFilter đọc claim này để chặn ghi.
        claims.put("mfaBlocked", canRequireMfa(roleCodes) && !user.isMfaEnabled());
        return claims;
    }

    private static boolean canRequireMfa(List<String> roleCodes) {
        return roleCodes.stream().anyMatch(ROLES_REQUIRING_MFA::contains);
    }

    /** Phạm vi tổ chức/chi nhánh của người dùng — tiêu chí nghiệm thu "phạm vi tổ chức/chi nhánh". */
    private List<String> storesOf(AppUser user) {
        return userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getStoreId)
                .map(storeId -> storeId != null ? storeId.toString() : ORGANIZATION_WIDE)
                .distinct()
                .toList();
    }
}
