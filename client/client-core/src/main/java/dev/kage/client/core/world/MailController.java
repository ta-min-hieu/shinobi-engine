package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.kage.client.core.render.FontFactory;
import dev.kage.protocol.ClaimMailOutcome;
import dev.kage.protocol.DeleteMailOutcome;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MailView;
import dev.kage.protocol.SendMailOutcome;

/**
 * Trạng thái hòm thư (2026-08-27) — thuần Java, không libGDX/GL, test được thẳng (UI.md §4:
 * controller giữ trạng thái, widget chỉ vẽ). Cùng hình dạng {@link GuildController}.
 *
 * <p>Giữ đúng ba thứ: <b>danh sách thư</b> server vừa trả về, <b>lá thư đang đọc</b>, và luật
 * <b>biến bốn ô nhập thành một lá thư gửi đi được</b> ({@link #compose}). Cộng thêm một thứ không có
 * ở bang hội: <b>khoá idempotency của lần nhận thưởng đang treo</b> ({@link #beginClaim}).
 *
 * <p><b>Khoảng trống 1 — không tra được tên vật phẩm đính kèm.</b> {@code MailAttachmentView} chỉ
 * mang {@code item_template_id} + số lượng, và client CHƯA có catalog vật phẩm nào để tra tên/icon
 * (đúng Khoảng trống 2 của ADR-035, đang chờ chủ dự án duyệt). Lớp này vì vậy giữ nguyên con số và
 * để {@code MailCoordinator} hiện nó qua khoá {@code mail.attachment.raw_item} — KHÔNG bịa tên,
 * KHÔNG đoán loại vật phẩm.
 *
 * <p><b>Khoảng trống 2 — không tra được tên người gửi.</b> {@code MailView.sender_id} là một
 * {@code character_id} trần; mail.proto không mang tên, và client không có gói nào hỏi "id này tên
 * gì". Cùng cách xử lý: hiện số qua {@code mail.sender.raw_id}. {@code has_sender = false} là thư hệ
 * thống và có khoá riêng ({@code mail.sender.system}) — cờ riêng chứ không phải sentinel
 * {@code sender_id == 0}, đúng như mail.proto ghi.
 *
 * <p><b>Khoảng trống 3 — {@code ClaimMailResponse}/{@code DeleteMailResponse} không mang
 * {@code mail_id}.</b> Chúng chỉ có một trường {@code outcome}, nên khi trả lời về, client không có
 * cách nào biết nó thuộc lá thư nào ngoài việc TỰ NHỚ mình vừa bấm cái gì
 * ({@link #pendingClaimMailId}). Đúng với một cửa sổ mà người chơi bấm từng nút một, nhưng nó có
 * nghĩa là hai lệnh nhận thưởng chồng nhau sẽ không phân biệt được — đóng lỗ đó thật sự cần thêm
 * {@code mail_id} vào response, tức phải sửa .proto, nằm ngoài quyền của client.
 *
 * <p>Tiêu đề và nội dung thư là chữ do <b>người chơi khác</b> gõ, nên chúng đi qua
 * {@link ChatController#sanitize} y như tin nhắn chat — font UI sinh từ
 * {@link FontFactory#VIETNAMESE_CHARACTERS} là tập ĐÓNG và im lặng nuốt mọi glyph ngoài tập đó.
 */
final class MailController {

    /**
     * Khớp {@code VARCHAR(64)} của cột {@code mail.subject} (V13__create_mail_tables.sql). Nhân đôi
     * hằng số là CÓ CHỦ ĐÍCH, cùng lý do {@link ChatController#MAX_BODY_LENGTH}: client-core không
     * được phụ thuộc module server, và luật thật vẫn nằm ở DB.
     *
     * <p>⚠️ Như bang hội, ở đây client là hàng rào DUY NHẤT: {@code SendMailUseCase} phía server
     * KHÔNG kiểm độ dài, nó đưa thẳng chuỗi xuống {@code INSERT INTO mail}. Tiêu đề 65 ký tự sẽ nổ
     * {@code SQLException} trong {@code authExecutor} chứ không trả về outcome nào. Chặn ở đây
     * không thay thế được validate phía server (Điều 24) — nó chỉ khiến người chơi không vấp vào
     * cái hố đó trong lúc chờ server vá.
     */
    static final int MAX_SUBJECT_LENGTH = 64;

