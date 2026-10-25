package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.List;

/**
 * Mục tiêu của MỘT quest, chép tay từ script của nó thành dữ liệu — mô hình tham chiếu
 * ({@link QuestFuzzModel}) chạy trên bảng này, độc lập với GraalJS. Lệch giữa hai bên (script đổi mà
 * bảng không đổi, hay ngược lại) là đỏ ngay ở bước đầu tiên đụng tới quest đó, và
 * {@link QuestFuzzContent} đòi bảng phủ ĐỦ mọi {@code quest_template} — thêm quest mới mà quên dòng
 * ở đây thì đỏ chứ không lặng lẽ bị bỏ qua.
 *
 * <p>Ba loại mục tiêu của dự án đều diễn được bằng năm trường: hạ {@code kills} con {@code monster}
 * (đếm vào {@code counterField} của {@code progress_json}), có thể phải hỏi {@code askNpc} TRƯỚC thì
 * mới đếm, rồi mở lời với {@code reportNpc} (rỗng = xong ngay khi đủ số), có thể kèm giao đồ
 * {@code deliveries} (tất cả hoặc không gì cả).
 *
 * <p>⚠️ Không chép SỐ THƯỞNG vào đây: phần thưởng là cân bằng, đổi thường xuyên. Bộ chạy ghi lại
 * phần thưởng quan sát được lần đầu và đòi mọi lần lĩnh sau của cùng quest ra đúng bằng nó.
 */
