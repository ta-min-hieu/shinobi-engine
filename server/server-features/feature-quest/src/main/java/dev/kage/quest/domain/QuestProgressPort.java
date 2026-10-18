package dev.kage.quest.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Cổng lưu trữ tiến độ quest ({@code character_quest_progress}). */
public interface QuestProgressPort {

    /** {@code Optional.empty()} nghĩa là NOT_STARTED — chưa nhận quest này (không có dòng nào). */
    Optional<QuestProgress> find(long characterId, long questTemplateId);

    /** Upsert theo khoá {@code (characterId, questTemplateId)} — luôn ghi trong transaction đang mở. */
    void save(Transaction tx, QuestProgress progress);

    /**
     * Ghi dòng tiến độ ĐẦU TIÊN — chỉ khi nhân vật CHƯA có dòng nào cho quest này. Dùng cho
     * {@code AcceptQuestUseCase}.
     *
     * <p><b>Vì sao có (2026-09-25).</b> Nhận quest từng là "{@link #find} thấy rỗng ⇒ {@link #save}"
     * — hai bước rời, và {@link #save} là upsert mù. Hai lệnh nhận của cùng một người chơi (bấm đúp)
     * chạy song song ở gateway; lần chậm đọc "chưa có" rồi ghi SAU khi người chơi đã làm xong và
     * lĩnh thưởng, kéo quest CLAIMED về IN_PROGRESS tiến độ 0 ⇒ làm lại và lĩnh lại được. Phép ghi
     * này để khoá chính {@code (character_id, quest_template_id)} phán quyết thay cho lần đọc.
     *
     * @return {@code false} nếu đã có dòng (bất kể status) — khi đó KHÔNG ghi gì
     */
    default boolean insertIfAbsent(Transaction tx, QuestProgress progress) {
        // Cùng lý do saveIfUnchanged: một lần GHI không có mặc định "vô hại".
        throw new UnsupportedOperationException("insertIfAbsent chưa được hiện thực ở " + getClass());
    }

    /**
     * Đổi {@code COMPLETED → CLAIMED} CHỈ KHI dòng vẫn đang {@code COMPLETED} — compare-and-set,
     * dùng làm BƯỚC ĐẦU TIÊN trong transaction lĩnh thưởng của {@code ClaimQuestRewardUseCase}.
     *
     * <p><b>Vì sao có (2026-09-25).</b> {@code idempotency_key} chỉ chặn lần gửi LẠI của cùng một
     * lệnh. Hai lệnh lĩnh khác nhau (mỗi lần bấm một key mới) cùng đọc thấy COMPLETED ngoài
     * transaction rồi cùng trao thưởng — đo thật: 8 lệnh lĩnh đồng thời trao 8 lần EXP. Lần ghi có
     * điều kiện này khoá dòng tiến độ và chỉ để MỘT giao dịch thắng; các giao dịch khác chờ khoá,
     * đánh giá lại {@code WHERE} trên bản đã commit và nhận 0 dòng.
     *
     * @return {@code false} nếu dòng không còn {@code COMPLETED} (đã có người lĩnh, hoặc chưa xong)
     *     — khi đó KHÔNG ghi gì, người gọi không được trao thưởng
     */
    default boolean markClaimedIfCompleted(Transaction tx, long characterId, long questTemplateId) {
        throw new UnsupportedOperationException("markClaimedIfCompleted chưa được hiện thực ở " + getClass());
    }

    /**
     * Ghi tiến độ mới CHỈ KHI dòng vẫn còn {@code IN_PROGRESS} với ĐÚNG {@code expectedProgressJson}
     * — compare-and-set, dùng cho {@code ProcessQuestEventUseCase}.
     *
     * <p><b>Vì sao có (2026-09-11).</b> Sự kiện của CÙNG một nhân vật chạy song song: mỗi con quái chết
     * là một Virtual Thread riêng ({@code ConnectedCharacterRegistry.lootExecutor}), nói chuyện NPC
     * lại chạy trên {@code authExecutor}. Với {@link #save} (upsert mù), hai sự kiện cùng đọc một
     * tiến độ rồi cùng ghi ⇒ một lần bị mất. Với quest đếm quái, cái giá là một cú giết không được
     * tính; với quest GIAO ĐỒ, cái giá là đồ bị trừ HAI lần cho một lần giao. Lần ghi có điều kiện
     * này biến "ghi đè mù" thành "phát hiện được xung đột" — người gọi đọc lại rồi chạy lại hook.
     *
     * @return {@code false} nếu dòng đã bị một lần ghi khác đổi đi (hoặc không còn IN_PROGRESS) —
     *     khi đó KHÔNG ghi gì
     */
    default boolean saveIfUnchanged(Transaction tx, QuestProgress progress, String expectedProgressJson) {
        // KHÔNG có mặc định "vô hại" cho một lần GHI: trả true mà không ghi là âm thầm làm mất tiến
        // độ. Fake/anonymous class không đi qua ProcessQuestEventUseCase thì không bao giờ tới đây;
        // adapter thật bắt buộc override (QuestIntegrationTest gác).
        throw new UnsupportedOperationException("saveIfUnchanged chưa được hiện thực ở " + getClass());
    }

