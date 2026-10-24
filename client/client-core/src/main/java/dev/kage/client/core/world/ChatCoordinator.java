package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link ChatController} (trạng thái), {@link ChatPanel} (hiển thị) và {@link WorldCommandSender}
 * (mạng) thành khung chat chạy được (2026-08-25) — cùng hình dạng {@link DialogueCoordinator}, và
 * cùng lý do tồn tại: {@code WorldScreen} đang ở ĐÚNG trần {@code CyclomaticComplexity} của PMD.
 *
 * <p>Kênh Bang hội/Party (chat.proto có, server có) cố tình KHÔNG bày ra: client chưa nối một opcode
 * nào của Guild (700-799) hay Party (1300-1399), nên người chơi không thể vào bang/party để mà nhắn
 * — bày kênh ra chỉ để nhận {@code NOT_IN_GUILD} là bày ra một nút hỏng.
 */
final class ChatCoordinator implements WindowFlow {

    private final ChatController controller = new ChatController();
    private final ChatPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link ChatPanel#setLines} — chỉ đổi khi CÓ TIN MỚI, không phải mỗi khung. */
    private final List<String> renderedLines = new ArrayList<>();

    private final List<ChatPanel.LineKind> lineKinds = new ArrayList<>();

    /** Lệnh gạch chéo phía client (biểu cảm, mở cửa sổ Quan hệ — lô 21); mỗi bộ trả {@code true} nếu đã nhận dòng. */
    private final List<Predicate<String>> slashCommands = new ArrayList<>();

    /** Người gửi bị ẩn tin (danh sách chặn — ADR-091); chỉ là hiển thị, luật chặn ở server. */
    private LongPredicate hiddenSender = senderId -> false;

    ChatCoordinator(ChatPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnSend(this::onSend);
        panel.setOnChannelToggle(this::onChannelToggle);
        refreshChannelLabel();
        // Dòng đầu tiên của nhật ký là hướng dẫn: phím T và cú pháp /w không xuất hiện ở đâu khác
        // trên màn hình (thanh gợi ý của CombatHud chỉ có phím gameplay), nên không nói ra ở đây thì
        // khung chat coi như không tồn tại với người chơi lần đầu.
        controller.appendSystem(text("chat.hint.open"));
        refreshLines();
    }

    /**
     * "Mở" với khung chat nghĩa là <b>ô nhập đang nhận chữ</b>, không phải nhật ký đang hiện: nhật
     * ký luôn hiện và không bao giờ chiếm bàn phím. Khi câu này đúng, mọi phím gameplay phải bị chặn
     * — xem {@link PanelHub#isAnyOpen()} và {@link ChatPanel#isComposing()}.
     */
    @Override
    public boolean isOpen() {
        return panel.isComposing();
    }

    /** Đóng ô nhập (mở hội thoại NPC, rời màn hình) — nhật ký vẫn giữ nguyên. */
    @Override
    public void close() {
        panel.closeComposer();
    }

    /**
     * Ghi một dòng hệ thống vào nhật ký chat — lối vào cho các luồng KHÁC muốn nói một câu bằng chữ
     * ({@code PartyCoordinator} dùng để báo lý do lệnh nhóm thất bại).
     *
     * @param text chữ ĐÃ dịch (người gọi có bundle của mình, xem {@code PartyCoordinator})
     */
    void showSystemNotice(String text) {
        controller.appendSystem(text);
        refreshLines();
    }

    /**
     * Thêm một bộ lệnh gạch chéo phía client — dòng gõ vào bắt đầu bằng {@code /} được hỏi qua từng bộ trước khi đi
     * đường chat thường ({@code /w}, {@code /gm} vẫn tới server vì không bộ nào nhận).
     */
    void addSlashCommands(Predicate<String> handler) {
        slashCommands.add(handler);
    }

    /** Ẩn tin của những người gửi khớp {@code filter} (ADR-091 — người mình đã chặn). */
    void hideSendersMatching(LongPredicate filter) {
        this.hiddenSender = filter;
    }

