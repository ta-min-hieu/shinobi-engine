// feature-chat — trò chuyện Thế giới/Riêng tư/Khu vực (Phase 6 lát cắt 6, GAMEPLAY.md §10). Bang
// hội/Party DỜI Phase 7 (chưa có dữ liệu thành viên guild/party — quyết định 2026-07-23, xem
// ROADMAP.md). KHÔNG lưu lịch sử tin nhắn (không bảng riêng) — mỗi tin ghi 1 dòng audit_log để
// còn dấu vết khi có báo cáo (GAMEPLAY.md §10 "audit log cho chat báo cáo"), không xây UI report.
// KHÔNG import JDBC/Redis (Điều 23) — domain/ chỉ định nghĩa Port, adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
