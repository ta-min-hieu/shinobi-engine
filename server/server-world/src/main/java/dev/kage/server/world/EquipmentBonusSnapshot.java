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
public record EquipmentBonusSnapshot(int atk, int def, int res, int hp, Extras extras) {

    /** Không mặc gì, hoặc trang bị không cộng chỉ số nào. */
    public static final EquipmentBonusSnapshot NONE = new EquipmentBonusSnapshot(0, 0, 0, 0);

    /**
     * Những thứ trang bị cho mà KHÔNG phải bốn chỉ số cộng phẳng — gom lại để record không vượt trần tham số.
     *
     * @param moveSpeedPercent tốc độ cộng thêm của phi kiếm (ADR-070)
     * @param petStrikePermille linh thú "đánh thêm": xác suất (phần nghìn) đòn đánh thường gây thêm sát thương
     * (ADR-086)
     * @param petGuardPermille linh thú "hộ chủ": xác suất (phần nghìn) chắn nửa sát thương đòn đánh thường (ADR-086)
     */
    public record Extras(int moveSpeedPercent, int petStrikePermille, int petGuardPermille) {

        /** Không phi kiếm, không kỹ năng linh thú. */
        public static final Extras NONE = new Extras(0, 0, 0);

        /** Chỉ có tốc độ phi kiếm. */
        public static Extras speed(int moveSpeedPercent) {
            return new Extras(moveSpeedPercent, 0, 0);
        }
    }

    public EquipmentBonusSnapshot {
        java.util.Objects.requireNonNull(extras, "extras");
    }

    /** Không phi kiếm, không kỹ năng linh thú — bốn chỉ số chiến đấu. */
    public EquipmentBonusSnapshot(int atk, int def, int res, int hp) {
        this(atk, def, res, hp, Extras.NONE);
    }

    /**
     * Bản cùng {@link #extras} nhưng bốn chỉ số khác — để các lớp cộng dồn (tâm pháp, bang) không đánh rơi phụ phẩm.
     */
    public EquipmentBonusSnapshot withStats(int newAtk, int newDef, int newRes, int newHp) {
        return new EquipmentBonusSnapshot(newAtk, newDef, newRes, newHp, extras);
    }

    /** Tốc độ cộng thêm của phi kiếm (ADR-070). */
    public int moveSpeedPercent() {
        return extras.moveSpeedPercent();
    }

    /** Hệ số nhân tốc độ di chuyển của phi kiếm: {@code 1 + %/100}. */
    public float moveSpeedMultiplier() {
        return 1f + extras.moveSpeedPercent() / 100f;
    }
}
