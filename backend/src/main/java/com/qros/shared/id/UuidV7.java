package com.qros.shared.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sinh UUID phiên bản 7 theo RFC 9562.
 *
 * <p>Khoá chính hướng ra ngoài dùng UUIDv7 vì ba lý do đã ghi trong PRD mục kiến trúc dữ liệu:
 * có thứ tự theo thời gian nên thân thiện với chỉ mục B-tree, không đoán được, và không lộ
 * số lượng bản ghi như khoá tuần tự. {@code TM-ACC-01} dựa vào tính không đoán được này để
 * chống dò IDOR — 62 bit ngẫu nhiên ở nửa dưới là phần giữ lời hứa đó, không phải phần thời gian.
 *
 * <p>Bố cục 128 bit: 48 bit mili giây Unix · 4 bit version · 12 bit bộ đếm đơn điệu ·
 * 2 bit variant · 62 bit ngẫu nhiên. Bộ đếm 12 bit bảo đảm hai ID sinh trong cùng một mili giây
 * vẫn tăng dần; khi tràn, phần thời gian được mượn thêm một mili giây thay vì quay vòng —
 * RFC 9562 mục 6.2 cho phép, và thứ tự quan trọng hơn độ chính xác một mili giây.
 *
 * <p>JDK 25 chưa có API sinh UUIDv7, nên phần này tự hiện thực.
 */
public final class UuidV7 {

    private static final int COUNTER_BITS = 12;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;
    private static final long VERSION_7 = 0x7L << COUNTER_BITS;
    private static final long VARIANT_RFC = 0x8000000000000000L;
    private static final long VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL;
    private static final long MAX_UNIX_MILLIS = (1L << 48) - 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Gói (mili giây · bộ đếm) vào một long để cập nhật nguyên tử, không cần khoá. */
    private static final AtomicLong STATE = new AtomicLong(0L);

    private UuidV7() {
    }

    /**
     * Sinh ID cho dữ liệu mới. Kết quả tăng dần nghiêm ngặt trong suốt vòng đời tiến trình,
     * kể cả khi nhiều luồng cùng gọi trong một mili giây.
     *
     * <p>Đổi lại, nếu đồng hồ hệ thống nhảy lùi thì mốc thời gian nhúng trong ID đi trước đồng hồ
     * vài mili giây. Đó là đánh đổi có chủ ý: thứ tự khoá chính đáng giá hơn độ chính xác một
     * mili giây, và khoá mất thứ tự thì phân trang theo con trỏ sai kết quả.
     */
    public static UUID generate() {
        long state = nextState(System.currentTimeMillis());
        return build(state >>> COUNTER_BITS, (int) (state & COUNTER_MASK));
    }

    public static UUID generate(Instant instant) {
        return generate(instant.toEpochMilli());
    }

    /**
     * Sinh ID mang đúng mốc thời gian được chỉ định — dùng cho fixture, dữ liệu nạp lại và
     * chuyển đổi dữ liệu cũ.
     *
     * <p>Đường này không đụng tới bộ đếm đơn điệu của {@link #generate()}: một mốc thời gian nạp
     * lại từ quá khứ không được phép kéo lùi ID của dữ liệu đang chạy. Bù lại, hai ID cùng một
     * mili giây chỉ khác nhau ở phần ngẫu nhiên chứ không bảo đảm thứ tự.
     */
    public static UUID generate(long unixMillis) {
        requireInRange(unixMillis);
        return build(unixMillis, RANDOM.nextInt(1 << COUNTER_BITS));
    }

    public static boolean isUuidV7(UUID uuid) {
        return uuid != null && uuid.version() == 7 && uuid.variant() == 2;
    }

    /** Đọc lại mốc thời gian đã nhúng — dùng cho phân trang theo con trỏ và chẩn đoán. */
    public static Instant timestampOf(UUID uuid) {
        if (!isUuidV7(uuid)) {
            throw new IllegalArgumentException("Không phải UUIDv7: " + uuid);
        }
        return Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
    }

    private static UUID build(long unixMillis, int counter) {
        long mostSignificantBits = (unixMillis << 16) | VERSION_7 | (counter & COUNTER_MASK);
        long leastSignificantBits = (RANDOM.nextLong() & VARIANT_MASK) | VARIANT_RFC;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static void requireInRange(long unixMillis) {
        if (unixMillis < 0L || unixMillis > MAX_UNIX_MILLIS) {
            throw new IllegalArgumentException("Mốc thời gian nằm ngoài 48 bit: " + unixMillis);
        }
    }

    /**
     * Trả về trạng thái kế tiếp, luôn lớn hơn trạng thái trước.
     *
     * <p>Bộ đếm nằm ở 12 bit thấp nên phép {@code +1} khi tràn tự cộng sang phần mili giây —
     * đó chính là hành vi "mượn thời gian" mong muốn.
     */
    private static long nextState(long unixMillis) {
        requireInRange(unixMillis);
        long candidate = unixMillis << COUNTER_BITS;
        while (true) {
            long previous = STATE.get();
            long next = previous < candidate ? candidate : previous + 1L;
            if (STATE.compareAndSet(previous, next)) {
                return next;
            }
        }
    }
}
