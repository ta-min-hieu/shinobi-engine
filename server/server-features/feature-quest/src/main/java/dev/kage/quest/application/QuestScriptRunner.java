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

    // Không chạy trên tick ⇒ trần chống treo rộng (README dòng 70) — xem ScriptSandbox.OFF_TICK_TIMEOUT.
    private final ScriptSandbox scriptSandbox =
            new ScriptSandbox(SCRIPT_MAX_STATEMENTS, ScriptSandbox.OFF_TICK_TIMEOUT);
    // Script không đổi lúc chạy — cache theo scriptRef, cùng lý do GetDialogueNodeUseCase.
    private final Map<String, String> scriptSourceCache = new ConcurrentHashMap<>();

    private static final String ON_EVENT_HOOK = "on_event";
    private static final String ON_ACCEPT_HOOK = "on_accept";
    private static final String ON_CLAIM_HOOK = "on_claim";

    /**
     * Hiệu ứng một lần chạy hook.
     *
     * @param itemTakes vật phẩm script đòi lấy khỏi túi ({@code ctx.take_item}) — CHỈ có ở
     *     {@code on_event}, xem {@link #runEvent}
     * @param scriptFailed script ném lỗi giữa chừng. Khi {@code true}, các trường còn lại là hiệu
     *     ứng DỞ DANG (những callback đã kịp gọi trước chỗ lỗi) — và từ 2026-09-12 CẢ BA đường vào
     *     đều bỏ HẾT: {@code ProcessQuestEventUseCase}, {@code AcceptQuestUseCase} (không ghi tiến
     *     độ) và {@code ClaimQuestRewardUseCase} (không trao thưởng, không đổi CLAIMED, không đốt
     *     {@code idempotencyKey}). Không còn đường nào dùng hiệu ứng dở dang.
     */
    record HookEffects(
            String progressJson,
            boolean completed,
            long expGranted,
            List<ItemGrant> itemGrants,
            List<ItemTake> itemTakes,
            boolean scriptFailed) {}

    /**
     * Một lần {@code ctx.give_item(code, qty)}. {@code quantity} phải dương (sửa 2026-10-10) — chiều
     * ngược của {@link ItemTake}: số âm đi xuống câu gộp chồng {@code quantity = quantity + ?} của
     * adapter là TRỪ đồ. Đo trên Postgres thật, túi có sẵn 3 phi đao, {@code give_item("kunai", -5)}:
     * dòng {@code inventory_item} thành {@code quantity = -2} (cột chưa có CHECK tới V52), và vì
     * {@code ItemStack} từ chối số lượng ≤ 0 trong constructor nên từ đó túi đồ của nhân vật không
     * đọc lên được nữa — trong khi lượt lĩnh vẫn trả OK và quest thành CLAIMED. Số {@code 0} (và số
     * âm khi túi không có chồng nào) thì không ghi dòng nào mà quest vẫn CLAIMED: mất thưởng im lặng.
     *
     * <p>KHÔNG có trần trên ở đây có chủ đích: trần thật là sức chứa túi, chỉ tầng túi đồ biết —
     * {@code Integer.MAX_VALUE} đi qua và bị từ chối ở đó ({@code INVENTORY_FULL}, rollback trọn).
     */
    record ItemGrant(String itemTemplateCode, int quantity) {
        ItemGrant {
            requireItemCode("give_item", itemTemplateCode);
            if (quantity <= 0) {
                throw new IllegalArgumentException("give_item cần số lượng dương: " + quantity);
            }
        }
    }

    /**
     * Một lần {@code ctx.take_item(code, qty)}. {@code quantity} phải dương: số âm đi xuống câu
     * {@code quantity - ?} của adapter sẽ thành CỘNG đồ — chặn ngay ở đây, và vì bị ném từ trong
     * callback nên nó thành lỗi script ({@link HookEffects#scriptFailed}), không lọt tới DB.
     */
    record ItemTake(String itemTemplateCode, int quantity) {
        ItemTake {
            requireItemCode("take_item", itemTemplateCode);
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

    /**
     * Chạy {@code on_accept}. Hook này PHẢI lưu tiến độ ban đầu, và thứ nó lưu phải là JSON (quyết định
     * chủ dự án 2026-10-11) — không thì kết quả mang {@link HookEffects#scriptFailed}.
     *
     * <p>Trước đó một {@code on_accept} quên {@code save_progress} (hoặc lưu một chuỗi không phải JSON)
     * vẫn tạo quest {@code IN_PROGRESS}; {@code JSON.parse} trong {@code on_event} rồi lỗi ở MỌI sự kiện
     * về sau, và {@code ALREADY_ACCEPTED} chặn nhận lại — quest kẹt vĩnh viễn. Nay việc nhận bị từ chối
     * ngay (use case không ghi gì khi script lỗi), nhận lại được sau khi script được sửa.
     */
    HookEffects runAccept(String scriptRef) {
        HookEffects effects = run(scriptRef, ON_ACCEPT_HOOK, "", null);
        if (effects.scriptFailed() || isJson(effects.progressJson())) {
            return effects;
        }
        LOG.error("Script quest on_accept không lưu tiến độ JSON hợp lệ: scriptRef={}", scriptRef);
        return new HookEffects(
                effects.progressJson(),
                effects.completed(),
                effects.expGranted(),
                effects.itemGrants(),
                effects.itemTakes(),
                true);
    }

    /**
     * {@code progressJson} có phải thứ {@code JSON.parse} của script đọc được không. Hỏi chính GraalJS
     * thay vì tự phân tích: Java không có thư viện JSON (feature-quest/CLAUDE.md), và câu trả lời duy
     * nhất có nghĩa là "{@code on_event} có parse nổi nó không". Chuỗi rỗng — tức chưa lưu gì — không
     * phải JSON.
     */
    private boolean isJson(String progressJson) {
        try {
            scriptSandbox.eval("JSON.parse(text); true;", Map.of("text", (Object) progressJson));
            return true;
        } catch (ScriptExecutionException e) {
            return false;
        }
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
        return run(scriptRef, ON_CLAIM_HOOK, currentProgressJson, null);
    }

    private HookEffects run(String scriptRef, String hookName, String currentProgressJson, Map<String, Object> event) {
        String source = scriptSourceCache.computeIfAbsent(scriptRef, QuestScriptRunner::loadScript);
        MutableEffects effects = new MutableEffects();

        Map<String, Object> ctx = buildContext(hookName, currentProgressJson, effects);

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

    /**
     * Dựng {@code ctx} của MỘT lần chạy hook — callback ghi thẳng vào {@code effects}.
     */
    private static Map<String, Object> buildContext(
            String hookName, String currentProgressJson, MutableEffects effects) {
        // Mỗi hàm chỉ có mặt ở hook mà hiệu ứng của nó được DÙNG (quyết định chủ dự án 2026-10-11). Trước
        // đó cả bốn hàm có ở mọi hook, và gọi sai chỗ bị bỏ qua im lặng: give_exp/give_item ở
        // on_accept/on_event không trao gì (thưởng chỉ được đọc ở on_claim), complete()/save_progress ở
        // on_claim không đổi gì. Nay gọi sai chỗ là gọi một hàm không tồn tại — lỗi script ồn ào, cùng
        // cách take_item đã làm từ 2026-09-11.
        HashMap<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("progress_json", currentProgressJson);
        if (ON_CLAIM_HOOK.equals(hookName)) {
            ctx.put("give_exp", (ProxyExecutable) args -> {
                // addExact: tràn long cũng là lỗi script, không phải một con số âm lặng lẽ.
                effects.expGranted = Math.addExact(effects.expGranted, requireNonNegativeExp(args[0].asLong()));
                return null;
            });
            ctx.put("give_item", (ProxyExecutable) args -> {
                effects.itemGrants.add(new ItemGrant(args[0].asString(), args[1].asInt()));
                return null;
            });
        } else {
            ctx.put("save_progress", (ProxyExecutable) args -> {
                effects.progressJson = requireProgressJson(args[0].asString());
                return null;
            });
            ctx.put("complete", (ProxyExecutable) args -> {
                effects.completed = true;
                return null;
            });
        }
        if (ON_EVENT_HOOK.equals(hookName)) {
            ctx.put("take_item", (ProxyExecutable) args -> {
                effects.itemTakes.add(new ItemTake(args[0].asString(), args[1].asInt()));
                return null;
            });
        }
        return ctx;
    }

    /**
     * {@code ctx.give_exp(n)} chỉ nhận {@code n >= 0} (sửa 2026-10-10). Số âm đi thẳng xuống
     * {@code exp = exp + ?}; cột {@code character.exp} chưa có CHECK tới V52, còn {@code Character} từ chối
     * exp âm ngay trong constructor — đo trên Postgres thật: sau một lần trừ quá tay,
     * {@code findById} VÀ {@code listByAccount} đều ném lỗi, tức nhân vật không vào được world và cả
     * tài khoản không liệt kê nổi nhân vật. Ném từ trong callback nên nó thành lỗi script
     * ({@link HookEffects#scriptFailed}) — cùng cách {@link ItemTake} chặn số lượng âm.
     */
    private static long requireNonNegativeExp(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("give_exp cần số không âm: " + amount);
        }
        return amount;
    }

    /**
     * Mã vật phẩm của {@code give_item}/{@code take_item} phải là một chuỗi có nội dung (sửa
     * 2026-10-10). {@code Value.asString()} trả {@code null} cho {@code null}/{@code undefined} của
     * JS thay vì ném, nên trước đó một biến gõ sai tên đi lọt xuống tận câu {@code WHERE code = ?}:
     * {@code give_item} thành "túi đầy" mãi mãi, {@code take_item} thành "chưa mang đủ đồ" mãi mãi —
     * cả hai đều im lặng và đổ lỗi cho người chơi. U+0000 thì Postgres từ chối ngay ở câu SELECT.
     *
     * <p>Mã KHÔNG TỒN TẠI thì tầng này không biết được — đó là việc của
     * {@code QuestRewardItemCodesIntegrationTest}.
     */
    private static void requireItemCode(String function, String itemTemplateCode) {
        if (itemTemplateCode == null || itemTemplateCode.isBlank() || itemTemplateCode.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(function + " cần mã vật phẩm là chuỗi không rỗng");
        }
    }

    /**
     * {@code ctx.save_progress(x)} chỉ nhận một chuỗi thật (sửa 2026-10-10). {@code null}/
     * {@code undefined} — kể cả {@code JSON.stringify(undefined)} — trước đó bị bỏ qua trong im lặng:
     * hook chạy "thành công" mà tiến độ không đổi. U+0000 không lưu được vào cột TEXT của Postgres,
     * nên để lọt là một exception SQL giữa transaction thay vì một lỗi script.
     *
     * <p>Nội dung chuỗi vẫn ĐỤC với Java: không kiểm nó có phải JSON hay không.
     */
    private static String requireProgressJson(String progressJson) {
        if (progressJson == null || progressJson.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("save_progress cần một chuỗi không null, không chứa U+0000");
        }
        return progressJson;
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
