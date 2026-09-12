package dev.kage.quest.application;

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

import dev.kage.engine.scripting.ScriptExecutionException;
import dev.kage.engine.scripting.ScriptSandbox;

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

    private static final String ON_EVENT_HOOK = "on_event";

    /**
     * Hiệu ứng một lần chạy hook.
     *
     * @param itemTakes vật phẩm script đòi lấy khỏi túi ({@code ctx.take_item}) — CHỈ có ở
     *     {@code on_event}, xem {@link #runEvent}
     * @param scriptFailed script ném lỗi giữa chừng. Khi {@code true}, các trường còn lại là hiệu
     *     ứng DỞ DANG (những callback đã kịp gọi trước chỗ lỗi) — {@code ProcessQuestEventUseCase} bỏ
     *     HẾT. ⚠️ {@code AcceptQuestUseCase}/{@code ClaimQuestRewardUseCase} chưa đọc cờ này và vẫn
     *     giữ hành vi cũ (dùng hiệu ứng dở dang); sửa hai đường đó là việc riêng.
     */
    record HookEffects(
            String progressJson,
            boolean completed,
            long expGranted,
            List<ItemGrant> itemGrants,
            List<ItemTake> itemTakes,
            boolean scriptFailed) {}

    record ItemGrant(String itemTemplateCode, int quantity) {}

    /**
     * Một lần {@code ctx.take_item(code, qty)}. {@code quantity} phải dương: số âm đi xuống câu
     * {@code quantity - ?} của adapter sẽ thành CỘNG đồ — chặn ngay ở đây, và vì bị ném từ trong
     * callback nên nó thành lỗi script ({@link HookEffects#scriptFailed}), không lọt tới DB.
     */
    record ItemTake(String itemTemplateCode, int quantity) {
        ItemTake {
            if (quantity <= 0) {
                throw new IllegalArgumentException("take_item cần số lượng dương: " + quantity);
            }
        }
    }

    private static final class MutableEffects {
        String progressJson;
        boolean completed;
        long expGranted;
        final List<ItemGrant> itemGrants = new ArrayList<>();
        final List<ItemTake> itemTakes = new ArrayList<>();
    }

    HookEffects runAccept(String scriptRef) {
        return run(scriptRef, "on_accept", "", null);
    }

    /**
     * Chạy {@code on_event} với MỘT sự kiện đã đóng gói sẵn.
     *
     * <p>⚠️ {@code event} là map các trường sẽ hiện ra trong JS dưới dạng {@code event.<tên>}, và
     * mỗi loại sự kiện mang bộ trường RIÊNG ({@code monster_killed} mang {@code monster_code},
     * {@code npc_talked} mang {@code npc_code}) — KHÔNG phải một bộ trường chung nhồi giá trị rỗng.
     * Đọc một trường không có trong map trả về {@code undefined} chứ không ném lỗi
     * ({@code ProxyObject.fromMap} khai {@code hasMember} theo {@code containsKey}), nên script của
     * quest loại này không vỡ khi nhận sự kiện loại kia — nhưng mọi script vẫn phải kiểm
     * {@code event.type} TRƯỚC, vì đó là thứ duy nhất phân biệt được hai loại sự kiện.
     *
     * <p>Vì sao là {@code Map} chứ không phải hai tham số {@code (eventType, monsterCode)} như bản
     * trước 2026-09-09: tham số thứ hai chỉ có nghĩa với đúng một loại sự kiện, nên thêm loại thứ
     * hai bằng cách thêm tham số thứ ba là con đường dẫn thẳng tới một chữ ký toàn {@code null}.
     *
     * <p><b>{@code ctx.take_item(code, qty)} CHỈ tồn tại ở hook này</b> (2026-09-11, mục tiêu "mang
     * vật X tới NPC Y"). Nó không lấy đồ ngay: nó là một ĐIỀU KIỆN gắn vào toàn bộ hiệu ứng của lần
     * chạy hook — {@code ProcessQuestEventUseCase} ghi tiến độ và lấy đồ trong CÙNG một transaction,
     * thiếu một món là rollback hết, như thể sự kiện chưa từng xảy ra. Ở {@code on_accept}/
     * {@code on_claim} hàm này KHÔNG được đưa vào {@code ctx}: gọi nó ở đó là lỗi script ồn ào, thay
     * vì một lời đòi đồ bị âm thầm bỏ qua.
     */
    HookEffects runEvent(String scriptRef, String currentProgressJson, Map<String, Object> event) {
        return run(scriptRef, ON_EVENT_HOOK, currentProgressJson, event);
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
        if (ON_EVENT_HOOK.equals(hookName)) {
            ctx.put("take_item", (ProxyExecutable) args -> {
                effects.itemTakes.add(new ItemTake(args[0].asString(), args[1].asInt()));
                return null;
            });
        }

        Map<String, Object> hostApi = Map.of("ctx", (Object) ctx, "event", (Object) (event == null ? Map.of() : event));
        String wrapped = "var quest = " + source + "; quest." + hookName + "(ctx, event);";
        boolean scriptFailed = false;
        try {
            scriptSandbox.eval(wrapped, hostApi);
        } catch (ScriptExecutionException e) {
            LOG.error("Script quest lỗi lúc chạy: scriptRef={}, hook={}", scriptRef, hookName, e);
            // KHÔNG tự ý complete/trao thưởng khi script lỗi — coi như không có hiệu ứng gì xảy ra.
            // Cờ dưới đây là thứ biến câu đó thành sự thật cho on_event (xem HookEffects.scriptFailed).
            scriptFailed = true;
        }

        String resultProgressJson = effects.progressJson != null ? effects.progressJson : currentProgressJson;
        List<ItemGrant> itemGrants = List.copyOf(effects.itemGrants);
        List<ItemTake> itemTakes = List.copyOf(effects.itemTakes);
        return new HookEffects(
                resultProgressJson, effects.completed, effects.expGranted, itemGrants, itemTakes, scriptFailed);
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
