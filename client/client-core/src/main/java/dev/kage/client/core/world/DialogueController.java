package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DialogueOptionView;

/**
 * Trạng thái một cuộc hội thoại đang mở (2026-08-09) — thuần Java, không libGDX/GL, test được thẳng
 * (UI.md §4: controller giữ trạng thái, widget chỉ vẽ).
 *
 * <p>Giữ đúng ba thứ mà giao thức bắt client phải nhớ giữa hai lần bấm: <b>NPC nào</b>, <b>node
 * nào</b>, và <b>danh sách lựa chọn của node đó</b>. Ba thứ này phải khớp nhau vì
 * {@code SelectDialogueOptionCommand} định danh lựa chọn bằng <b>chỉ số</b> chứ không bằng nội dung
 * ({@code npc_code} + {@code current_node_id} + {@code option_index}, xem dialogue.proto): server
 * tự tra lại {@code action} thật từ script theo chỉ số đó và KHÔNG tin client gửi thẳng action
 * (Điều 24). Gửi chỉ số kèm sai {@code current_node_id} là chọn nhầm nhánh của cây hội thoại.
 *
 * <p>Lớp này KHÔNG dùng {@code action} để điều hướng — hội thoại đi tiếp tới đâu là do gói TRẢ VỀ
 * quyết định, không phải do client đoán trước từ {@link DialogueOptionView#getAction()}. Trường đó
 * vẫn được GIỮ để đọc ({@link #optionAction}), đúng như dialogue.proto cho phép ("chỉ để client hiển
 * thị"); cái bị cấm là gửi nó ngược lại server, và điều đó không xảy ra ở đâu cả.
 */
final class DialogueController {

    /** Không có cuộc hội thoại nào đang mở — giá trị của {@link #npcCode} lúc đóng. */
    private static final String CLOSED = "";

    /** Server đánh {@code sequenceId} từ 1, nên 0 không bao giờ trùng một lệnh thật. */
    private static final int NO_PENDING_COMMAND = 0;

    private String npcCode = CLOSED;
    private String currentNodeId = "";

    /** {@code sequenceId} của lệnh hội thoại vừa gửi mà chưa có câu trả lời. */
    private int pendingSequenceId = NO_PENDING_COMMAND;

    private String textKey = "";
    private final List<String> optionLabelKeys = new ArrayList<>();

    /**
     * {@code action} của từng lựa chọn, song song với {@link #optionLabelKeys}.
     *
     * <p>Giữ lại CHỈ để đọc, không bao giờ gửi đi — dialogue.proto nói rõ trường này "chỉ để client
     * hiển thị, KHÔNG gửi lại khi chọn". Người dùng thật của nó là {@code QuestCoordinator}: mã
     * nhiệm vụ nằm trong {@code "accept_quest:<code>"} và KHÔNG có ở bất kỳ chỗ nào khác —
     * {@code AcceptQuestResponse} chỉ mang {@code outcome}, nên không đọc ở đây thì client vĩnh viễn
     * không biết mình vừa nhận nhiệm vụ nào.
     */
    private final List<String> optionActions = new ArrayList<>();

    /**
     * Người chơi vừa click một NPC — ghi nhớ mã NPC NGAY, trước khi server trả lời.
     *
     * <p>Phải nhớ sớm như vậy vì {@code DialogueNodeResponse} <b>không mang {@code npc_code}</b>
     * (dialogue.proto chỉ có {@code node_id}/{@code text_key}/{@code options}), mà lệnh chọn tiếp
     * theo thì bắt buộc phải có. Nếu đợi tới lúc gói về mới ghi thì không còn nguồn nào để lấy.
     *
     * @return {@code false} nếu đang có hội thoại mở — bỏ qua click, không chồng hai cuộc lên nhau.
     */
    boolean beginTalk(String npcCode) {
        if (isOpen()) {
            return false;
        }
        this.npcCode = npcCode;
        this.currentNodeId = "";
        this.textKey = "";
        optionLabelKeys.clear();
        optionActions.clear();
        return true;
    }

    /** Server trả về một node — thay toàn bộ nội dung đang hiển thị. */
    void onNode(DialogueNodeResponse response) {
        pendingSequenceId = NO_PENDING_COMMAND;
        currentNodeId = response.getNodeId();
        textKey = response.getTextKey();
        optionLabelKeys.clear();
        optionActions.clear();
        for (DialogueOptionView option : response.getOptionsList()) {
            optionLabelKeys.add(option.getLabelKey());
            optionActions.add(option.getAction());
        }
    }

    /**
     * Đóng hội thoại. Gọi khi {@code DialogueEndedResponse}, khi server rẽ sang luồng khác
     * (nhận quest / mở cửa hàng), và khi lỗi — mọi nhánh KHÔNG phải {@code DialogueNodeResponse} đều
     * kết thúc cuộc hội thoại, vì server không giữ state nào để nói tiếp
     * ({@code GetDialogueNodeUseCase} không có phiên).
     */
    /**
     * Ghi lại lệnh hội thoại vừa gửi (bắt chuyện hoặc chọn một lựa chọn). Server từ chối bằng
     * {@code ErrorResponse} mang đúng {@code sequenceId} này — NPC không còn, hoặc đứng xa quá (từ
     * 2026-10-11 server kiểm khoảng cách tới NPC).
     */
    void onCommandSent(int sequenceId) {
        pendingSequenceId = sequenceId;
    }

    /**
     * {@code true} nếu {@code relatedSequenceId} là của lệnh hội thoại đang chờ trả lời. Cuộc thoại
     * đã đóng thì không lệnh nào còn chờ.
     */
    boolean isPendingCommand(int relatedSequenceId) {
        return isOpen() && pendingSequenceId != NO_PENDING_COMMAND && pendingSequenceId == relatedSequenceId;
    }

    void close() {
        pendingSequenceId = NO_PENDING_COMMAND;
        npcCode = CLOSED;
        currentNodeId = "";
        textKey = "";
        optionLabelKeys.clear();
        optionActions.clear();
    }

    boolean isOpen() {
        return !CLOSED.equals(npcCode);
    }

    /** True khi đã click NPC nhưng gói node đầu tiên chưa về — panel chưa có gì để vẽ. */
    boolean isAwaitingFirstNode() {
        return isOpen() && textKey.isEmpty();
    }

    String npcCode() {
        return npcCode;
    }

    String currentNodeId() {
        return currentNodeId;
    }

    String textKey() {
        return textKey;
    }

    List<String> optionLabelKeys() {
        return Collections.unmodifiableList(optionLabelKeys);
    }

    /**
     * True nếu {@code index} là một lựa chọn có thật của node đang mở.
     *
     * <p>Kiểm ở client KHÔNG phải để bảo vệ server (server tự kiểm lại — Điều 15) mà để không gửi
     * gói chắc chắn vô nghĩa: một chỉ số ngoài dải chỉ đổi lại được một
     * {@code error.dialogue_option_not_found}.
     */
    boolean isSelectableOption(int index) {
        return isOpen() && index >= 0 && index < optionLabelKeys.size();
    }

    /**
     * {@code action} của một lựa chọn — chuỗi RỖNG nếu chỉ số không hợp lệ. Xem
     * {@link #optionActions} về việc chỉ được đọc, không được gửi lại.
     */
    String optionAction(int index) {
        return isSelectableOption(index) ? optionActions.get(index) : "";
    }
}