    /**
     * Nhiệm vụ ngày (ADR-055 D2): xoá dòng {@code CLAIMED} lĩnh TRƯỚC {@code resetAt} để nhận lại được.
     * Compare-and-delete — hai lệnh nhận song song: một xoá, một thấy 0 dòng, rồi {@link #insertIfAbsent}
     * phán quyết như thường.
     *
     * @return {@code true} nếu đã xoá một dòng
     */
    default boolean deleteClaimedBefore(Transaction tx, long characterId, long questTemplateId, Instant resetAt) {
        throw new UnsupportedOperationException("deleteClaimedBefore chưa được hiện thực ở " + getClass());
    }

    /**
     * Nhật ký như {@link #listLog(long)} nhưng BỎ dòng nhiệm vụ ngày đã lĩnh trước {@code dailyResetAt}
     * (ADR-055 D2 — hôm qua đã lĩnh thì hôm nay là "chưa nhận"). Mặc định = {@link #listLog(long)} cho
     * fake không có khái niệm nhiệm vụ ngày.
     */
    default List<QuestLogEntry> listLog(long characterId, Instant dailyResetAt) {
        return listLog(characterId);
    }

    /**
     * Xoá dòng tiến độ CHỈ KHI nó còn {@code IN_PROGRESS} — compare-and-delete cho
     * {@code AbandonQuestUseCase} (ADR-054). Một câu SQL là trọng tài: lần bỏ chạy đua với lần
     * hoàn thành thì đúng một bên thắng.
     *
     * @return {@code progress_json} của dòng vừa xoá (để ghi audit), hoặc rỗng nếu không có dòng
     *     {@code IN_PROGRESS} nào — khi đó KHÔNG xoá gì
     */
    default Optional<String> deleteIfInProgress(Transaction tx, long characterId, long questTemplateId) {
        // Cùng lý do saveIfUnchanged: một lần GHI không có mặc định "vô hại".
        throw new UnsupportedOperationException("deleteIfInProgress chưa được hiện thực ở " + getClass());
    }

    /**
     * MỌI dòng nhật ký của nhân vật, GỒM CẢ {@code CLAIMED} (ADR-035 Decision-4) — lọc phía server
     * thì client không phân biệt được "chưa từng nhận" với "đã trả xong". Trả {@code questCode}
     * (adapter tự join {@code quest_template}), không phải {@code questTemplateId}.
     *
     * <p>{@code default} rỗng để Fake/anonymous class hiện có không vỡ compile — cùng lý do đã ghi
     * ở {@link QuestTemplatePort#listAllScriptRefs}.
     */
    default List<QuestLogEntry> listLog(long characterId) {
        return List.of();
    }

    /**
     * Mọi quest đang IN_PROGRESS của nhân vật, kèm {@code scriptRef} để chạy hook ngay — dùng cho
     * {@code ProcessQuestEventUseCase}.
     *
     * <p><b>Vì sao có (2026-09-05).</b> Trước đây use case đó hardcode ĐÚNG MỘT mã quest
     * ({@code kill_training_dummies}) — placeholder có chủ đích, và feature-quest/CLAUDE.md ghi rõ
     * "khi có quest thứ 2 THEO DÕI GIẾT QUÁI, bắt buộc tổng quát hoá tra cứu này trước, không
     * copy-paste thêm hằng số hardcode thứ 2". Quest thứ hai của arc 1 (STORY.md §6) chính là lúc đó.
     *
     * <p><b>Vì sao lọc IN_PROGRESS trong SQL, không lọc trong Java:</b> số quest đã CLAIMED chỉ
     * tăng theo đời nhân vật, còn số quest đang làm thì nhỏ và có trần thực tế. Lọc ở DB giữ khối
     * lượng trả về bé bất kể người chơi đã chơi bao lâu.
     *
     * <p>⚠️ Đây vẫn CHƯA phải "index quest theo loại sự kiện" mà GAMEPLAY.md §9 mong muốn: mỗi lần
     * quái chết vẫn chạy hook của MỌI quest đang làm, để chính script tự quyết sự kiện có liên quan
     * không (Điều 27 — Java là cơ chế, Script là nội dung; Java KHÔNG được biết quest nào quan tâm
     * quái nào). Phạm vi quét nay bị chặn bởi số quest ĐANG LÀM của một người chơi, không phải toàn
     * bảng — đủ cho quy mô hiện tại. Đo lại khi một nhân vật ôm hàng chục quest cùng lúc.
     *
     * <p>{@code default} rỗng để Fake/anonymous class hiện có không vỡ compile — cùng lý do
     * {@link #listLog}.
     */
    default List<ActiveQuest> listInProgress(long characterId) {
        return List.of();
    }

    /**
     * Một quest đang làm, đủ dữ liệu để chạy hook mà không phải tra vòng hai.
     *
     * @param questTemplateId khoá để {@code save} tiến độ mới
     * @param questCode mã nghiệp vụ — thứ báo về client khi quest hoàn thành
     * @param scriptRef đường dẫn script GraalJS chứa hook {@code on_event}
     * @param progressJson chuỗi ĐỤC do script tự quản, Java không đọc nội dung
     */
    record ActiveQuest(long questTemplateId, String questCode, String scriptRef, String progressJson) {}
}
