package dev.kage.quest.application;

import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chạy 1 hook quest script ({@code on_accept}/{@code on_event}/{@code on_claim}, SCRIPTING.md §5) —
 * KHÁC {@code GetDialogueNodeUseCase} (feature-dialogue): dialogue đọc CẤU TRÚC TRẢ VỀ từ script
 * (cần extractor chạy trong lúc Context còn mở), quest thì NGƯỢC LẠI — script gọi NGƯỢC vào các
 * hàm callback trên {@code ctx} ({@code save_progress}/{@code complete}/{@code give_exp}/
 * {@code give_item}), Java chỉ đọc lại {@link MutableEffects} (object Java thuần, không phải
 * {@code Value}) SAU KHI {@code eval} xong — không cần overload extractor vì không đọc {@code Value}
 * trả về của hook (luôn bỏ qua, hiệu ứng chỉ tới qua callback).
 *
 * <p>{@code progress_json} là CHUỖI ĐỤC với Java — script tự {@code JSON.parse}/
 * {@code JSON.stringify} (GraalJS built-in, xác nhận bằng test thật trước khi chọn cách này, xem
 * feature-quest/CLAUDE.md), Java không bao giờ phân tích nội dung.
 */
final class QuestScriptRunner {

    private static final Logger LOG = LoggerFactory.getLogger(QuestScriptRunner.class);
    private static final long SCRIPT_MAX_STATEMENTS = 10_000L; // cùng độ lớn SkillConstants/GetDialogueNodeUseCase

    private final ScriptSandbox scriptSandbox = new ScriptSandbox(SCRIPT_MAX_STATEMENTS);
    // Script không đổi lúc chạy — cache theo scriptRef, cùng lý do GetDialogueNodeUseCase.
    private final Map<String, String> scriptSourceCache = new ConcurrentHashMap<>();

    record HookEffects(String progressJson, boolean completed, long expGranted, List<ItemGrant> itemGrants) {
    }

    record ItemGrant(String itemTemplateCode, int quantity) {
    }

    private static final class MutableEffects {
        String progressJson;
        boolean completed;
        long expGranted;
        final List<ItemGrant> itemGrants = new ArrayList<>();
    }

    HookEffects runAccept(String scriptRef) {
        return run(scriptRef, "on_accept", "", null);
    }

    HookEffects runEvent(String scriptRef, String currentProgressJson, String eventType, String monsterCode) {
        Map<String, Object> event = Map.<String, Object>of("type", eventType, "monster_code", monsterCode);
        return run(scriptRef, "on_event", currentProgressJson, event);
    }

    HookEffects runClaim(String scriptRef, String currentProgressJson) {
        return run(scriptRef, "on_claim", currentProgressJson, null);
    }

    private HookEffects run(String scriptRef, String hookName, String currentProgressJson, Map<String, Object> event) {
        String source = scriptSourceCache.computeIfAbsent(scriptRef, QuestScriptRunner::loadScript);
        MutableEffects effects = new MutableEffects();

        HashMap<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("progress_json", currentProgressJson);
        ctx.put("save_progress", (ProxyExecutable) args -> {
            effects.progressJson = args[0].asString();
            return null;
        });
        ctx.put("complete", (ProxyExecutable) args -> {
            effects.completed = true;
            return null;
        });
        ctx.put("give_exp", (ProxyExecutable) args -> {
            effects.expGranted += args[0].asLong();
            return null;
        });
        ctx.put("give_item", (ProxyExecutable) args -> {
            effects.itemGrants.add(new ItemGrant(args[0].asString(), args[1].asInt()));
            return null;
        });

        Map<String, Object> hostApi =
                Map.of("ctx", (Object) ctx, "event", (Object) (event == null ? Map.of() : event));
        String wrapped = "var quest = " + source + "; quest." + hookName + "(ctx, event);";
        try {
            scriptSandbox.eval(wrapped, hostApi);
        } catch (ScriptExecutionException e) {
            LOG.error("Script quest lỗi lúc chạy: scriptRef={}, hook={}", scriptRef, hookName, e);
            // KHÔNG tự ý complete/trao thưởng khi script lỗi — coi như không có hiệu ứng gì xảy ra.
        }

        String resultProgressJson = effects.progressJson != null ? effects.progressJson : currentProgressJson;
        List<ItemGrant> itemGrants = List.copyOf(effects.itemGrants);
        return new HookEffects(resultProgressJson, effects.completed, effects.expGranted, itemGrants);
    }

    private static String loadScript(String resourcePath) {
        try (InputStream in = QuestScriptRunner.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy script quest: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Đọc script quest thất bại: " + resourcePath, e);
        }
    }
}