    /** Khớp {@code VARCHAR(1024)} của cột {@code mail.body} — xem {@link #MAX_SUBJECT_LENGTH}. */
    static final int MAX_BODY_LENGTH = 1024;

    /** Khớp {@code FriendController.MIN_NAME_LENGTH} — cùng một thứ: tên một nhân vật. */
    static final int MIN_RECIPIENT_NAME_LENGTH = 2;

    /** Khớp {@code FriendController.MAX_NAME_LENGTH} — xem {@link #MIN_RECIPIENT_NAME_LENGTH}. */
    static final int MAX_RECIPIENT_NAME_LENGTH = 16;

    /**
     * Số lá thư hiển thị cùng lúc. Trần cứng cùng lý do {@link GuildController#MAX_VISIBLE_ROWS}: số
     * thư do NGƯỜI KHÁC quyết (ai cũng gửi cho mình được, và thư hệ thống thì tự đến), không phải do
     * máy này quyết — và {@code KageSkin} chưa có style {@code ScrollPane} nào, nên danh sách dài sẽ
     * đội khung cao vượt khỏi màn hình chứ không tự cuộn.
     */
    static final int MAX_VISIBLE_ROWS = 6;

    /**
     * Một món đính kèm — chỉ có số, xem "Khoảng trống 1" ở javadoc lớp.
     *
     * @param itemTemplateId khoá chính của {@code item_template}, không phải {@code code} đọc được.
     * @param quantity số lượng, luôn dương (V13 có {@code CHECK (quantity > 0)}).
     */
    record Attachment(long itemTemplateId, int quantity) {}

    /**
     * Một lá thư trong hòm.
     *
     * @param hasSender {@code false} = thư hệ thống; lúc đó {@code senderId} KHÔNG có nghĩa
     *     (mail.proto), nên đừng hiện nó ra.
     * @param subject đã qua {@link ChatController#sanitize} — chữ của người khác.
     * @param body đã qua {@link ChatController#sanitize} — xem trên.
     * @param claimed đã lĩnh vàng/đính kèm chưa. Server không cho xoá thư chưa lĩnh mà còn đồ
     *     ({@code DELETE_MAIL_OUTCOME_MUST_CLAIM_FIRST}).
     */
    record Mail(
            long mailId,
            boolean hasSender,
            long senderId,
            String subject,
            String body,
            long goldAmount,
            List<Attachment> attachments,
            boolean claimed) {}

    /**
     * Một lá thư sắp gửi, đã qua kiểm ở mức client. Server vẫn là nơi quyết định cuối cùng
     * (Điều 24) — nhất là {@code recipientName}, thứ chỉ server tra được.
     */
    record Draft(String recipientName, String subject, String body, long goldAmount) {}

    /**
     * Kết quả biến bốn ô nhập thành một lá thư. Ba nhánh, {@code sealed} để chỗ gọi không thể quên
     * nhánh nào (/CLAUDE.md §7) — cùng hình dạng {@link GuildController.ComposeResult}.
     */
    sealed interface ComposeResult {

        /** Hợp lệ ở mức client, gửi đi được. */
        record Send(Draft draft) implements ComposeResult {}

        /** Không gửi, và nói cho người chơi biết vì sao. */
        record Reject(String messageKey) implements ComposeResult {}

        /** Chưa gõ gì cả — không gửi, KHÔNG báo lỗi (bấm nhầm nút không đáng bị mắng). */
        enum Nothing implements ComposeResult {
            INSTANCE
        }
    }

    private final List<Mail> mails = new ArrayList<>();

