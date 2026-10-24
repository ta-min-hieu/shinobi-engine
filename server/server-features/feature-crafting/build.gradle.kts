// feature-crafting — luyện đan (ADR-079). Công thức là tham số DB. KHÔNG gọi facade của feature khác — túi đồ,
// linh thạch và audit đi qua CraftingWorkshopPort, server-database hiện thực trong cùng một transaction.
// KHÔNG import JDBC/Redis (Điều 23) — adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