record QuestFuzzSpec(
        String code,
        String askNpc,
        String monster,
        int kills,
        CounterKind counterKind,
        String counterField,
        String reportNpc,
        List<ItemTake> deliveries) {

    /** Cách script ghi bộ đếm hạ quái vào {@code progress_json}. */
    enum CounterKind {
        /** Không có mục tiêu hạ quái. */
        NONE,
        /** Số nguyên, cộng mỗi lần hạ đúng loài khi quest còn đang làm — kể cả khi đã vượt mốc. */
        COUNT,
        /** Số nguyên, ngừng cộng khi chạm mốc ({@code first_cultivation_manual}). */
        COUNT_UNTIL_TARGET,
        /** Cờ boolean — {@code true} sau con đầu tiên. */
        FLAG,
        /** Không hạ quái: xong bằng sự kiện {@code escort_arrived} do server sinh (ADR-114). */
        ESCORT
    }

    record ItemTake(String itemCode, int quantity) {}

    QuestFuzzSpec {
        deliveries = List.copyOf(deliveries);
    }

    static QuestFuzzSpec quest(String code) {
        return new QuestFuzzSpec(code, "", "", 0, CounterKind.NONE, "", "", List.of());
    }

    QuestFuzzSpec killing(String monsterCode, int target, CounterKind kind, String field) {
        return new QuestFuzzSpec(code, askNpc, monsterCode, target, kind, field, reportNpc, deliveries);
    }

    /** ADR-114 — quest hộ tống: chỉ sự kiện đoàn tới nơi làm nó xong. */
    QuestFuzzSpec escorting() {
        return new QuestFuzzSpec(code, askNpc, monster, kills, CounterKind.ESCORT, counterField, reportNpc, deliveries);
    }

    boolean isEscort() {
        return counterKind == CounterKind.ESCORT;
    }

    QuestFuzzSpec askingFirst(String npcCode) {
        return new QuestFuzzSpec(code, npcCode, monster, kills, counterKind, counterField, reportNpc, deliveries);
    }

    QuestFuzzSpec reportingTo(String npcCode) {
        return new QuestFuzzSpec(code, askNpc, monster, kills, counterKind, counterField, npcCode, deliveries);
    }

    QuestFuzzSpec delivering(String itemCode, int quantity) {
        List<ItemTake> more = new ArrayList<>(deliveries);
        more.add(new ItemTake(itemCode, quantity));
        return new QuestFuzzSpec(code, askNpc, monster, kills, counterKind, counterField, reportNpc, more);
    }

    boolean completesOnKill() {
        return reportNpc.isEmpty();
    }

    boolean hasAskStep() {
        return !askNpc.isEmpty();
    }

    private static final String TOWN_ELDER = "town_elder";
    private static final String SENIOR_SISTER = "senior_sister";
    private static final String FELLED = "felled";

    /** Quest mẫu giao HAI món — không có tiên quyết, chạy song song được với chuỗi nội dung. */
    static final QuestFuzzSpec FIXTURE_DELIVERY = quest("fuzz_fixture_delivery")
            .reportingTo(SENIOR_SISTER)
            .delivering("minor_healing_pill", 2)
            .delivering("kunai", 1);

    /** Toàn bộ nội dung quest đã ship — đúng theo thứ tự chuỗi, mỗi dòng chép từ script cùng tên. */
    static final List<QuestFuzzSpec> CONTENT = List.of(
            quest("kill_training_dummies").killing("training_dummy", 3, CounterKind.COUNT, "killed"),
            quest("night_watch_bandit").killing("wandering_bandit", 1, CounterKind.COUNT, "repelled"),
            quest("clear_old_archive").killing("archive_warden", 2, CounterKind.COUNT, "cleared"),
            quest("awakening_trial").killing("trial_construct", 1, CounterKind.COUNT, "endured"),
            quest("spirit_vein_raiders").killing("vein_raider", 3, CounterKind.COUNT, FELLED),
            quest("first_cultivation_manual")
                    .killing("vein_raider", 2, CounterKind.COUNT_UNTIL_TARGET, FELLED)
                    .reportingTo(TOWN_ELDER),
            quest("break_the_raider_band")
                    .killing("raider_chieftain", 1, CounterKind.FLAG, FELLED)
                    .reportingTo(TOWN_ELDER),
            quest("report_to_outer_sect").reportingTo(SENIOR_SISTER),
            quest("recover_outer_sect_tallies").reportingTo(SENIOR_SISTER).delivering("outer_sect_tally", 2),
            quest("missing_disciple_trail")
                    .askingFirst(TOWN_ELDER)
                    .killing("wandering_bandit", 2, CounterKind.COUNT, "cleared")
                    .reportingTo(SENIOR_SISTER),
            quest("abandoned_cave")
                    .killing("blood_sand_scout", 1, CounterKind.COUNT, "slain")
                    .reportingTo(SENIOR_SISTER)
                    .delivering("blood_sand_talisman", 1),
            quest("confront_the_steward")
                    .killing("blood_sand_enforcer", 1, CounterKind.FLAG, FELLED)
                    .reportingTo(SENIOR_SISTER),
            quest("dead_vein_intruders").killing("blood_sand_acolyte", 3, CounterKind.COUNT, FELLED),
            quest("blood_sand_trail")
                    .killing("blood_sand_acolyte", 2, CounterKind.COUNT, FELLED)
                    .reportingTo(SENIOR_SISTER),
            quest("scatter_the_blood_sand_band")
                    .killing("blood_sand_ringleader", 1, CounterKind.FLAG, FELLED)
                    .reportingTo(TOWN_ELDER),
            quest("wasteland_edge_patrol")
                    .killing("blood_sand_ringleader", 1, CounterKind.FLAG, "cleared")
                    .reportingTo(TOWN_ELDER),
            quest("rank_board_dispute")
                    .killing("wasteland_beast", 2, CounterKind.COUNT, FELLED)
                    .reportingTo(SENIOR_SISTER),
            quest("foundation_pill_run")
                    .killing("wasteland_beast", 2, CounterKind.COUNT, FELLED)
                    .reportingTo(TOWN_ELDER)
                    .delivering("minor_healing_pill", 3),
            quest("outer_sect_proving")
                    .killing("wasteland_beast", 3, CounterKind.COUNT, FELLED)
                    .reportingTo(TOWN_ELDER),
            quest("foundation_breakthrough")
                    .killing("foundation_warden", 1, CounterKind.FLAG, "endured")
                    .reportingTo(TOWN_ELDER),
            // ADR-055 — nhiệm vụ ngày, không tiên quyết. Trong một lượt fuzz (vài giây) không qua 0h,
            // nên mô hình "CLAIMED là cuối" vẫn đúng.
            quest("daily_dummy_drill").killing("training_dummy", 5, CounterKind.COUNT, FELLED),
            quest("daily_mountain_patrol").killing("wandering_bandit", 3, CounterKind.COUNT, FELLED),
            quest("daily_water_duty").askingFirst(SENIOR_SISTER).reportingTo(TOWN_ELDER),
            // ADR-114 — hộ tống: không sự kiện quái/NPC nào làm nó xong, chỉ `escort_arrived`.
            quest("daily_caravan_escort").escorting());
}
