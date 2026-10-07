package dev.kage.server.database.quest.fuzz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.domain.DialogueNode;

/**
 * Nội dung THẬT đọc từ DB sau Flyway, ghép với bảng {@link QuestFuzzSpec}: chuỗi tiên quyết, loài
 * quái + EXP của chúng, NPC có hội thoại, và đường bấm trong cây hội thoại dẫn tới từng lệnh
 * {@code accept_quest:<code>} (dò bằng chính {@link DialogueFacade}, không đọc file script).
 */
final class QuestFuzzContent {

    /** Mã vật phẩm dùng để nhồi túi đầy — trang bị, không xếp chồng, không quest nào lấy hay trao. */
    static final String JUNK_ITEM = "wooden_sword";

    /** Một bước bấm trong hội thoại: đang ở node nào, chọn lựa chọn thứ mấy. */
    record DialogueStep(String nodeId, int optionIndex) {}

    /** Đường từ lúc mở lời tới lựa chọn nhận quest. */
    record DialoguePath(String npcCode, List<DialogueStep> steps) {
        DialoguePath {
            steps = List.copyOf(steps);
        }
    }

    private final Map<String, QuestFuzzSpec> specs;
    private final List<String> chain;
    private final Map<String, String> prerequisites;
    private final Map<String, Long> monsterExp;
    private final List<String> npcs;
    private final Map<String, DialoguePath> acceptPaths;

    private QuestFuzzContent(Builder builder) {
        this.specs = Collections.unmodifiableMap(new LinkedHashMap<>(builder.specs)); // giữ thứ tự template id
        this.chain = List.copyOf(builder.chain);
        this.prerequisites = Map.copyOf(builder.prerequisites);
        this.monsterExp = Map.copyOf(builder.monsterExp);
        this.npcs = List.copyOf(builder.npcs);
        this.acceptPaths = Map.copyOf(builder.acceptPaths);
    }

    private static final class Builder {
        private final Map<String, QuestFuzzSpec> specs = new LinkedHashMap<>();
        private final List<String> chain = new ArrayList<>();
        private final Map<String, String> prerequisites = new HashMap<>();
        private final Map<String, Long> monsterExp = new HashMap<>();
        private final List<String> npcs = new ArrayList<>();
        private final Map<String, DialoguePath> acceptPaths = new HashMap<>();
    }

    static QuestFuzzContent load(QuestFuzzDb db, DialogueFacade dialogue) {
        Builder builder = new Builder();
        loadQuests(db, builder);
        builder.chain.addAll(chainOrder(builder.specs.keySet(), builder.prerequisites));
        builder.chain.remove(QuestFuzzSpec.FIXTURE_DELIVERY.code());
        for (QuestFuzzDb.Row row : db.rows("SELECT code, exp_reward FROM monster_template ORDER BY code")) {
            builder.monsterExp.put(row.first(), Long.parseLong(row.second()));
        }
        for (QuestFuzzDb.Row row :
                db.rows("SELECT code FROM npc_template WHERE dialogue_script_ref IS NOT NULL ORDER BY code")) {
            builder.npcs.add(row.first());
        }
        for (String npc : builder.npcs) {
            discoverAcceptPaths(dialogue, npc, builder.acceptPaths);
        }
        return new QuestFuzzContent(builder);
    }

    /** Ghép {@code quest_template} với bảng spec — ném nếu hai bên lệch nhau dù chỉ một quest. */
    private static void loadQuests(QuestFuzzDb db, Builder builder) {
        List<QuestFuzzDb.Row> quests = db.rows("""
                SELECT t.code, COALESCE(p.code, '') FROM quest_template t
                  LEFT JOIN quest_template p ON p.id = t.prerequisite_quest_id
                 ORDER BY t.id
                """);
        Map<String, QuestFuzzSpec> byCode = new HashMap<>();
        for (QuestFuzzSpec spec : QuestFuzzSpec.CONTENT) {
            byCode.put(spec.code(), spec);
        }
        byCode.put(QuestFuzzSpec.FIXTURE_DELIVERY.code(), QuestFuzzSpec.FIXTURE_DELIVERY);
        for (QuestFuzzDb.Row row : quests) {
            QuestFuzzSpec spec = byCode.remove(row.first());
            if (spec == null) {
                throw new IllegalStateException("quest_template '" + row.first()
                        + "' chưa có dòng trong QuestFuzzSpec.CONTENT — chép mục tiêu của script vào đó");
            }
            builder.specs.put(spec.code(), spec);
            if (!row.second().isEmpty()) {
                builder.prerequisites.put(row.first(), row.second());
            }
        }
        if (!byCode.isEmpty()) {
            throw new IllegalStateException("QuestFuzzSpec có quest không còn trong DB: " + byCode.keySet());
        }
    }

