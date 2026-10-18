// feature-market — phường thị (ADR-063). Treo bán món chưa cường hoá lấy linh thạch, phí 5% (gold sink).
// KHÔNG gọi facade của feature khác; đụng túi đồ/vàng qua port riêng trong CÙNG transaction (cách của
// feature-trade). KHÔNG import JDBC/Redis (Điều 23) — adapter thật ở server-database.
plugins {
    id("kage.quality-conventions")
}
