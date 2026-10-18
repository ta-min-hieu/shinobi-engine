// feature-achievement — thành tựu + danh hiệu (ADR-062). Đếm mốc từ sự kiện gateway báo vào; KHÔNG gọi
// facade của feature khác. KHÔNG import JDBC/Redis (Điều 23) — adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
