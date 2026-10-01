package dev.kage.server.world;

/**
 * Một trận tay đôi bắt buộc của chuỗi quest, đặt dưới <b>LUẬT BIÊN THẬT</b> — chủ dự án chốt
 * 2026-10-01 (GAME-DESIGN-DOCUMENT.md §6, mục "Luật biên thật").
 *
 * <p><b>Vì sao có luật này.</b> Mọi lớp {@code *BalanceTest} trước đây chỉ khoá SỐ HỌC: "quái cần
 * bao nhiêu nhát, người chơi cần bao nhiêu nhát". Biên giấy đó xanh trong khi trận thật THUA. Số đo
 * 2026-09-17 ({@code gameProbe}, Cấp 30, bộ Trúc Cơ, V49): {@code wasteland_beast} biên giấy +3
 * (ta 5 nhát, nó 8 nhát) mà ba trận 1v1 từ máu đầy ra <b>1 thắng, 2 chết</b>. Hai số hạng giấy bỏ
 * sót:
 *
 * <ul>
 *   <li><b>Nhát phủ đầu</b> — quái ra 1–2 nhát trong lúc người chơi còn đang đi tới / đứng vào thế
 *       đánh, kể cả với {@code aggro 3} (V46, V47 đo hai lần độc lập). Luật lấy CẬN TRÊN: 2.
 *   <li><b>Một lần né</b> — người chơi trúng ~91% ({@code hitPercent}), mỗi lần hụt là trả trọn một
 *       nhát của quái. Luật dự trữ đúng 1.
 * </ul>
 *
 * <p>Nên: {@code biên thật = nhát quái cần − nhát người chơi cần − 2 − 1}, và trận tay đôi bắt buộc
 * phải có {@code biên thật ≥ 2} (tức biên giấy ≥ 5).
 *
 * <p><b>Máu đầu vào.</b> {@link #playerHp} là máu lúc TỚI NƠI do THUẾ ĐƯỜNG ĐI của các loài KHÁC
 * (nếu lớp cân bằng đó có mô hình đường đi), và KHÔNG trừ nhát phủ đầu của chính đối thủ — số hạng
 * {@link #OPENING_HITS} đã gánh phần đó. Trừ cả hai là đếm trùng.
 *
 * <p>Vẫn KHÔNG mô phỏng AI hay ngẫu nhiên — chỉ đổi câu hỏi từ "ai hết máu trước nếu cả hai đánh
 * sạch" thành "ai hết máu trước sau khi quái được đánh trước 2 nhát và người chơi hụt 1 nhát".
 *
 * @param playerAtk ATK của người chơi (derive + trang bị mốc của lớp cân bằng đó)
 * @param playerDef DEF của người chơi
 * @param playerHp máu người chơi lúc CHẠM MẶT (máu tối đa trừ thuế đường đi, nếu có)
 * @param monsterHp {@code monster_template.max_hp}
 * @param monsterAtk {@code monster_template.atk}
 * @param monsterDef {@code monster_template.def}
 */
record DuelMargin(int playerAtk, int playerDef, int playerHp, int monsterHp, int monsterAtk, int monsterDef) {

    /** Cận trên số nhát quái ra trước khi người chơi vung nhát đầu (V46: 0/1/2 nhát, 2 là trần). */
    static final int OPENING_HITS = 2;

    /** Số lần người chơi hụt mà trận vẫn phải thắng — mỗi lần hụt trả trọn một nhát của quái. */
    static final int DODGE_RESERVE = 1;

    /** Biên thật tối thiểu của một trận tay đôi bắt buộc (chủ dự án chốt 2026-10-01). */
    static final int REQUIRED_REAL_MARGIN = 2;

    /** Biên giấy tối thiểu tương đương — {@code 2 + 2 + 1}. */
    static final int REQUIRED_PAPER_MARGIN = REQUIRED_REAL_MARGIN + OPENING_HITS + DODGE_RESERVE;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /** Số nhát người chơi cần để hạ quái, với sát thương trung bình (không bạo kích). */
    int playerHitsToKill() {
        return hitsToKill(monsterHp, hit(playerAtk, monsterDef));
    }

    /** Số nhát quái cần để hạ người chơi từ {@link #playerHp}. */
    int monsterHitsToKill() {
        return hitsToKill(playerHp, hit(monsterAtk, playerDef));
    }

    /** Biên GIẤY — thứ mọi lớp cân bằng trước 2026-10-01 khoá. Hai bên chung nhịp 1 đòn/giây. */
    int paperMargin() {
        return monsterHitsToKill() - playerHitsToKill();
    }

    /** Biên THẬT — biên giấy trừ nhát phủ đầu và một lần né. */
    int realMargin() {
        return paperMargin() - OPENING_HITS - DODGE_RESERVE;
    }

    /** Trận có thoả luật biên thật không. */
    boolean meetsRealMarginRule() {
        return realMargin() >= REQUIRED_REAL_MARGIN;
    }
}
