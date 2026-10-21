package dev.kage.server.world;

/**
 * Bốn chỉ số cộng thêm từ trang bị, gói lại để {@code server-world} nhận được chúng mà KHÔNG import
 * {@code feature-inventory} (Điều 23 — server-world không biết feature nào tồn tại;
 * {@code GatewayChannelHandler} là nơi dịch từ {@code dev.kage.inventory.domain.EquipmentBonus}
 * sang record này). Đây cũng là lý do không truyền 4 số rời: 4 số + {@code characterId} là 5 tham
 * số, vượt ngưỡng Checkstyle {@code ParameterNumber} (max=4).
 *
 * <p>Mọi giá trị là TỔNG cộng dồn hiện tại từ MỌI item đang mặc, ĐÃ nhân hệ số cường hoá của từng
 * món (ADR-036 D2/D3) — không phải delta. Nơi nhận luôn tính lại chỉ số từ NỀN + tổng này, không
 * cộng dồn vào giá trị đang có.
 */
public record EquipmentBonusSnapshot(int atk, int def, int res, int hp, int moveSpeedPercent) {

    /** Không mặc gì, hoặc trang bị không cộng chỉ số nào. */
    public static final EquipmentBonusSnapshot NONE = new EquipmentBonusSnapshot(0, 0, 0, 0, 0);

    /** Không có phi kiếm (ADR-070) — bốn chỉ số chiến đấu. */
    public EquipmentBonusSnapshot(int atk, int def, int res, int hp) {
        this(atk, def, res, hp, 0);
    }

    /** Hệ số nhân tốc độ di chuyển của phi kiếm: {@code 1 + %/100}. */
    public float moveSpeedMultiplier() {
        return 1f + moveSpeedPercent / 100f;
    }
}
