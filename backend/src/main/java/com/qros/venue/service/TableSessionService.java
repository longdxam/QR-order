package com.qros.venue.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.audit.api.AuditEntry;
import com.qros.audit.api.AuditRecorder;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.JwtIssuer;
import com.qros.shared.security.Totp;
import com.qros.venue.domain.RestaurantTable;
import com.qros.venue.domain.SessionDevice;
import com.qros.venue.domain.Store;
import com.qros.venue.domain.TableSessionEntity;
import com.qros.venue.repository.RestaurantTableRepository;
import com.qros.venue.repository.SessionDeviceRepository;
import com.qros.venue.repository.StoreRepository;
import com.qros.venue.repository.TableSessionRepository;

/**
 * Điều phối sáu bước xác minh của lớp 1–2 mục 5.3.2 (PRD) và mở/tham gia phiên bàn theo
 * {@code EC-02}. Đây là nơi duy nhất gọi tới {@link QrTokenVerifier}, {@link TableScanRateLimiter},
 * và phát token phiên bàn — controller chỉ dịch qua lại giữa DTO hợp đồng và các kiểu ở đây.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng lý do {@link QrTokenVerifier} — phụ thuộc trực tiếp
 * vào nó cùng các repository JPA khác không tồn tại ở profile {@code test}.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class TableSessionService {

    /** Lớp 4 mục 5.3.2: QR xoay vòng đổi mỗi 60 giây, khác bước 30 giây mặc định của MFA. */
    private static final int ROTATING_QR_STEP_SECONDS = 60;

    /** {@code EC-02} "nghi ngờ lạm dụng": trên 4 thiết bị trong 30 phút thì chặn thiết bị thứ năm. */
    private static final int MAX_DEVICES_PER_SESSION = 4;
    private static final Duration ABUSE_WINDOW = Duration.ofMinutes(30);

    private final QrTokenVerifier qrTokenVerifier;
    private final TableScanRateLimiter rateLimiter;
    private final StoreRepository storeRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final TableSessionRepository tableSessionRepository;
    private final SessionDeviceRepository sessionDeviceRepository;
    private final JwtIssuer guestJwtIssuer;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public TableSessionService(QrTokenVerifier qrTokenVerifier, TableScanRateLimiter rateLimiter,
            StoreRepository storeRepository, RestaurantTableRepository restaurantTableRepository,
            TableSessionRepository tableSessionRepository, SessionDeviceRepository sessionDeviceRepository,
            JwtIssuer guestJwtIssuer, AuditRecorder auditRecorder, Clock clock) {
        this.qrTokenVerifier = qrTokenVerifier;
        this.rateLimiter = rateLimiter;
        this.storeRepository = storeRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.tableSessionRepository = tableSessionRepository;
        this.sessionDeviceRepository = sessionDeviceRepository;
        this.guestJwtIssuer = guestJwtIssuer;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    // noRollbackFor bắt buộc: EC-02 "nghi ngờ lạm dụng" ghi audit RỒI ném QrosException để trả 409
    // cho client — mặc định Spring rollback trên mọi RuntimeException sẽ cuốn trôi luôn dòng audit
    // vừa ghi, y hệt bẫy đã gặp ở AuthenticationService.login (BL-M0-08) và
    // AuthenticationService.refresh/RefreshTokenService.rotate (BL-M0-09).
    @Transactional(noRollbackFor = QrosException.class)
    public SessionOutcome startFromQr(String qrToken, UUID deviceId, String nickname, String clientIp) {
        QrTokenVerifier.QrClaims claims = qrTokenVerifier.verify(qrToken);
        // Bước 3: bàn phải tồn tại và thuộc đúng chi nhánh ghi trong QR — cross-check thứ hai,
        // độc lập với việc QrTokenVerifier đã so sid với chủ sở hữu khoá ký.
        RestaurantTable table = restaurantTableRepository
                .findByIdAndStoreId(claims.tableId(), claims.storeId())
                .filter(RestaurantTable::isUsable)
                .orElseThrow(() -> new QrosException(ErrorCode.QR_INVALID_SIGNATURE));
        Store store = requireOpenStore(claims.storeId());

        // Bước 5: QR xoay vòng — bàn bật chế độ này thì claim otp phải khớp mã hiện hành.
        if (table.isRotatingQrEnabled()) {
            byte[] secret = Base64.getDecoder().decode(table.getTotpSecretRef());
            if (!Totp.verify(secret, claims.otp(), Instant.now(clock), ROTATING_QR_STEP_SECONDS)) {
                throw new QrosException(ErrorCode.QR_OTP_EXPIRED);
            }
        }

        return startOrJoin(store, table, deviceId, nickname, clientIp);
    }

    @Transactional(noRollbackFor = QrosException.class)
    public SessionOutcome startFromCode(String tableCode, UUID deviceId, String nickname, String clientIp) {
        List<RestaurantTable> matches = restaurantTableRepository.findByShortCode(tableCode);
        // short_code chỉ UNIQUE trong phạm vi một chi nhánh (V1__baseline.sql) — quét thấy đúng một
        // kết quả mới coi là hợp lệ, tránh đoán bừa nếu (hiếm) trùng mã giữa hai chi nhánh.
        RestaurantTable table = matches.size() == 1 ? matches.get(0) : null;
        if (table == null || !table.isUsable()) {
            throw new QrosException(ErrorCode.TABLE_CODE_INVALID);
        }
        Store store = requireOpenStore(table.getStoreId());

        return startOrJoin(store, table, deviceId, nickname, clientIp);
    }

    @Transactional(readOnly = true)
    public SessionOutcome currentSession(UUID sessionId, UUID deviceId) {
        Instant now = Instant.now(clock);
        TableSessionEntity session = tableSessionRepository.findById(sessionId)
                .filter(TableSessionEntity::isOpen)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));

        Store store = storeRepository.findById(session.getStoreId())
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));
        RestaurantTable table = restaurantTableRepository.findById(session.getTableId())
                .orElseThrow(() -> new QrosException(ErrorCode.TABLE_SESSION_EXPIRED));
        List<SessionDevice> participants = sessionDeviceRepository.findBySessionIdOrderByJoinedAt(sessionId);

        String accessToken = issueAccessToken(session, deviceId, now);
        return new SessionOutcome(session, store, table, true, participants, accessToken);
    }

    private Store requireOpenStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .filter(Store::isActive)
                .orElseThrow(() -> new QrosException(ErrorCode.QR_INVALID_SIGNATURE));
        // Bước 4: từ chối cấp phiên nếu chi nhánh đang ngoài giờ mở cửa.
        if (!store.isOpenAt(Instant.now(clock))) {
            throw new QrosException(ErrorCode.STORE_CLOSED);
        }
        return store;
    }

    private SessionOutcome startOrJoin(Store store, RestaurantTable table, UUID deviceId, String nickname,
            String clientIp) {
        // Bước 6 (thứ tự đúng theo PRD 5.3.2): giới hạn tần suất trước khi chạm CSDL phiên.
        rateLimiter.checkTableScan(table.getId(), clientIp);

        // Khoá tư vấn theo bàn trước khi đọc: hai request quét cùng bàn cùng lúc thì request thứ
        // hai CHỜ ở đây tới khi giao dịch của request thứ nhất commit xong, nên luôn đọc đúng phiên
        // vừa tạo thay vì cùng thấy rỗng rồi cùng ghi. An toàn hơn bắt lỗi vi phạm ràng buộc duy
        // nhất giữa chừng giao dịch: PostgreSQL đánh dấu cả giao dịch "aborted" ngay khi một câu
        // lệnh vi phạm ràng buộc, mọi câu lệnh sau đó trong cùng giao dịch sẽ lỗi theo — bắt
        // exception rồi thử đọc/ghi tiếp trong cùng giao dịch đó sẽ không hoạt động.
        tableSessionRepository.khoaTheoBan(table.getId());

        Instant now = Instant.now(clock);
        TableSessionEntity existing = tableSessionRepository
                .findByTableIdAndStatus(table.getId(), TableSessionEntity.STATUS_OPEN)
                .orElse(null);

        if (existing == null) {
            return createSession(store, table, deviceId, nickname, now);
        }
        return joinSession(store, table, existing, deviceId, nickname, now);
    }

    private SessionOutcome createSession(Store store, RestaurantTable table, UUID deviceId, String nickname,
            Instant now) {
        TableSessionEntity session = TableSessionEntity.open(store.getId(), table.getId(), now);
        tableSessionRepository.save(session);
        SessionDevice device = new SessionDevice(session.getId(), deviceId, nickname, now);
        sessionDeviceRepository.save(device);
        String accessToken = issueAccessToken(session, deviceId, now);
        return new SessionOutcome(session, store, table, false, List.of(device), accessToken);
    }

    private SessionOutcome joinSession(Store store, RestaurantTable table, TableSessionEntity session,
            UUID deviceId, String nickname, Instant now) {
        boolean daTrongPhien = sessionDeviceRepository.existsBySessionIdAndDeviceId(session.getId(), deviceId);
        if (!daTrongPhien) {
            if (!session.canJoin(now)) {
                // EC-02 "nhóm mới" hoặc phiên đã nguội: không tự chiếm phiên cũ, chuyển nhân viên.
                throw new QrosException(ErrorCode.TABLE_SESSION_CONFLICT);
            }
            long soThietBiGanDay = sessionDeviceRepository
                    .countBySessionIdAndJoinedAtAfter(session.getId(), now.minus(ABUSE_WINDOW));
            if (soThietBiGanDay >= MAX_DEVICES_PER_SESSION) {
                canhBaoLamDung(store, session, soThietBiGanDay);
                throw new QrosException(ErrorCode.TABLE_SESSION_CONFLICT);
            }
            sessionDeviceRepository.save(new SessionDevice(session.getId(), deviceId, nickname, now));
        }
        session.recordActivity(now);
        List<SessionDevice> participants = sessionDeviceRepository.findBySessionIdOrderByJoinedAt(session.getId());
        String accessToken = issueAccessToken(session, deviceId, now);
        return new SessionOutcome(session, store, table, true, participants, accessToken);
    }

    private void canhBaoLamDung(Store store, TableSessionEntity session, long soThietBi) {
        auditRecorder.record(new AuditEntry(
                "venue.session.device_abuse_suspected", "TableSession", session.getId(),
                null, null, store.getId(),
                null, Map.of("deviceCount", soThietBi), null));
    }

    private String issueAccessToken(TableSessionEntity session, UUID deviceId, Instant now) {
        Duration ttl = Duration.between(now, session.getExpiresAt());
        Map<String, Object> claims = Map.of(
                "scope", "table_session",
                "sid", session.getStoreId().toString(),
                "tid", session.getTableId().toString(),
                "did", deviceId.toString(),
                // Chưa có cơ chế thu hồi theo phiên (khác token_version của staff) — giữ claim cho
                // đúng cấu trúc chung NFR-SEC-02 mô tả, chưa có nơi nào đọc lại giá trị này.
                "tv", 0);
        return guestJwtIssuer.issue(session.getId().toString(), ttl, claims);
    }

    public record SessionOutcome(
            TableSessionEntity session,
            Store store,
            RestaurantTable table,
            boolean joined,
            List<SessionDevice> participants,
            String accessToken) {
    }
}
