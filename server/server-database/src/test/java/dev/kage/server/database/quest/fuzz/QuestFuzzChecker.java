package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.kage.character.domain.ExperienceCurve;
import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestStatus;

/**
 * So trạng thái ĐÃ LƯU (đọc thẳng bảng) với mô hình, cộng các bất biến không cần mô hình:
 *
 * <ul>
 *   <li>status + bộ đếm + cờ "đã hỏi" của từng quest khớp mô hình (tiến độ không mất, không vượt mốc);
 *   <li>túi đồ theo từng mã khớp mô hình (thưởng vào đúng một lần, đồ giao ra đúng một lần);
 *   <li>EXP khớp mô hình, và cột {@code level} khớp cấp suy từ {@code exp};
 *   <li>audit: mỗi quest tối đa MỘT dòng {@code QUEST_CLAIM}, đúng tập quest đã lĩnh qua use case;
 *       số dòng {@code QUEST_ITEM_TAKEN} khớp số lần lấy đồ;
 *   <li>không quest nào tồn tại khi tiên quyết của nó chưa CLAIMED;
 *   <li>túi không vượt sức chứa;
 *   <li>nhật ký nhiệm vụ (dữ liệu opcode 610/611) khớp bảng tiến độ.
 * </ul>
 */
final class QuestFuzzChecker {

    /** PLACEHOLDER trùng {@code DEFAULT_CAPACITY} của các adapter túi đồ. */
    static final int BAG_CAPACITY = 20;

    private static final Pattern QUEST_CODE_IN_DETAIL = Pattern.compile("\"questCode\":\"([^\"]+)\"");

    private final QuestFuzzDb db;
    private final QuestFuzzContent content;
    private final long characterId;

    QuestFuzzChecker(QuestFuzzDb db, QuestFuzzContent content, long characterId) {
        this.db = db;
        this.content = content;
        this.characterId = characterId;
    }

    List<String> check(QuestFuzzModel model, List<QuestLogEntry> questLog) {
        List<String> violations = new ArrayList<>();
        checkProgress(model, violations);
        checkBag(model, violations);
        checkExperience(model, violations);
        checkAudit(model, violations);
        checkPrerequisites(violations);
        checkQuestLog(questLog, violations);
        return violations;
    }

    int bagRows() {
        return (int) db.single("SELECT COUNT(*) FROM inventory_item WHERE character_id = ?", characterId);
    }

    Map<String, QuestStatus> persistedStatuses() {
        Map<String, QuestStatus> statuses = new TreeMap<>();
        for (QuestFuzzDb.Row row : progressRows()) {
            statuses.put(row.first(), QuestStatus.valueOf(row.second()));
        }
        return statuses;
    }

    private List<QuestFuzzDb.Row> progressRows() {
        return db.rows("""
                SELECT t.code, p.status, p.progress_json
                  FROM character_quest_progress p
                  JOIN quest_template t ON t.id = p.quest_template_id
                 WHERE p.character_id = ?
                """, characterId);
    }

    private void checkProgress(QuestFuzzModel model, List<String> violations) {
        Map<String, QuestFuzzModel.QuestState> expected = model.quests();
        Map<String, String> persistedJson = new HashMap<>();
        Map<String, QuestStatus> persisted = new TreeMap<>();
        for (QuestFuzzDb.Row row : progressRows()) {
            persisted.put(row.first(), QuestStatus.valueOf(row.second()));
            persistedJson.put(row.first(), row.third());
        }
        Map<String, QuestStatus> expectedStatuses = new TreeMap<>();
        expected.forEach((code, state) -> expectedStatuses.put(code, state.status()));
        if (!persisted.equals(expectedStatuses)) {
            violations.add("status quest đã lưu " + persisted + " ≠ mô hình " + expectedStatuses);
            return;
        }
        expected.forEach((code, state) -> {
            String json = persistedJson.get(code);
            if (!"{}".equals(json)) {
                checkCounter(code, state, json, violations);
            }
        });
    }

    private void checkCounter(String code, QuestFuzzModel.QuestState state, String json, List<String> violations) {
        QuestFuzzSpec spec = content.spec(code);
        if (spec.hasAskStep()
                && !String.valueOf(state.asked()).equals(field(json, "asked").orElse(""))) {
            violations.add("%s: cờ asked đã lưu %s ≠ mô hình %s".formatted(code, json, state.asked()));
        }
        String expectedCounter = expectedCounter(spec, state);
        if (!expectedCounter.isEmpty()
                && !expectedCounter.equals(field(json, spec.counterField()).orElse(""))) {
            violations.add("%s: bộ đếm '%s' đã lưu %s ≠ mô hình %s"
                    .formatted(code, spec.counterField(), json, expectedCounter));
        }
        if (overshoots(spec, json)) {
            violations.add("%s: tiến độ vượt mốc %d: %s".formatted(code, spec.kills(), json));
        }
    }

    private static String expectedCounter(QuestFuzzSpec spec, QuestFuzzModel.QuestState state) {
        return switch (spec.counterKind()) {
            case NONE -> "";
            case FLAG -> String.valueOf(state.kills() >= 1);
            case COUNT, COUNT_UNTIL_TARGET -> String.valueOf(state.kills());
        };
    }