    /** Id lá thư đang mở đọc, 0 = chưa chọn lá nào. */
    private long selectedMailId;

    /** Có thư mới đến trong lúc đang chơi mà chưa mở hòm thư xem — xem {@link #onNewMail}. */
    private boolean newMailPending;

    /** Lá thư của lệnh nhận thưởng đang treo, 0 = không có lệnh nào đang treo. */
    private long pendingClaimMailId;

    /** Khoá ADR-025 của lệnh đang treo — rỗng khi không có lệnh nào đang treo. */
    private String pendingClaimKey = "";

    /**
     * Danh sách thư mới nhất từ server — thay TOÀN BỘ, không trộn với bản cũ.
     *
     * <p>Lá thư đang đọc chỉ giữ lại nếu nó CÒN trong danh sách mới. Vừa xoá một lá mà màn hình còn
     * treo nội dung của nó là nói dối người chơi bằng dữ liệu cũ — cùng luật
     * {@link GuildController#onMyGuild} dùng để dọn danh sách thành viên của bang vừa rời.
     */
    void onMailList(ListMailResponse response) {
        mails.clear();
        for (MailView view : response.getMailsList()) {
            mails.add(toMail(view));
        }
        if (findMail(selectedMailId).isEmpty()) {
            selectedMailId = 0L;
        }
    }

    private static Mail toMail(MailView view) {
        List<Attachment> attachments = new ArrayList<>(view.getAttachmentsCount());
        for (MailAttachmentView attachment : view.getAttachmentsList()) {
            attachments.add(new Attachment(attachment.getItemTemplateId(), attachment.getQuantity()));
        }
        return new Mail(
                view.getMailId(),
                view.getHasSender(),
                view.getSenderId(),
                ChatController.sanitize(view.getSubject()),
                ChatController.sanitize(view.getBody()),
                view.getGoldAmount(),
                List.copyOf(attachments),
                view.getClaimed());
    }

    /** Thư trong hòm, đúng thứ tự server trả về. */
    List<Mail> mails() {
        return List.copyOf(mails);
    }

    /** Lá thư đang mở đọc, rỗng khi chưa chọn lá nào (hoặc lá đang đọc vừa biến mất khỏi danh sách). */
    Optional<Mail> selected() {
        return findMail(selectedMailId);
    }

    /** Mở đọc một lá. Id lạ thì KHÔNG đổi gì — danh sách có thể đã đổi dưới chân cú bấm. */
    void select(long mailId) {
        if (findMail(mailId).isPresent()) {
            selectedMailId = mailId;
        }
    }

    private Optional<Mail> findMail(long mailId) {
        if (mailId == 0L) {
            return Optional.empty();
        }
        for (Mail mail : mails) {
            if (mail.mailId() == mailId) {
                return Optional.of(mail);
            }
        }
        return Optional.empty();
    }

    /**
     * Server đẩy {@code NewMailEvent}. Gói chỉ mang {@code mail_id} chứ không mang nội dung
     * (mail.proto nói rõ: "client tự gọi lại ListMailRequest để lấy nội dung đầy đủ"), nên ở đây chỉ
     * bật cờ — không dựng ra một lá thư nửa vời từ mỗi cái id.
     */
    void onNewMail() {
        newMailPending = true;
    }

    /** Có thư mới chưa xem không — {@code MailCoordinator} bày dấu hiệu trên cửa sổ theo câu này. */
    boolean hasNewMail() {
        return newMailPending;
    }

    /** Người chơi vừa mở hòm thư ra xem — dấu "có thư mới" hết nhiệm vụ. */
    void clearNewMailFlag() {
        newMailPending = false;
    }

