package dev.kage.quest.domain;

import java.util.List;
import java.util.Optional;

/** Cổng lưu trữ tiến độ quest ({@code character_quest_progress}). */
public interface QuestProgressPort {

    /** {@code Optional.empty()} nghĩa là NOT_STARTED — chưa nhận quest này (không có dòng nào). */
    Optional<QuestProgress> find(long characterId, long questTemplateId);

    /** Upsert theo khoá {@code (characterId, questTemplateId)} — luôn ghi trong transaction đang mở. */
    void save(Transaction tx, QuestProgress progress);

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