    /** Người chơi vừa vào/rời nhóm — kênh Nhóm xuất hiện/biến mất, xem {@code ChatController}. */
    void setPartyAvailable(boolean available) {
        controller.setPartyAvailable(available);
        refreshChannelLabel();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng chat.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
            ChatMessageEvent event = ChatMessageEvent.parseFrom(payload);
            if (event.getSenderId() != 0L && hiddenSender.test(event.getSenderId())) {
                return true; // người đã chặn — tin vẫn tới (kênh chung), chỉ không hiện
            }
            controller.onMessage(withTitle(event));
            refreshLines();
            return true;
        }
        if (opcode == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE) {
            onSendResponse(SendChatMessageResponse.parseFrom(payload));
            return true;
        }
        return false;
    }

    /**
     * ADR-062 D3 — người gửi đang đeo danh hiệu thì tên hiện thành {@code [Danh hiệu] Tên}. Danh hiệu là
     * khoá i18n (server không gửi chữ); khoá lạ (client cũ hơn server) thì bỏ qua, không hiện mã thô.
     */
    private ChatMessageEvent withTitle(ChatMessageEvent event) {
        String titleKey = event.getSenderTitleKey();
        String title = titleKey.isEmpty() ? "" : text(titleKey);
        if (title.isEmpty() || title.equals(titleKey)) {
            return event; // textOrKey trả lại chính khoá khi bundle thiếu nó
        }
        return event.toBuilder()
                .setSenderName("[" + title + "] " + event.getSenderName())
                .build();
    }

    /**
     * Kết quả lệnh gửi. {@code OK} không có gì để nói — tin nhắn tự hiện ra qua
     * {@link ChatMessageEvent} mà server dội về, KỂ CẢ cho chính người gửi (chat.proto: Thế giới gửi
     * toàn server, Khu vực gửi cho mọi quan sát viên AoI "gồm cả người gửi").
     *
     * <p>Ngoại lệ là kênh RIÊNG: server chỉ gửi cho đúng một người nhận, nên người gửi KHÔNG bao giờ
     * thấy tin của chính mình. Chỗ này cố tình bỏ trống thay vì tự bịa dòng "đã gửi tới X" — client
     * không có {@code target_name} lúc nhận response (chat.proto: {@code SendChatMessageResponse}
     * chỉ mang {@code outcome}), và đoán bừa là đúng loại lỗi mà giao thức bày ra để mắc phải.
     */
    private void onSendResponse(SendChatMessageResponse response) {
        ChatController.outcomeMessageKey(response.getOutcome()).ifPresent(messageKey -> {
            controller.appendSystem(text(messageKey));
            refreshLines();
        });
    }

    private void onSend(String typed) {
        String line = typed.strip();
        if (line.startsWith("/")) {
            for (Predicate<String> handler : slashCommands) {
                if (handler.test(line)) {
                    return;
                }
            }
        }
        ChatController.ComposeResult result = controller.compose(typed);
        switch (result) {
            case ChatController.ComposeResult.Send send ->
                sender.sendChatMessage(send.channel(), send.targetName(), send.body());
            case ChatController.ComposeResult.Reject reject -> {
                controller.appendSystem(text(reject.messageKey()));
                refreshLines();
            }
            case ChatController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — đã đóng rồi, không có gì để làm.
            }
        }
    }

    private void onChannelToggle() {
        controller.toggleChannel();
        refreshChannelLabel();
    }

    private void refreshChannelLabel() {
        panel.setChannelLabel(text(channelNameKey(controller.channel())));
    }

    private void refreshLines() {
        renderedLines.clear();
        lineKinds.clear();
        for (ChatController.Line line : controller.lines()) {
            renderedLines.add(render(line));
            lineKinds.add(kindOf(line.channel()));
        }
        panel.setLines(renderedLines, lineKinds);
    }

    /**
     * Ghép một dòng hiển thị: {@code [Kênh] Tên: nội dung}. Tên rỗng = dòng của hệ thống, không có
     * người gửi nào để mà nêu tên.
     */
    private String render(ChatController.Line line) {
        if (line.senderName().isEmpty()) {
            return line.body();
        }
        return "[" + text(channelNameKey(line.channel())) + "] " + line.senderName() + ": " + line.body();
    }

    private static String channelNameKey(ChatChannel channel) {
        return switch (channel) {
            case CHAT_CHANNEL_WORLD -> "chat.channel.world";
            case CHAT_CHANNEL_AREA -> "chat.channel.area";
            case CHAT_CHANNEL_PRIVATE -> "chat.channel.private";
            case CHAT_CHANNEL_GUILD -> "chat.channel.guild";
            case CHAT_CHANNEL_PARTY -> "chat.channel.party";
            // Không gửi được nhưng NHẬN được: server mới hơn client thêm kênh mới, hoặc dòng hệ
            // thống (không thuộc kênh nào). Cả hai đều cần một cái tên để không vỡ bố cục.
            case CHAT_CHANNEL_UNSPECIFIED, UNRECOGNIZED -> "chat.channel.system";
        };
    }

    private static ChatPanel.LineKind kindOf(ChatChannel channel) {
        return switch (channel) {
            case CHAT_CHANNEL_AREA -> ChatPanel.LineKind.AREA;
            case CHAT_CHANNEL_PRIVATE -> ChatPanel.LineKind.PRIVATE;
            case CHAT_CHANNEL_WORLD, CHAT_CHANNEL_GUILD, CHAT_CHANNEL_PARTY -> ChatPanel.LineKind.WORLD;
            case CHAT_CHANNEL_UNSPECIFIED, UNRECOGNIZED -> ChatPanel.LineKind.SYSTEM;
        };
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    /** Nhật ký hiện có, để mang sang màn world mới khi tự đăng nhập lại — xem {@link ChatHistory}. */
    ChatHistory history() {
        return new ChatHistory(controller.lines());
    }

    /** Nhận nhật ký từ màn world cũ — thay dòng gợi ý mặc định của màn mới. Rỗng thì không đổi gì. */
    void restoreHistory(ChatHistory history) {
        if (history.lines().isEmpty()) {
            return;
        }
        controller.restoreLines(history.lines());
        refreshLines();
    }

    /** Rời màn hình/mất kết nối — nhật ký của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.closeComposer();
        refreshChannelLabel();
        controller.appendSystem(text("chat.hint.open"));
        refreshLines();
    }
}
