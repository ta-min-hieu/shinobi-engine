package dev.kage.dialogue.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;
import dev.kage.dialogue.domain.NpcTemplatePort;
import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;

/**
 * Tra {@code npc_template.dialogue_script_ref} theo {@code npcCode} rồi chạy script lấy 1
 * {@link DialogueNode} theo {@code nodeId} — script trả về TOÀN BỘ cây hội thoại (object, key =
 * nodeId), Java trích đúng 1 nhánh cần dùng (GAMEPLAY.md §9.2).
 *
 * <p>⚠️ Trích xuất PHẢI chạy qua {@link ScriptSandbox#eval(String, Map, java.util.function.Function)}
 * (extractor chạy trong lúc {@code Context} còn mở) — gọi {@code getMember}/{@code getArrayElement}
 * trên {@code Value} trả về từ overload thường sau khi hàm return đã ném {@code IllegalStateException}
 * thật (xác nhận bằng test ở {@code engine-scripting}, không đoán trước).
 */
public final class GetDialogueNodeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetDialogueNodeUseCase.class);
    private static final long SCRIPT_MAX_STATEMENTS = 10_000L; // cùng độ lớn SkillConstants (server-world)

    private final NpcTemplatePort npcTemplatePort;
    // Không chạy trên tick ⇒ trần chống treo rộng (README dòng 70) — xem ScriptSandbox.OFF_TICK_TIMEOUT.
    private final ScriptSandbox scriptSandbox =
            new ScriptSandbox(SCRIPT_MAX_STATEMENTS, ScriptSandbox.OFF_TICK_TIMEOUT);
    // Script không đổi lúc chạy (chỉ đổi khi deploy lại + restart server) — cache theo
    // scriptRef tránh đọc lại classpath resource mỗi lần người chơi nói chuyện với NPC.
    private final Map<String, String> scriptSourceCache = new ConcurrentHashMap<>();

    public GetDialogueNodeUseCase(NpcTemplatePort npcTemplatePort) {
        this.npcTemplatePort = npcTemplatePort;
    }

    public Optional<DialogueNode> execute(String npcCode, String nodeId) {
        // npcCode đến thẳng từ packet. U+0000 không thể có trong npc_template.code — và Postgres từ
        // chối nó ngay cả ở câu SELECT, nên trước 2026-10-02 client nhận 500 thay vì 404.
        if (npcCode.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        Optional<String> scriptRef = npcTemplatePort.findDialogueScriptRefByCode(npcCode);
        if (scriptRef.isEmpty()) {
            return Optional.empty();
        }
        String source = scriptSourceCache.computeIfAbsent(scriptRef.get(), GetDialogueNodeUseCase::loadScript);
        try {
            return Optional.ofNullable(scriptSandbox.eval(source, Map.of(), root -> extractNode(root, nodeId)));
        } catch (ScriptExecutionException e) {
            LOG.error("Script dialogue lỗi lúc chạy: npcCode={}, scriptRef={}", npcCode, scriptRef.get(), e);
            return Optional.empty();
        }
    }

    /**
     * {@code null} nếu {@code nodeId} không tồn tại trong cây — Optional bọc lại ở {@link #execute}.
     *
     * <p>⚠️ Chỉ tính key RIÊNG của cây, không dùng {@code getMember} trần: {@code nodeId} đến thẳng
     * từ packet, và {@code getMember} đi theo chuỗi prototype — client gửi {@code "constructor"}/
     * {@code "toString"} thì nhận về một hàm JS, extractor ném NPE và gateway trả 500 thay vì 404
     * (test {@code shouldReturnEmpty_whenNodeIdNamesAnInheritedObjectProperty}). Phải DUYỆT tập key:
     * {@code getMemberKeys().contains(...)} của GraalJS cũng hỏi qua chuỗi prototype (đo thật).
     */
    private static DialogueNode extractNode(Value root, String nodeId) {
        if (!isOwnKey(root, nodeId)) {
            return null;
        }
        Value node = root.getMember(nodeId);
        String textKey = requiredString(node, "text_key", nodeId);
        Value optionsValue = node.getMember("options");
        if (optionsValue == null || !optionsValue.hasArrayElements()) {
            throw malformed(nodeId, "options");
        }
        ArrayList<DialogueOption> options = new ArrayList<DialogueOption>();
        long size = optionsValue.getArraySize();
        for (long i = 0; i < size; i++) {
            Value option = optionsValue.getArrayElement(i);
            options.add(new DialogueOption(
                    requiredString(option, "label_key", nodeId), requiredString(option, "action", nodeId)));
        }
        return new DialogueNode(nodeId, textKey, options);
    }

    /**
     * Trường chuỗi bắt buộc của một node — thiếu hoặc sai kiểu là lỗi SCRIPT (dòng 77(e), chủ dự án
     * duyệt 2026-10-17). Trước đó {@code getMember(...).asString()} trần ném NPE/ClassCastException
     * KHÔNG bọc: lọt khỏi {@code ScriptSandbox} (chỉ bọc lỗi polyglot) thành lỗi 500 ở gateway, thay vì
     * dòng log "script dialogue lỗi" có tên NPC và đường dẫn script.
     */
    private static String requiredString(Value object, String member, String nodeId) {
        if (object == null || !object.hasMember(member)) {
            throw malformed(nodeId, member);
        }
        Value value = object.getMember(member);
        if (value == null || !value.isString()) {
            throw malformed(nodeId, member);
        }
        return value.asString();
    }

    private static ScriptExecutionException malformed(String nodeId, String member) {
        return new ScriptExecutionException(
                "Node hội thoại '" + nodeId + "' thiếu hoặc sai kiểu trường '" + member + "'", null);
    }

    private static boolean isOwnKey(Value root, String nodeId) {
        for (String key : root.getMemberKeys()) {
            if (key.equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private static String loadScript(String resourcePath) {
        try (InputStream in = GetDialogueNodeUseCase.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy script dialogue: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Đọc script dialogue thất bại: " + resourcePath, e);
        }
    }
}
