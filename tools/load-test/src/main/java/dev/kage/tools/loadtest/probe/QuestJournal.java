package dev.kage.tools.loadtest.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.kage.protocol.QuestLogEntry;
import dev.kage.protocol.QuestStatus;

/**
 * Nhật ký nhiệm vụ ({@code LIST_QUESTS_RESPONSE}, ADR-035) thành BẢNG, và chênh lệch giữa hai lần
 * đọc thành DÒNG SỐ.
 *
 * <p>Vì sao cần chênh lệch chứ không chỉ ảnh chụp: câu hỏi thật của một buổi kiểm tra là "bấm nhận
 * nhiệm vụ xong thì trạng thái CÓ ĐỔI không" — đúng loại câu hỏi mà nhìn giao diện đã trả lời sai
 * nhiều lần (ROADMAP mục 55/57/58). Hai ảnh chụp trước/sau trả lời bằng số.
 *
 * <p>Hàm THUẦN, không I/O — test được không cần server ({@code QuestJournalTest}).
 */
final class QuestJournal {

    /** Giá trị cột trạng thái khi quest chưa từng xuất hiện trong nhật ký ở lần đọc đó. */
    static final String ABSENT = "(chưa có)";

    private static final String NEWLINE = System.lineSeparator();

    /** Một quest có trạng thái khác nhau giữa hai lần đọc nhật ký. */
    record StatusChange(String questCode, String before, String after) {}

    private QuestJournal() {}

    /** Bảng nhật ký một lần đọc; sắp theo {@code quest_code} để hai lần chạy in ra cùng thứ tự. */
    static String renderTable(List<QuestLogEntry> entries) {
        if (entries.isEmpty()) {
            return "  (nhật ký trống)";
        }
        StringBuilder table = new StringBuilder(128);
        List<QuestLogEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(QuestLogEntry::getQuestCode));
        for (int index = 0; index < sorted.size(); index++) {
            QuestLogEntry entry = sorted.get(index);
            table.append(String.format(Locale.ROOT, "  %-28s %s", entry.getQuestCode(), statusName(entry.getStatus())));
            if (index < sorted.size() - 1) {
                table.append(NEWLINE);
            }
        }
        return table.toString();
    }

    /** Chỉ những quest THẬT SỰ đổi trạng thái (gồm cả quest mới xuất hiện), sắp theo mã quest. */
    static List<StatusChange> between(List<QuestLogEntry> before, List<QuestLogEntry> after) {
        Map<String, String> statusBefore = indexByQuestCode(before);
        Map<String, String> statusAfter = indexByQuestCode(after);
        Set<String> allCodes = new TreeSet<>(statusBefore.keySet());
        allCodes.addAll(statusAfter.keySet());

        List<StatusChange> changes = new ArrayList<>();
        for (String questCode : allCodes) {
            String from = statusBefore.getOrDefault(questCode, ABSENT);
            String to = statusAfter.getOrDefault(questCode, ABSENT);
            if (!from.equals(to)) {
                changes.add(new StatusChange(questCode, from, to));
            }
        }
        return changes;
    }

    static String renderChanges(List<StatusChange> changes) {
        if (changes.isEmpty()) {
            return "  (không quest nào đổi trạng thái)";
        }
        StringBuilder rendered = new StringBuilder(128);
        for (int index = 0; index < changes.size(); index++) {
            StatusChange change = changes.get(index);
            rendered.append(String.format(
                    Locale.ROOT, "  %-28s %s -> %s", change.questCode(), change.before(), change.after()));
            if (index < changes.size() - 1) {
                rendered.append(NEWLINE);
            }
        }
        return rendered.toString();
    }

    private static Map<String, String> indexByQuestCode(List<QuestLogEntry> entries) {
        Map<String, String> byCode = new LinkedHashMap<>();
        for (QuestLogEntry entry : entries) {
            byCode.put(entry.getQuestCode(), statusName(entry.getStatus()));
        }
        return byCode;
    }

    /** Bỏ tiền tố {@code QUEST_STATUS_} — bảng đọc bằng mắt, không phải log máy đọc. */
    private static String statusName(QuestStatus status) {
        String rawName = status.name();
        String prefix = "QUEST_STATUS_";
        return rawName.startsWith(prefix) ? rawName.substring(prefix.length()) : rawName;
    }
}
