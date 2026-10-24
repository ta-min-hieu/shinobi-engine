package dev.kage.server.database.inventory;

/**
 * Một dòng vật phẩm cần cấp/tiêu theo LOẠI: mẫu + số lượng + cấp cường hoá (ADR-083 — thư chở đồ cường hoá). Gom để các
 * helper SQL túi đồ không vượt trần {@code ParameterNumber}.
 *
 * @param itemTemplateId mẫu vật phẩm
 * @param quantity số lượng
 * @param enhanceLevel cấp cường hoá (0 = chưa cường hoá)
 */
public record ItemLine(long itemTemplateId, int quantity, int enhanceLevel) {}