    /**
     * Khoá idempotency cho một lệnh nhận thưởng (ADR-025).
     *
     * <p>Bấm lại Nhận trên ĐÚNG lá thư đang treo trả LẠI đúng khoá cũ — đó là "tự thử lại cùng một
     * lần nhận", server phải coi là một. Bấm sang lá khác, hoặc bấm lại sau khi lệnh cũ đã có kết
     * luận ({@link #onClaimSettled}), là một ý định MỚI và phải có khoá mới. Cùng hình dạng
     * {@link QuestTracker#claimKey()}.
     *
     * @param mailId lá thư sắp nhận thưởng.
     * @return UUID v4 gửi kèm {@code ClaimMailCommand}.
     */
    String beginClaim(long mailId) {
        if (pendingClaimMailId != mailId || pendingClaimKey.isEmpty()) {
            pendingClaimMailId = mailId;
            pendingClaimKey = UUID.randomUUID().toString();
        }
        return pendingClaimKey;
    }

    /**
     * Lần nhận thưởng đang treo đã có kết luận từ server (thành công, trùng lệnh, hay hỏng hẳn).
     *
     * <p>Xoá khoá kể cả khi hỏng, cùng lý do {@link QuestTracker#onClaimSettled}: một lệnh đã có kết
     * luận không còn là "lần thử lại" của lệnh cũ nữa, nên lần bấm sau là một ý định mới.
     */
    void onClaimSettled() {
        pendingClaimMailId = 0L;
        pendingClaimKey = "";
    }

    /** Lá thư của lệnh nhận thưởng đang treo — 0 khi không có lệnh nào treo. Chỉ dùng để test/log. */
    long pendingClaimMailId() {
        return pendingClaimMailId;
    }

    /**
     * Biến bốn ô nhập của khung soạn thư thành một lá thư gửi đi được.
     *
     * <p>Vàng nhận vào dưới dạng CHUỖI chứ không phải số: ô nhập là chữ, và "người chơi gõ chữ vào ô
     * số" là một nhánh có thật cần nói ra bằng lời ({@code mail.error.bad_gold}) chứ không phải một
     * {@code NumberFormatException} rơi ra ngoài.
     *
     * @param recipientName tên nhân vật nhận thư, đúng những gì đang nằm trong ô nhập.
     * @param subject tiêu đề, đúng những gì đang nằm trong ô nhập.
     * @param body nội dung, đúng những gì đang nằm trong ô nhập.
     * @param goldText số vàng đính kèm dạng chữ; rỗng nghĩa là không đính vàng.
     * @return xem {@link ComposeResult}.
     */
    ComposeResult compose(String recipientName, String subject, String body, String goldText) {
        String recipient = recipientName.strip();
        String trimmedSubject = subject.strip();
        if (recipient.isEmpty() && trimmedSubject.isEmpty() && body.isBlank()) {
            return ComposeResult.Nothing.INSTANCE;
        }
        if (recipient.length() < MIN_RECIPIENT_NAME_LENGTH || recipient.length() > MAX_RECIPIENT_NAME_LENGTH) {
            return new ComposeResult.Reject("mail.error.bad_recipient");
        }
        if (trimmedSubject.isEmpty()) {
            return new ComposeResult.Reject("mail.error.empty_subject");
        }
        if (trimmedSubject.length() > MAX_SUBJECT_LENGTH) {
            return new ComposeResult.Reject("mail.error.subject_too_long");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            return new ComposeResult.Reject("mail.error.body_too_long");
        }
        return parseGold(goldText)
                .<ComposeResult>map(gold -> new ComposeResult.Send(new Draft(recipient, trimmedSubject, body, gold)))
                .orElseGet(() -> new ComposeResult.Reject("mail.error.bad_gold"));
    }

    /**
     * Số vàng đính kèm, hoặc rỗng khi chuỗi không phải một số nguyên không âm.
     *
     * <p>Số ÂM bị chặn ở đây vì cột {@code mail.gold_amount} có {@code CHECK (gold_amount >= 0)}
     * (V13) mà {@code SendMailUseCase} không kiểm gì trước khi INSERT — gửi số âm lên là nổ
     * {@code SQLException} phía server chứ không nhận lại được outcome nào.
     */
    private static Optional<Long> parseGold(String goldText) {
        String trimmed = goldText.strip();
        if (trimmed.isEmpty()) {
            return Optional.of(0L);
        }
        try {
            long gold = Long.parseLong(trimmed);
            return gold < 0L ? Optional.empty() : Optional.of(gold);
        } catch (NumberFormatException e) {
            // Người chơi gõ chữ vào ô số — một nhánh bình thường của giao diện, không phải sự cố;
            // chỗ gọi biến nó thành mail.error.bad_gold. (Ngoại lệ CÓ CHỦ ĐÍCH của /CLAUDE.md §11,
            // cùng dạng WorldCommandSender nuốt IOException.)
            return Optional.empty();
        }
    }

