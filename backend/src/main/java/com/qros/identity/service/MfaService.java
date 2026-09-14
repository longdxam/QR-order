package com.qros.identity.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.MfaBackupCode;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.MfaBackupCodeRepository;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.OpaqueTokenHashes;
import com.qros.shared.security.Totp;

/**
 * MFA TOTP và mã dự phòng — {@code FR-AUTH-02}.
 *
 * <p><b>Đơn giản hoá có chủ ý so với một luồng đăng ký MFA đầy đủ:</b> {@link #enable} kích hoạt
 * MFA ngay, không có bước "xác nhận đã cấu hình đúng ứng dụng TOTP" trước khi bật — vì chưa có
 * màn hình đăng ký tự phục vụ nào gọi tới đây cả (chưa có endpoint HTTP nào, xem
 * {@code OPEN-08}). Khi xây endpoint đăng ký thật, thêm bước xác nhận trước khi gọi
 * {@link AppUser#activateMfa}, đừng bật thẳng như test đang làm.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class MfaService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final int CODE_LENGTH = 8;
    // Bỏ ký tự dễ nhầm khi đọc/gõ tay: 0/O, 1/I/L.
    private static final char[] ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final AppUserRepository appUserRepository;
    private final MfaBackupCodeRepository backupCodeRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(AppUserRepository appUserRepository,
            MfaBackupCodeRepository backupCodeRepository, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.clock = clock;
    }

    /** @return 10 mã dự phòng ở dạng gốc — chỉ hiện ra đúng lần này, CSDL chỉ giữ hash. */
    @Transactional
    public List<String> enable(UUID userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() ->
                new QrosException(ErrorCode.NOT_FOUND));

        byte[] secret = Totp.generateSecret();
        user.activateMfa(Base64.getEncoder().encodeToString(secret));
        appUserRepository.save(user);

        List<String> plainCodes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            plainCodes.add(randomBackupCode());
        }
        backupCodeRepository.saveAll(plainCodes.stream()
                .map(code -> MfaBackupCode.issue(userId, OpaqueTokenHashes.sha256Hex(code)))
                .toList());

        return List.copyOf(plainCodes);
    }

    public boolean verifyTotp(AppUser user, String code) {
        if (!user.isMfaEnabled() || user.getMfaSecretRef() == null) {
            return false;
        }
        byte[] secret = Base64.getDecoder().decode(user.getMfaSecretRef());
        return Totp.verify(secret, code, Instant.now(clock));
    }

    /** @return {@code true} nếu mã hợp lệ và chưa dùng — tiêu ngay trong cùng lệnh gọi. */
    @Transactional
    public boolean consumeBackupCode(UUID userId, String rawCode) {
        String hash = OpaqueTokenHashes.sha256Hex(rawCode);
        return backupCodeRepository.consume(userId, hash, Instant.now(clock)) == 1;
    }

    private String randomBackupCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                sb.append('-');
            }
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
