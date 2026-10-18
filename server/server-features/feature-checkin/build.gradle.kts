// feature-checkin — điểm danh hằng ngày (ADR-060). Chu kỳ 7 ngày + bảng quà là tham số DB. KHÔNG gọi
// facade của feature khác — gửi quà qua port CheckInRewardSender, gateway hiện thực bằng thư hệ thống.
// KHÔNG import JDBC/Redis (Điều 23) — adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
