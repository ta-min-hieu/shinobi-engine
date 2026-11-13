package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** ADR-187 — định dạng giờ gửi của tin riêng giao muộn. */
    private static final java.time.format.DateTimeFormatter OFFLINE_WHEN =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ChatController controller = new ChatController();
    private final ChatPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();
    private final MentionWatch mentions = new MentionWatch(); // ADR-245

    /** Đệm dựng sẵn cho {@link ChatPanel#setLines} — chỉ đổi khi CÓ TIN MỚI, không phải mỗi khung. */
    private final List<String> renderedLines = new ArrayList<>();

    private final List<ChatPanel.LineKind> lineKinds = new ArrayList<>();

    /** Lệnh gạch chéo phía client (biểu cảm, mở cửa sổ Quan hệ — lô 21); mỗi bộ trả {@code true} nếu đã nhận dòng. */
    private final List<Predicate<String>> slashCommands = new ArrayList<>();

    /** Người gửi bị ẩn tin (danh sách chặn — ADR-091); chỉ là hiển thị, luật chặn ở server. */
    private LongPredicate hiddenSender = senderId -> false;

    private Predicate<String> hiddenBody = body -> false; // ADR-249

    /** ADR-268 — định dạng giờ trước mỗi dòng (giờ máy người chơi). */
    private static final java.time.format.DateTimeFormatter LINE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private boolean timestamps; // ADR-268

    /** ADR-273 — biệt danh lệnh: viết lại dòng gõ vào trước mọi xử lý khác. */
    private java.util.function.UnaryOperator<String> lineRewriter = java.util.function.UnaryOperator.identity();

    private java.util.function.Consumer<ChatController.ComposeResult.Send> outgoing = send -> {}; // ADR-284

    /** ADR-315/317 — ai cần nghe mọi tin chat đến (bong bóng chat, lịch sử thông báo). */
    private final List<java.util.function.Consumer<ChatMessageEvent>> incoming = new ArrayList<>();

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

    /** ADR-315/317 — nghe mọi tin chat đến (sau bộ lọc chặn). */
    void onIncoming(java.util.function.Consumer<ChatMessageEvent> listener) {
        incoming.add(listener);
    }

    /** ADR-284 — nghe mọi tin ô nhập vừa gửi đi (sau khi qua lệnh gạch chéo). */
    void onOutgoing(java.util.function.Consumer<ChatController.ComposeResult.Send> listener) {
        this.outgoing = listener;
    }

    /** ADR-291/292/294 — khung chat (ghim tin, thu gọn khi rảnh, cỡ chữ). */
    ChatPanel panel() {
        return panel;
    }

    /** ADR-291 — dòng NGƯỜI CHƠI gần nhất đã ghép sẵn để hiển thị (bỏ dòng hệ thống). */
    Optional<String> latestPlayerLine() {
        List<ChatController.Line> lines = controller.lines();
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (!lines.get(index).senderName().isEmpty()) {
                return Optional.of(render(lines.get(index)));
            }
        }
        return Optional.empty();
    }

    /** ADR-245 — tên nhân vật đang chơi, để tô sáng tin nhắc tên mình. Trả chính nó (gọi nối ngay sau khi dựng). */
    ChatCoordinator withOwnName(String name) {
        mentions.useOwnName(name);
        return this;
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

    /** ADR-222/229 — bật / tắt hiển thị một kênh chat. */
    void setChannelVisible(ChatChannel channel, boolean visible) {
        controller.setChannelVisible(channel, visible);
    }

    /** ADR-222/229 — kênh có đang hiện không. */
    boolean isChannelVisible(ChatChannel channel) {
        return controller.isChannelVisible(channel);
    }

    /**
     * Thêm một bộ lệnh gạch chéo phía client — dòng gõ vào bắt đầu bằng {@code /} được hỏi qua từng bộ trước khi đi
     * đường chat thường ({@code /w}, {@code /gm} vẫn tới server vì không bộ nào nhận).
     */
    void addSlashCommands(Predicate<String> handler) {
        slashCommands.add(handler);
    }

    /** ADR-268 — bật / tắt hiện giờ {@code [HH:mm]} trước mỗi dòng nhật ký (vẽ lại ngay). */
    void setTimestamps(boolean enabled) {
        this.timestamps = enabled;
        refreshLines();
    }

    /** ADR-273 — nối bộ viết lại dòng gõ (biệt danh lệnh). */
    void useLineRewriter(java.util.function.UnaryOperator<String> rewriter) {
        this.lineRewriter = rewriter;
    }

    /** ADR-249 — ẩn tin người chơi có nội dung khớp {@code filter} (lọc từ khoá). */
    void hideBodiesMatching(Predicate<String> filter) {
        this.hiddenBody = filter;
    }

    /** ADR-247 — {@code name} có phải nhân vật đang chơi không (kể cả có danh hiệu đứng trước). */
    boolean isOwnName(String name) {
        return mentions.isSelf(name);
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
            if (event.getSenderId() != 0L
                    && (hiddenSender.test(event.getSenderId()) || hiddenBody.test(event.getBody()))) { // ADR-091, 249
                return true; // người đã chặn — tin vẫn tới (kênh chung), chỉ không hiện
            }
            controller.onMessage(withTitle(markLinked(markOffline(markAutoReply(event)))));
            incoming.forEach(listener -> listener.accept(event)); // ADR-315/317
            if (mentions.mentions(event.getSenderName(), event.getBody())) { // ADR-245
                controller.appendSystem(KageI18n.formatOrKey(i18n, "mention.notice", event.getSenderName()));
            }
            refreshLines();
            return true;
        }
        if (opcode == Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE) {
            onSendResponse(SendChatMessageResponse.parseFrom(payload));
            return true;
        }
        return false;
    }

    /** ADR-188 — nhãn món khoe do luồng khoe vật phẩm cung cấp (cần catalog vật phẩm, lớp này không có). */
    private java.util.function.Function<dev.kage.protocol.LinkedItemView, String> linkLabel =
            item -> "[#" + item.getItemTemplateId() + " +" + item.getEnhanceLevel() + "]";

    /** ADR-188 — nối cách ghi nhãn món khoe. */
    void useLinkLabel(java.util.function.Function<dev.kage.protocol.LinkedItemView, String> label) {
        this.linkLabel = label;
    }

    /** ADR-188 — kênh đang chọn (cho {@code /khoe}). */
    ChatChannel currentChannel() {
        return controller.channel();
    }

    /**
     * ADR-188 — tin có món khoe: nhãn món ghi TRƯỚC nội dung, dựng từ dữ liệu SERVER đọc trong túi người gửi — chữ
     * người gửi gõ không thể giả nhãn này.
     */
    private ChatMessageEvent markLinked(ChatMessageEvent event) {
        if (!event.hasLinkedItem()) {
            return event;
        }
        return event.toBuilder()
                .setBody(linkLabel.apply(event.getLinkedItem()) + " " + event.getBody())
                .build();
    }

    /** ADR-187 — tin riêng gửi lúc mình offline: ghi lúc gửi để không đọc nhầm là vừa nhắn. */
    private ChatMessageEvent markOffline(ChatMessageEvent event) {
        if (event.getSentAtEpochSeconds() <= 0) {
            return event;
        }
        String when = OFFLINE_WHEN.format(java.time.Instant.ofEpochSecond(event.getSentAtEpochSeconds())
                .atZone(java.time.ZoneId.systemDefault()));
        return event.toBuilder()
                .setBody(KageI18n.formatOrKey(i18n, "chat.offline_prefix", when) + " " + event.getBody())
                .build();
    }

    /** ADR-178 — tin tự trả lời (người kia đang vắng) mang nhãn để không bị đọc nhầm là người thật vừa gõ. */
    private ChatMessageEvent markAutoReply(ChatMessageEvent event) {
        if (!event.getAutoReply()) {
            return event;
        }
        return event.toBuilder()
                .setBody(text("away.auto_reply_prefix") + " " + event.getBody())
                .build();
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

    private void onSend(String raw) {
        String typed = lineRewriter.apply(raw);
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
            case ChatController.ComposeResult.Send send -> {
                sender.sendChatMessage(send.channel(), send.targetName(), send.body());
                outgoing.accept(send); // ADR-284 — lịch sử tin riêng
            }
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
            lineKinds.add(
                    mentions.mentions(line.senderName(), line.body())
                            ? ChatPanel.LineKind.MENTION
                            : kindOf(line.channel()));
        }
        panel.setLines(renderedLines, lineKinds);
    }

    /**
     * Ghép một dòng hiển thị: {@code [Kênh] Tên: nội dung}. Tên rỗng = dòng của hệ thống, không có
     * người gửi nào để mà nêu tên.
     */
    private String render(ChatController.Line line) {
        String time = timestamps ? timePrefix(line.receivedAtMillis(), java.time.ZoneId.systemDefault()) : "";
        if (line.senderName().isEmpty()) {
            return time + line.body();
        }
        return time + "[" + text(channelNameKey(line.channel())) + "] " + line.senderName() + ": " + line.body();
    }

    /** ADR-268 — {@code [HH:mm] } của một dòng. Tĩnh để test. */
    static String timePrefix(long epochMillis, java.time.ZoneId zone) {
        return "["
                + LINE_TIME.format(java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)) + "] ";
    }

    /**
     * Tên kênh — bảng tra thay switch (thêm kênh BROADCAST của ADR-152 đẩy switch qua trần CyclomaticComplexity). Kênh
     * không có trong bảng (UNSPECIFIED/UNRECOGNIZED: dòng hệ thống, hoặc server mới hơn client thêm kênh mới) nhận tên
     * "Hệ thống" để không vỡ bố cục.
     */
    private static final Map<ChatChannel, String> CHANNEL_NAME_KEYS = Map.of(
            ChatChannel.CHAT_CHANNEL_TRADE, "chat.channel.trade", // ADR-222
            ChatChannel.CHAT_CHANNEL_WORLD, "chat.channel.world",
            ChatChannel.CHAT_CHANNEL_AREA, "chat.channel.area",
            ChatChannel.CHAT_CHANNEL_PRIVATE, "chat.channel.private",
            ChatChannel.CHAT_CHANNEL_GUILD, "chat.channel.guild",
            ChatChannel.CHAT_CHANNEL_PARTY, "chat.channel.party",
            ChatChannel.CHAT_CHANNEL_SWORN, "chat.channel.sworn",
            ChatChannel.CHAT_CHANNEL_BROADCAST, "chat.channel.broadcast");

    /** Màu dòng theo kênh — cùng lý do bảng tra; ngoài bảng là dòng hệ thống. */
    private static final Map<ChatChannel, ChatPanel.LineKind> LINE_KINDS = Map.of(
            ChatChannel.CHAT_CHANNEL_TRADE, ChatPanel.LineKind.WORLD, // ADR-222
            ChatChannel.CHAT_CHANNEL_AREA, ChatPanel.LineKind.AREA,
            ChatChannel.CHAT_CHANNEL_PRIVATE, ChatPanel.LineKind.PRIVATE,
            ChatChannel.CHAT_CHANNEL_WORLD, ChatPanel.LineKind.WORLD,
            ChatChannel.CHAT_CHANNEL_GUILD, ChatPanel.LineKind.WORLD,
            ChatChannel.CHAT_CHANNEL_PARTY, ChatPanel.LineKind.WORLD,
            ChatChannel.CHAT_CHANNEL_SWORN, ChatPanel.LineKind.WORLD,
            ChatChannel.CHAT_CHANNEL_BROADCAST, ChatPanel.LineKind.BROADCAST);

    private static String channelNameKey(ChatChannel channel) {
        return CHANNEL_NAME_KEYS.getOrDefault(channel, "chat.channel.system");
    }

    private static ChatPanel.LineKind kindOf(ChatChannel channel) {
        return LINE_KINDS.getOrDefault(channel, ChatPanel.LineKind.SYSTEM);
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
