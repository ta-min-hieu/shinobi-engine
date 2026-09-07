// feature-world — HÌNH HỌC BẢN ĐỒ: vùng nào ở đâu và có luật gì. Sinh ra bởi ADR-046 D3
// (bảng `safe_zone`), sau khi chủ dự án chọn "module mới" thay vì nhét vào feature-monster.
//
// Không có application/ vì không có nghiệp vụ nào để điều phối: tra cứu config đọc-thuần, nạp một
// lần lúc khởi động — cùng khuôn feature-monster (DATABASE.md §182).
//
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
