package dev.kage.server.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NPC giao nhiệm vụ (ADR-228) — đọc script hội thoại {@code /scripts/dialogue/<mã NPC>.js} và lấy mọi hành động
 * {@code accept_quest:<mã nhiệm vụ>} (luật hành động của feature-dialogue). Cùng mã nhiệm vụ ở nhiều NPC thì NPC đọc
 * trước thắng. Chỉ để gợi ý đường đi.
 */
final class QuestGivers {

    private static final Pattern ACCEPT = Pattern.compile("accept_quest:([a-z0-9_]+)");
    private static final Pattern NPC_CODE = Pattern.compile("[a-z0-9_]{1,32}");

    private QuestGivers() {}

    /** {@code mã nhiệm vụ → mã NPC giao} cho các NPC {@code npcCodes}. */
    static Map<String, String> index(Collection<String> npcCodes) {
        Map<String, String> givers = new HashMap<>();
        for (String npc : npcCodes) {
            if (NPC_CODE.matcher(npc).matches()) {
                questsIn(read(npc)).forEach(quest -> givers.putIfAbsent(quest, npc));
            }
        }
        return givers;
    }

    /** Mọi mã nhiệm vụ trong {@code script} theo thứ tự xuất hiện. Tách riêng để test. */
    static java.util.List<String> questsIn(String script) {
        java.util.List<String> quests = new java.util.ArrayList<>();
        Matcher matcher = ACCEPT.matcher(script);
        while (matcher.find()) {
            quests.add(matcher.group(1));
        }
        return quests;
    }

    private static String read(String npcCode) {
        try (InputStream in = QuestGivers.class.getResourceAsStream("/scripts/dialogue/" + npcCode + ".js")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được script hội thoại " + npcCode, e);
        }
    }
}
