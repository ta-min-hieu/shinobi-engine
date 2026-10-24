package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bảng TĨNH hai tâm pháp bị động của mỗi linh căn (ADR-077, GDD §5 "2 tâm pháp bị động") — cùng kiểu
 * {@link SkillRegistry}: chưa DB-driven, thêm một dòng là thêm một tâm pháp.
 *
 * <p>Khác {@code CombatConstants.FACTION_PASSIVES} (luôn bật, gắn với linh căn): tâm pháp ở đây phải HỌC,
 * nâng 0→5 bằng chính quỹ điểm công pháp của ADR-071, và cộng PHẲNG theo bậc vào cùng ống chỉ số với trang bị
 * ({@link EquipmentBonusSnapshot}) — nên máu tối đa, bảng nhân vật, Lực Chiến và trận đánh tự thấy nó.
 * Mọi con số là PLACEHOLDER cân bằng.
 */
public final class MentalArts {

    /** Bậc tối đa — bằng bậc chiêu tối đa của ADR-071. */
    public static final int MAX_RANK = 5;

    private static final int FIRST_LEVEL = 15;
    private static final int SECOND_LEVEL = 30;

    private static final List<MentalArt> ALL = List.of(
            new MentalArt("hoa_liet_hoa_tam_kinh", Element.HOA, FIRST_LEVEL, new EquipmentBonusSnapshot(2, 0, 0, 0)),
            new MentalArt("hoa_hoa_linh_ho_the", Element.HOA, SECOND_LEVEL, new EquipmentBonusSnapshot(0, 0, 0, 10)),
            new MentalArt("thuy_thuy_nguyen_quyet", Element.THUY, FIRST_LEVEL, new EquipmentBonusSnapshot(0, 0, 0, 12)),
            new MentalArt("thuy_bang_tam_quyet", Element.THUY, SECOND_LEVEL, new EquipmentBonusSnapshot(0, 0, 2, 0)),
            new MentalArt("phong_phong_hanh_quyet", Element.PHONG, FIRST_LEVEL, new EquipmentBonusSnapshot(2, 0, 0, 0)),
            new MentalArt("phong_luu_phong_than", Element.PHONG, SECOND_LEVEL, new EquipmentBonusSnapshot(0, 2, 0, 0)),
            new MentalArt("tho_hau_tho_kinh", Element.THO, FIRST_LEVEL, new EquipmentBonusSnapshot(0, 2, 0, 0)),
            new MentalArt("tho_ban_thach_than", Element.THO, SECOND_LEVEL, new EquipmentBonusSnapshot(0, 0, 0, 15)),
            new MentalArt("loi_loi_dinh_quyet", Element.LOI, FIRST_LEVEL, new EquipmentBonusSnapshot(3, 0, 0, 0)),
            new MentalArt("loi_loi_the_quyet", Element.LOI, SECOND_LEVEL, new EquipmentBonusSnapshot(0, 0, 2, 0)));

    private MentalArts() {}

    /** Tâm pháp của {@code element}, theo thứ tự mở. */
    public static List<MentalArt> of(Element element) {
        List<MentalArt> arts = new ArrayList<>();
        for (MentalArt art : ALL) {
            if (art.faction() == element) {
                arts.add(art);
            }
        }
        return arts;
    }

    /**
     * {@code equipment} cộng thêm tâm pháp của linh căn HIỆN TẠI theo bậc trong {@code ranks} (vắng = 0). Tâm
     * pháp của linh căn khác bị bỏ qua — cùng luật "chỉ tính trên linh căn hiện tại" của ADR-071 D3.
     */
    public static EquipmentBonusSnapshot applyTo(
            EquipmentBonusSnapshot equipment, Element element, Map<String, Integer> ranks) {
        int atk = equipment.atk();
        int def = equipment.def();
        int res = equipment.res();
        int hp = equipment.hp();
        for (MentalArt art : of(element)) {
            int rank = Math.clamp(ranks.getOrDefault(art.code(), 0), 0, MAX_RANK);
            atk += art.perRank().atk() * rank;
            def += art.perRank().def() * rank;
            res += art.perRank().res() * rank;
            hp += art.perRank().hp() * rank;
        }
        return equipment.withStats(atk, def, res, hp);
    }
}
