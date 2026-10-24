package dev.kage.guild.domain;

/**
 * Một Linh Mạch tranh đoạt được (ADR-090) — THAM SỐ đọc từ bảng {@code spirit_vein} (Điều 27).
 *
 * @param code mã ổn định
 * @param nameKey khoá i18n tên hiển thị
 * @param zone dải toạ độ X trên world chính tính là "đứng trong Linh Mạch"
 * @param schedule khung giờ tranh đoạt hằng tuần
 * @param reward thưởng mỗi thành viên bang thắng
 */
public record SpiritVein(String code, String nameKey, Zone zone, Schedule schedule, Reward reward) {

    /** Dải X kín hai đầu. */
    public record Zone(float minX, float maxX) {

        /** {@code x} nằm trong vùng. */
        public boolean contains(float x) {
            return x >= minX && x <= maxX;
        }
    }

    /**
     * Khung giờ hằng tuần theo UTC.
     *
     * @param weekday thứ trong tuần theo ISO (1 = thứ Hai … 7 = Chủ nhật)
     * @param startMinute phút bắt đầu trong ngày UTC (0–1439)
     * @param durationMinutes độ dài khung
     */
    public record Schedule(int weekday, int startMinute, int durationMinutes) {}

    /** Thưởng mỗi thành viên bang thắng; {@code itemTemplateId <= 0} = không có vật phẩm. */
    public record Reward(long gold, long itemTemplateId, int quantity) {}
}
