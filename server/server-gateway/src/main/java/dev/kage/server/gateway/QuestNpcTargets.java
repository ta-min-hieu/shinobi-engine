package dev.kage.server.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NPC cần gặp của một nhiệm vụ (ADR-221) — đọc thẳng từ script quest ({@code /scripts/quest/<mã>.js}, cùng file
 * {@code QuestScriptRunner} chạy): lần so khớp {@code npc_code === "<mã NPC>"} đầu tiên trong sự kiện
 * {@code npc_talked}. Script không gặp NPC nào (chỉ hạ quái / nhặt đồ) ⇒ chuỗi rỗng. Chỉ để client tự đi tới — không
 * cấp quyền gì; cache theo mã nhiệm vụ (script không đổi lúc chạy).
 */
final class QuestNpcTargets {

    private static final Pattern NPC_CHECK = Pattern.compile("npc_code\\s*===?\\s*[\"']([a-z0-9_]+)[\"']");
    private static final Pattern QUEST_CODE = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private QuestNpcTargets() {}

    /** Mã NPC cần gặp của {@code questCode}; rỗng nếu không có (hoặc không có script). */
    static String of(String questCode) {
        if (!QUEST_CODE.matcher(questCode).matches()) {
            return "";
        }
        return CACHE.computeIfAbsent(questCode, QuestNpcTargets::scan);
    }

    /** Lần so khớp đầu tiên trong {@code script}; rỗng nếu không có. Tách riêng để test. */
    static String firstNpcIn(String script) {
        Matcher matcher = NPC_CHECK.matcher(script);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String scan(String questCode) {
        try (InputStream in = QuestNpcTargets.class.getResourceAsStream("/scripts/quest/" + questCode + ".js")) {
            return in == null ? "" : firstNpcIn(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được script quest " + questCode, e);
        }
    }
}