    /**
     * Bộ đếm vượt mốc — chỉ là lỗi với quest xong NGAY khi đủ số (sau đó không còn sự kiện nào tới)
     * và với bộ đếm tự dừng ở mốc. Quest còn chờ báo cáo thì đếm quá mốc là đúng thiết kế script.
     */
    private static boolean overshoots(QuestFuzzSpec spec, String json) {
        boolean capped = spec.completesOnKill() || spec.counterKind() == QuestFuzzSpec.CounterKind.COUNT_UNTIL_TARGET;
        if (!capped || spec.counterKind() == QuestFuzzSpec.CounterKind.FLAG) {
            return false;
        }
        return field(json, spec.counterField()).map(Integer::parseInt).orElse(0) > spec.kills();
    }

    private static Optional<String> field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":(true|false|-?\\d+)")
                .matcher(json);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private void checkBag(QuestFuzzModel model, List<String> violations) {
        Map<String, Long> persisted = new TreeMap<>();
        long junk = 0;
        for (QuestFuzzDb.Row row : db.rows("""
                SELECT it.code, SUM(ii.quantity), COUNT(*)
                  FROM inventory_item ii
                  JOIN item_template it ON it.id = ii.item_template_id
                 WHERE ii.character_id = ?
                 GROUP BY it.code
                """, characterId)) {
            if (QuestFuzzContent.JUNK_ITEM.equals(row.first())) {
                junk = Long.parseLong(row.third());
            } else {
                persisted.put(row.first(), Long.parseLong(row.second()));
            }
        }
        if (!persisted.equals(new TreeMap<>(model.bag()))) {
            violations.add("túi đồ đã lưu " + persisted + " ≠ mô hình " + new TreeMap<>(model.bag()));
        }
        int rows = bagRows();
        if (rows > BAG_CAPACITY) {
            violations.add(
                    "túi có %d dòng, vượt sức chứa %d (trong đó %d dòng nhồi)".formatted(rows, BAG_CAPACITY, junk));
        }
    }

    private void checkExperience(QuestFuzzModel model, List<String> violations) {
        List<QuestFuzzDb.Row> rows = db.rows("SELECT exp, level FROM character WHERE id = ?", characterId);
        long exp = Long.parseLong(rows.get(0).first());
        int level = Integer.parseInt(rows.get(0).second());
        if (exp != model.exp()) {
            violations.add("exp đã lưu %d ≠ mô hình %d".formatted(exp, model.exp()));
        }
        int derived = ExperienceCurve.progressOf(exp).level();
        if (level != derived) {
            violations.add("cột level %d ≠ cấp suy từ exp=%d là %d".formatted(level, exp, derived));
        }
    }

    private void checkAudit(QuestFuzzModel model, List<String> violations) {
        Map<String, Integer> claimAudits = new TreeMap<>();
        int itemTaken = 0;
        for (QuestFuzzDb.Row row : db.rows(
                "SELECT action, detail_json FROM audit_log WHERE character_id = ? AND action LIKE 'QUEST%'",
                characterId)) {
            if ("QUEST_CLAIM".equals(row.first())) {
                Matcher matcher = QUEST_CODE_IN_DETAIL.matcher(row.second());
                claimAudits.merge(matcher.find() ? matcher.group(1) : row.second(), 1, Integer::sum);
            } else if ("QUEST_ITEM_TAKEN".equals(row.first())) {
                itemTaken++;
            }
        }
        claimAudits.forEach((code, count) -> {
            if (count > 1) {
                violations.add("%s được lĩnh thưởng %d lần (audit QUEST_CLAIM)".formatted(code, count));
            }
        });
        if (!claimAudits.keySet().equals(new TreeSet<>(model.claimedThroughUseCase()))) {
            violations.add("audit QUEST_CLAIM " + claimAudits.keySet() + " ≠ quest đã lĩnh "
                    + new TreeSet<>(model.claimedThroughUseCase()));
        }
        if (itemTaken != model.itemTakeAudits()) {
            violations.add("audit QUEST_ITEM_TAKEN %d dòng ≠ mô hình %d lần lấy đồ"
                    .formatted(itemTaken, model.itemTakeAudits()));
        }
    }

    private void checkPrerequisites(List<String> violations) {
        for (QuestFuzzDb.Row row : db.rows("""
                SELECT t.code, COALESCE(pp.status, 'ABSENT')
                  FROM character_quest_progress p
                  JOIN quest_template t ON t.id = p.quest_template_id
                  LEFT JOIN character_quest_progress pp
                         ON pp.character_id = p.character_id AND pp.quest_template_id = t.prerequisite_quest_id
                 WHERE p.character_id = ? AND t.prerequisite_quest_id IS NOT NULL
                   AND COALESCE(pp.status, 'ABSENT') <> 'CLAIMED'
                """, characterId)) {
            violations.add("%s tồn tại trong khi tiên quyết đang %s".formatted(row.first(), row.second()));
        }
    }

    private void checkQuestLog(List<QuestLogEntry> questLog, List<String> violations) {
        Map<String, QuestStatus> fromLog = new TreeMap<>();
        for (QuestLogEntry entry : questLog) {
            if (fromLog.put(entry.questCode(), entry.status()) != null) {
                violations.add("nhật ký nhiệm vụ lặp quest " + entry.questCode());
            }
        }
        Map<String, QuestStatus> persisted = persistedStatuses();
        if (!fromLog.equals(persisted)) {
            violations.add("nhật ký nhiệm vụ " + fromLog + " ≠ bảng tiến độ " + persisted);
        }
    }
}
