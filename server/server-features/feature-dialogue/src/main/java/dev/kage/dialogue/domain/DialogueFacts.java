package dev.kage.dialogue.domain;

/**
 * Những gì script hội thoại được phép BIẾT về người đang nói chuyện — chỉ đọc, qua {@code ctx.*}
 * (dòng 65(c), chủ dự án duyệt 2026-10-17). Có nó, NPC nhận đồ nói được "con chưa mang đủ" thay vì
 * chào như thường rồi để quest đứng im không một lời.
 *
 * <p>Feature này không đọc bảng của quest/túi đồ (server/CLAUDE.md): người gọi (gateway) dựng bản
 * thật từ {@code QuestFacade}/{@code InventoryFacade} theo nhân vật trong SESSION.
 *
 * <p>⚠️ Script chỉ được đổi {@code text_key} theo các dữ kiện này, KHÔNG đổi danh sách lựa chọn:
 * {@code optionIndex} là VỊ TRÍ, và lượt chọn đánh giá lại cây — dữ kiện đổi giữa hai lượt thì
 * vị trí trỏ sang lựa chọn khác.
 */
public interface DialogueFacts {

    /** Trạng thái của quest chưa từng nhận (hoặc mã quest không tồn tại). */
    String NOT_ACCEPTED = "NOT_ACCEPTED";

    /** Không biết gì về người chơi: mọi quest {@link #NOT_ACCEPTED}, túi rỗng. */
    DialogueFacts NONE = new DialogueFacts() {
        @Override
        public String questStatus(String questCode) {
            return NOT_ACCEPTED;
        }

        @Override
        public int itemCount(String itemTemplateCode) {
            return 0;
        }

        @Override
        public String questProgressJson(String questCode) {
            return NO_PROGRESS;
        }
    };

    /** Tiến độ của quest không đang làm — một object JSON rỗng, để {@code JSON.parse} không bao giờ ném. */
    String NO_PROGRESS = "{}";

    /**
     * {@code ctx.quest_status(code)}: {@link #NOT_ACCEPTED}, hoặc tên một giá trị {@code QuestStatus}
     * ({@code IN_PROGRESS}/{@code COMPLETED}/{@code CLAIMED}).
     */
    String questStatus(String questCode);

    /** {@code ctx.item_count(code)}: tổng số lượng món mang mã này đang nằm trong túi. */
    int itemCount(String itemTemplateCode);

    /**
     * {@code ctx.quest_progress(code)}: chuỗi {@code progress_json} do script QUEST tự ghi, nếu quest đang
     * làm; {@link #NO_PROGRESS} nếu không. Script hội thoại tự {@code JSON.parse} — và phải biết đúng hình
     * dạng mà script quest kia ghi (vd {@code {felled: 1, delivered: false}}).
     */
    String questProgressJson(String questCode);
}
