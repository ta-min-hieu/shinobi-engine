package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.MailPanel;
import dev.kage.client.core.ui.hud.ScrollingList;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ClaimMailResponse;
import dev.kage.protocol.DeleteMailResponse;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link MailController} (trạng thái), {@link MailPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành hòm thư chạy được (2026-08-27) — cùng hình dạng
 * {@link GuildCoordinator}, và cùng lý do tồn tại: {@code WorldScreen} đang ở ĐÚNG trần
 * {@code CyclomaticComplexity} của PMD.
 *
 * <p>Toàn bộ dải opcode 900-908 chưa từng được client dùng trước hôm nay, dù {@code feature-mail}
 * phía server đã xong từ Phase 6.
 *
 * <p><b>Luật một dòng của lớp này: hỏi lại server sau MỌI lệnh đổi trạng thái</b>
 * ({@link #settle}). mail.proto chỉ có ĐÚNG MỘT event đẩy — {@code NewMailEvent}, và nó chỉ tới
 * người NHẬN, chỉ khi người đó đang online, và chỉ mang một cái {@code mail_id} trần. Không có gói
 * nào báo "thư vừa bị nhận/xoá", nên "lệnh vừa trả OK" KHÔNG được dùng để suy ra trạng thái mới:
 * trạng thái mới chỉ đến từ {@code ListMailResponse}, thứ server nói ra chứ không phải client tự
 * tính (Điều 15).
 *
 * <p>Hệ quả còn lại mà cách này KHÔNG bù được: thư đến trong lúc người chơi đang <b>offline</b> thì
 * không có event nào cả, nên dấu "có thư mới" chỉ đúng cho những lá tới trong phiên chơi này. Đóng
 * lỗ đó thật sự cần một trường "số thư chưa đọc" trong gói đăng nhập, tức phải sửa .proto — nằm
 * ngoài quyền của client.
 */
final class MailCoordinator implements WindowFlow {

    private final MailController controller = new MailController();
    private final MailPanel panel;
    private final WorldCommandSender sender;

    /**
     * Kênh nói ra khi cửa sổ ĐANG ĐÓNG. Thư tới bất cứ lúc nào, và dòng trạng thái trong cửa sổ chỉ
     * đọc được khi cửa sổ mở — chữ nổi giữa màn hình là kênh duy nhất người chơi thấy trong lúc
     * đang đánh nhau. Cùng lý do {@link FriendCoordinator} mượn nó.
     */
    private final CombatFeedbackPresenter feedback;

    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link MailPanel#setMails} — chỉ đổi khi server vừa trả lời, không mỗi khung. */
    private final List<MailPanel.MailRow> mailRows = new ArrayList<>();

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím M — cổng phím CHUNG của mọi cửa sổ, xem
     *     {@link MailPanel#setCanOpen}. Nhận qua tham số chứ không tự hỏi coordinator khác: các
     *     luồng này ngang hàng nhau, không cái nào sở hữu cái nào.
     */
    MailCoordinator(
            MailPanel panel, WorldCommandSender sender, CombatFeedbackPresenter feedback, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.feedback = feedback;
        panel.setCanOpen(canOpen);
        panel.setOnRefresh(this::onOpened);
        panel.setOnRead(this::onRead);
        panel.setOnClaim(this::onClaim);
        panel.setOnDelete(sender::deleteMail);
        panel.setOnSend(this::onSend);
        panel.setStatus(text("mail.hint.open"));
        refreshView();
    }

    /**
     * Cửa sổ đang mở hay không — {@code WorldScreen} phải chặn MỌI phím gameplay khi câu này đúng,
     * xem {@link MailPanel#isOpen()}.
     */
    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng cửa sổ (mở hội thoại NPC, rời màn hình) — hòm thư đã tải vẫn giữ nguyên. */
    @Override
    public void close() {
        panel.close();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng thư.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE) {
            controller.onMailList(ListMailResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_NEW_MAIL_EVENT_VALUE) {
            onNewMail();
            return true;
        }
        return dispatchCommandResponse(opcode, payload);
    }

    /**
     * Ba response KẾT QUẢ LỆNH. Tách khỏi {@link #dispatch} THUẦN để hạ
     * {@code CyclomaticComplexity} dưới ngưỡng PMD, không phải đổi hành vi — cùng lối đi
     * {@link GuildCoordinator#dispatchCommandResponse}.
     */
    private boolean dispatchCommandResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE) {
            onSendSettled(SendMailResponse.parseFrom(payload).getOutcome());
        } else if (opcode == Opcode.OPCODE_CLAIM_MAIL_RESPONSE_VALUE) {
            // TRƯỚC settle: lệnh đã có kết luận thì khoá ADR-025 hết hiệu lực, lần bấm sau là một ý
            // định MỚI chứ không phải lần thử lại của lệnh này.
            controller.onClaimSettled();
            settle(MailController.claimMessageKey(
                    ClaimMailResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_DELETE_MAIL_RESPONSE_VALUE) {
            settle(MailController.deleteMessageKey(
                    DeleteMailResponse.parseFrom(payload).getOutcome()));
        } else {
            return false;
        }
        return true;
    }

    /**
     * Gửi thư xong. Dọn khung soạn CHỈ khi server nói OK — dọn cả khi hỏng là bắt người chơi gõ lại
     * từ đầu một lá thư mà lỗi duy nhất là gõ nhầm một chữ trong tên người nhận.
     */
    private void onSendSettled(SendMailOutcome outcome) {
        if (outcome == SendMailOutcome.SEND_MAIL_OUTCOME_OK) {
            panel.clearComposer();
        }
        settle(MailController.sendMessageKey(outcome));
    }

    /**
     * Nói ra kết quả một lệnh, rồi hỏi lại server.
     *
     * <p>Hỏi lại KỂ CẢ khi lệnh hỏng, cùng lý do {@link GuildCoordinator#settle}: một lệnh hỏng
     * thường có nghĩa là client đang hiểu sai trạng thái (VD bấm Xoá mà nhận {@code NOT_FOUND} —
     * lá thư đó đã bị xoá ở một phiên khác), và đó chính là lúc cần dữ liệu mới nhất nhất.
     *
     * @param messageKey rỗng khi client không nhận ra outcome (server MỚI HƠN client) — nói thẳng ra
     *     là không hiểu thay vì im lặng hoặc đoán bừa.
     */
    private void settle(Optional<String> messageKey) {
        panel.setStatus(text(messageKey.orElse("mail.error.unknown")));
        sender.listMail();
    }

    /**
     * Thư mới tới trong lúc đang chơi.
     *
     * <p>Hiện chữ nổi giữa màn hình VÀ bật dấu trên cửa sổ: chữ nổi tan sau hơn một giây, nên nếu
     * chỉ có nó thì người chơi đang mải đánh nhau sẽ không bao giờ biết mình có thư.
     *
     * <p>Chỉ hỏi lại danh sách khi cửa sổ ĐANG MỞ. {@code NewMailEvent} do NGƯỜI KHÁC gây ra với
     * nhịp do họ quyết — hỏi lại vô điều kiện là để người lạ quyết lưu lượng mạng của máy này, và
     * danh sách vừa tải cũng chẳng ai nhìn khi cửa sổ đóng.
     */
    private void onNewMail() {
        controller.onNewMail();
        panel.setNewMailBadge(true);
        feedback.showSelfText(text("mail.notice.new"), FloatingCombatText.LEVEL_UP);
        if (panel.isOpen()) {
            sender.listMail();
        }
    }

    /** Người chơi vừa mở cửa sổ (hoặc bấm Làm mới) — dấu "có thư mới" hết nhiệm vụ, và hỏi lại server. */
    private void onOpened() {
        controller.clearNewMailFlag();
        panel.setNewMailBadge(false);
        sender.listMail();
    }

    private void onRead(long mailId) {
        controller.select(mailId);
        refreshSelected();
    }

    /**
     * Bấm Nhận. Khoá idempotency sinh ở {@link MailController#beginClaim} chứ không ở đây: bấm lại
     * trên cùng một lá thư khi lệnh trước chưa có kết luận là "tự thử lại cùng một lần nhận"
     * (ADR-025) và phải giữ NGUYÊN khoá cũ.
     */
    private void onClaim(long mailId) {
        sender.claimMail(mailId, controller.beginClaim(mailId));
    }

    private void onSend(MailPanel.Composed composed) {
        MailController.ComposeResult result =
                controller.compose(composed.recipient(), composed.subject(), composed.body(), composed.gold());
        switch (result) {
            case MailController.ComposeResult.Send valid -> sender.sendMail(valid.draft());
            case MailController.ComposeResult.Reject reject -> panel.setStatus(text(reject.messageKey()));
            case MailController.ComposeResult.Nothing ignored -> {
                // Chưa gõ gì cả — không gửi, không mắng.
            }
        }
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống panel. Gọi sau MỖI response tra cứu, không phải mỗi khung. */
    private void refreshView() {
        List<MailController.Mail> all = controller.mails();
        int shown = Math.min(all.size(), ScrollingList.MAX_BUILT_ROWS);
        mailRows.clear();
        for (int index = 0; index < shown; index++) {
            MailController.Mail mail = all.get(index);
            mailRows.add(new MailPanel.MailRow(mail.mailId(), rowText(mail), isClaimable(mail)));
        }
        panel.setMails(mailRows, listNotice(all.size(), all.size() - shown));
        panel.setNewMailBadge(controller.hasNewMail());
        refreshSelected();
    }

    private void refreshSelected() {
        controller
                .selected()
                .ifPresentOrElse(
                        mail -> panel.showMail(header(mail), mail.body()),
                        () -> panel.showNoMail(text("mail.body.none")));
    }

    /**
     * Có gì để nhận không. Thư đã {@code claimed} thì không, và thư không vàng không đính kèm cũng
     * không — server trả {@code ALREADY_CLAIMED} cho cái đầu, tức bày nút ra là bày một nút chắc
     * chắn hỏng (cùng lập luận {@code GuildCoordinator} dùng để giấu nút Đuổi ở dòng của chính mình).
     */
    private static boolean isClaimable(MailController.Mail mail) {
        return !mail.claimed() && (mail.goldAmount() > 0L || !mail.attachments().isEmpty());
    }

    /** Một dòng danh sách: {@code Người gửi — Tiêu đề · phần thưởng · trạng thái}. */
    private String rowText(MailController.Mail mail) {
        return senderText(mail) + " — " + mail.subject() + " · " + rewardText(mail) + " · "
                + text(mail.claimed() ? "mail.label.claimed" : "mail.label.unclaimed");
    }

    /** Đầu trang phần đọc thư: người gửi + tiêu đề + phần thưởng, không kèm trạng thái (đã có ở dòng). */
    private String header(MailController.Mail mail) {
        return senderText(mail) + " — " + mail.subject() + " · " + rewardText(mail);
    }

    /**
     * Tên người gửi — hoặc đúng hơn, thứ DUY NHẤT client biết về người gửi.
     *
     * <p>{@code has_sender = false} là thư hệ thống và có khoá riêng. Còn lại thì mail.proto chỉ cho
     * một {@code character_id} trần, không cho tên, và client không có gói nào tra id ra tên — nên
     * hiện thẳng con số qua {@code mail.sender.raw_id} thay vì bịa (xem javadoc {@link MailPanel}).
     */
    private String senderText(MailController.Mail mail) {
        return mail.hasSender() ? format("mail.sender.raw_id", mail.senderId()) : text("mail.sender.system");
    }

    /**
     * Phần thưởng đính kèm, ghép thành một chuỗi.
     *
     * <p>Vật phẩm hiện dạng {@code Vật phẩm #12 x3}: client chưa có catalog để tra tên — Khoảng
     * trống 2 của ADR-035, xem javadoc {@link MailPanel}. Không có gì thì nói rõ là không có gì, chứ
     * không để một chuỗi rỗng làm dòng thư trông như bị cắt cụt.
     */
    private String rewardText(MailController.Mail mail) {
        StringBuilder builder = new StringBuilder();
        if (mail.goldAmount() > 0L) {
            builder.append(format("mail.label.gold", mail.goldAmount()));
        }
        for (MailController.Attachment attachment : mail.attachments()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(formatAttachment(attachment));
        }
        return builder.length() == 0 ? text("mail.label.no_reward") : builder.toString();
    }

    private String formatAttachment(MailController.Attachment attachment) {
        return KageI18n.formatOrKey(
                i18n, "mail.attachment.raw_item", attachment.itemTemplateId(), attachment.quantity());
    }

    /**
     * Câu nói thêm dưới danh sách: "hòm thư trống", hoặc "còn N lá nữa không hiện", hoặc không nói
     * gì. Nói ra thay vì im lặng cắt: một danh sách bị cắt âm thầm trông y hệt một danh sách ngắn
     * thật — cùng lý do {@link GuildCoordinator#overflowNotice}.
     */
    private String listNotice(int total, int hidden) {
        if (total == 0) {
            return text("mail.empty");
        }
        return hidden <= 0 ? "" : format("mail.list.truncated", hidden);
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object argument) {
        return KageI18n.formatOrKey(i18n, key, argument);
    }

    /** Rời màn hình/mất kết nối — hòm thư của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.clearComposer();
        panel.setStatus(text("mail.hint.open"));
        refreshView();
    }
}