    /** Rời màn hình/mất kết nối — hòm thư của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        mails.clear();
        selectedMailId = 0L;
        newMailPending = false;
        onClaimSettled();
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome.
    //
    // Bảng tra thay cho `switch` phủ hết enum — cùng cách GuildController làm, và cùng lý do: một
    // biểu thức switch phủ hết một enum protobuf tự nó đã vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt ở mọi bảng: cả hai rơi vào Optional.empty(), và
    // chỗ gọi dịch nó thành "mail.error.unknown". Cái sau là kết quả của một server MỚI HƠN client —
    // không thể liệt kê trước, chỉ có thể nói ra là không hiểu.
    //
    // OK có khoá riêng chứ không im lặng, cùng lý do bang hội: mail.proto chỉ đẩy NewMailEvent cho
    // NGƯỜI NHẬN, nên với người vừa bấm nút thì không có gì trên màn hình tự đổi cả.
    // ---------------------------------------------------------------------------------------------

    private static final Map<SendMailOutcome, String> SEND_MESSAGE_KEYS = Map.of(
            SendMailOutcome.SEND_MAIL_OUTCOME_OK, "mail.notice.sent",
            SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND, "mail.error.recipient_not_found");

    private static final Map<ClaimMailOutcome, String> CLAIM_MESSAGE_KEYS = Map.of(
            ClaimMailOutcome.CLAIM_MAIL_OUTCOME_OK, "mail.notice.claimed",
            ClaimMailOutcome.CLAIM_MAIL_OUTCOME_NOT_FOUND, "mail.error.not_found",
            ClaimMailOutcome.CLAIM_MAIL_OUTCOME_ALREADY_CLAIMED, "mail.error.already_claimed",
            ClaimMailOutcome.CLAIM_MAIL_OUTCOME_INVENTORY_FULL, "mail.error.inventory_full",
            ClaimMailOutcome.CLAIM_MAIL_OUTCOME_DUPLICATE_REQUEST, "mail.error.duplicate_request");

    private static final Map<DeleteMailOutcome, String> DELETE_MESSAGE_KEYS = Map.of(
            DeleteMailOutcome.DELETE_MAIL_OUTCOME_OK, "mail.notice.deleted",
            DeleteMailOutcome.DELETE_MAIL_OUTCOME_NOT_FOUND, "mail.error.not_found",
            DeleteMailOutcome.DELETE_MAIL_OUTCOME_MUST_CLAIM_FIRST, "mail.error.must_claim_first");

    /**
     * Khoá i18n của một outcome, hoặc rỗng khi client không nhận ra giá trị đó — cùng lý do
     * {@code GuildController.messageKey} gộp về một hàm generic (/CLAUDE.md §11: không trả
     * {@code null}).
     */
    private static <T> Optional<String> messageKey(Map<T, String> table, T outcome) {
        return Optional.ofNullable(table.get(outcome));
    }

    static Optional<String> sendMessageKey(SendMailOutcome outcome) {
        return messageKey(SEND_MESSAGE_KEYS, outcome);
    }

    static Optional<String> claimMessageKey(ClaimMailOutcome outcome) {
        return messageKey(CLAIM_MESSAGE_KEYS, outcome);
    }

    static Optional<String> deleteMessageKey(DeleteMailOutcome outcome) {
        return messageKey(DELETE_MESSAGE_KEYS, outcome);
    }
}
