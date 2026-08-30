// feature-monster — dữ liệu TĨNH của quái: `monster_template` (loài nào có chỉ số gì) và
// `monster_spawn` (loài nào đứng ở đâu). ADR-037.
//
// Không có application/ vì không có nghiệp vụ nào để điều phối: đây là tra cứu config đọc-thuần,
// nạp một lần lúc khởi động (DATABASE.md §182 xếp `monster_template` vào nhóm "load lúc khởi động,
// không đổi khi chạy"), giống hệt cách `item_template` được đối xử.
//
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