    /** Thứ tự đi chuỗi: quest nào tiên quyết đã đứng trước thì được xếp — ném nếu có vòng. */
    private static List<String> chainOrder(Set<String> codes, Map<String, String> prerequisites) {
        List<String> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        Set<String> remaining = new TreeSet<>(codes);
        while (!remaining.isEmpty()) {
            List<String> ready = remaining.stream()
                    .filter(code -> !prerequisites.containsKey(code) || placed.contains(prerequisites.get(code)))
                    .toList();
            if (ready.isEmpty()) {
                throw new IllegalStateException("Chuỗi tiên quyết có vòng: " + remaining);
            }
            for (String code : ready) {
                ordered.add(code);
                placed.add(code);
                remaining.remove(code);
            }
        }
        return ordered;
    }

    /** Duyệt rộng cây hội thoại qua {@code selectOption} — đúng đường client đi. */
    private static void discoverAcceptPaths(DialogueFacade dialogue, String npc, Map<String, DialoguePath> paths) {
        Optional<DialogueNode> start = dialogue.startDialogue(npc);
        if (start.isEmpty()) {
            return;
        }
        Deque<Map.Entry<DialogueNode, List<DialogueStep>>> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(Map.entry(start.get(), List.of()));
        visited.add(start.get().nodeId());
        while (!queue.isEmpty()) {
            Map.Entry<DialogueNode, List<DialogueStep>> current = queue.poll();
            DialogueNode node = current.getKey();
            for (int i = 0; i < node.options().size(); i++) {
                List<DialogueStep> steps = new ArrayList<>(current.getValue());
                steps.add(new DialogueStep(node.nodeId(), i));
                switch (dialogue.selectOption(npc, node.nodeId(), i)) {
                    case DialogueFacade.SelectOptionResult.AcceptQuest accept ->
                        paths.putIfAbsent(accept.questCode(), new DialoguePath(npc, steps));
                    case DialogueFacade.SelectOptionResult.Node next -> {
                        if (visited.add(next.node().nodeId())) {
                            queue.add(Map.entry(next.node(), steps));
                        }
                    }
                    default -> {
                        // end / open_shop / không tìm thấy — lá của cây, không có gì để đi tiếp.
                    }
                }
            }
        }
    }

    QuestFuzzSpec spec(String code) {
        return specs.get(code);
    }

    boolean hasQuest(String code) {
        return specs.containsKey(code);
    }

    /** Mọi mã quest, theo thứ tự {@code quest_template.id} — đúng thứ tự xử lý sự kiện của production. */
    List<String> codesInTemplateOrder() {
        return List.copyOf(specs.keySet());
    }

    List<String> chain() {
        return chain;
    }

    Optional<String> prerequisiteOf(String code) {
        return Optional.ofNullable(prerequisites.get(code));
    }

    long expOf(String monster) {
        return monsterExp.getOrDefault(monster, 0L);
    }

    List<String> monsters() {
        return List.copyOf(new TreeSet<>(monsterExp.keySet()));
    }

    List<String> npcs() {
        return npcs;
    }

    Optional<DialoguePath> acceptPath(String questCode) {
        return Optional.ofNullable(acceptPaths.get(questCode));
    }

    Set<String> questsOfferedInDialogue() {
        return acceptPaths.keySet();
    }
}
