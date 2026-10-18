// feature-gm — lệnh GM trong game (ADR-052). Quyền (gm_role) + sổ audit (gm_audit_log) + parser lệnh
// whitelist. KHÔNG gọi facade của feature khác — điều phối liên feature là việc của gateway
// (GmCommandRouter). KHÔNG import JDBC/Redis (Điều 23) — adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
