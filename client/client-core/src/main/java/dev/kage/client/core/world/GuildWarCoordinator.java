package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.DeclareGuildWarCommand;
import dev.kage.protocol.DeclareGuildWarOutcome;
import dev.kage.protocol.DeclareGuildWarResponse;
import dev.kage.protocol.GetGuildWarStatusRequest;
import dev.kage.protocol.GetGuildWarStatusResponse;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildWarView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bang chiến (ADR-102 D6) — {@link ReportPanel} mở từ nút trong cửa sổ Bang (G): mọi trận chưa chốt trên server, ô tên
 * bang và nút Tuyên chiến. Server quyết hết (ai là bang chủ, đủ vàng không, ai được đánh ai); client chỉ hiển thị.
 *
 * <p>Còn giữ khung giờ các trận CỦA BANG MÌNH để {@link #isMyWarActive} cho phép click nhắm một người chơi khác ở world
 * chính ({@link DuelTargeting}). Hỏi lại tình hình khi: mở cửa sổ, về world chính, server báo tin bang chiến lên kênh
 * hệ thống, hoặc nhận thông tin bang của mình.
 */
final class GuildWarCoordinator implements WindowFlow {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    // Trận chưa bắt đầu: server chỉ báo còn bao lâu tới lúc bắt đầu; độ dài trận lấy theo ADR-102 D1 (60 phút) — trả
    // lời kế tiếp lúc trận đã mở sẽ sửa lại cho đúng giờ kết thúc thật.
    private static final long ROUGH_DURATION_MILLIS = 60L * SECONDS_PER_MINUTE * MILLIS_PER_SECOND;

    // Tin hệ thống của GuildWarScheduler/GuildWarCommands phía server — chỉ để biết khi nào nên hỏi lại.
    private static final List<String> WAR_NEWS_MARKERS = List.of("Bang chiến", "tuyên chiến", "Guild war");
    private static final Map<DeclareGuildWarOutcome, String> OUTCOME_KEYS = new EnumMap<>(DeclareGuildWarOutcome.class);

    static {
        OUTCOME_KEYS.put(DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_OK, "guildwar.outcome.ok");
        OUTCOME_KEYS.put(
                DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_NOT_IN_GUILD, "guildwar.outcome.not_in_guild");
        OUTCOME_KEYS.put(DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_NOT_LEADER, "guildwar.outcome.not_leader");
        OUTCOME_KEYS.put(
                DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_UNKNOWN_GUILD, "guildwar.outcome.unknown_guild");
        OUTCOME_KEYS.put(DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_SAME_GUILD, "guildwar.outcome.same_guild");
        OUTCOME_KEYS.put(
                DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_ALREADY_AT_WAR, "guildwar.outcome.already_at_war");
        OUTCOME_KEYS.put(DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_COOLDOWN, "guildwar.outcome.cooldown");
        OUTCOME_KEYS.put(
                DeclareGuildWarOutcome.DECLARE_GUILD_WAR_OUTCOME_INSUFFICIENT_GOLD,
                "guildwar.outcome.insufficient_gold");
    }

    /** Khung giờ một trận của bang mình, theo đồng hồ máy khách lúc nhận trả lời. */
    private record Window(long startMillis, long endMillis) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final LongSupplier nowMillis;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Window> myWars = new ArrayList<>();
    private long declareCost;

    GuildWarCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator chat) {
        this(panel, sender, chat, System::currentTimeMillis);
    }

    GuildWarCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator chat, LongSupplier nowMillis) {
        this.panel = panel;
        this.sender = sender;
        this.chat = chat;
        this.nowMillis = nowMillis;
        panel.enableInput("guildwar.input.guild");
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(index -> declare());
    }

    /** Nút "Bang chiến" trong cửa sổ Bang — hỏi tình hình rồi mở cửa sổ khi có trả lời. */
    void open() {
        panel.show();
        request();
    }

    /** Bang mình đang trong giờ chiến — click một người chơi khác ở world chính là nhắm họ. */
    boolean isMyWarActive() {
        long now = nowMillis.getAsLong();
        for (Window window : myWars) {
            if (now >= window.startMillis() && now < window.endMillis()) {
                return true;
            }
        }
        return false;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_GUILD_WAR_STATUS_REQUEST_VALUE, GetGuildWarStatusRequest.getDefaultInstance());
    }

    private void declare() {
        String name = panel.inputText().strip();
        if (name.isEmpty()) {
            panel.setStatus(text("guildwar.error.name_required"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_DECLARE_GUILD_WAR_COMMAND_VALUE,
                DeclareGuildWarCommand.newBuilder().setTargetGuildName(name).build());
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_GUILD_WAR_STATUS_RESPONSE_VALUE) {
            show(GetGuildWarStatusResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_DECLARE_GUILD_WAR_RESPONSE_VALUE) {
            DeclareGuildWarResponse response = DeclareGuildWarResponse.parseFrom(frame.payload());
            String line = KageI18n.formatOrKey(
                    i18n,
                    OUTCOME_KEYS.getOrDefault(response.getOutcome(), "guildwar.outcome.unknown"),
                    response.getCostGold());
            panel.setStatus(line);
            chat.showSystemNotice(line);
            request();
            return true;
        }
        if (signalsWarChange(frame)) {
            request(); // không nhận gói — luồng khác vẫn xử lý nó (PanelHub hỏi mọi luồng)
        }
        return false;
    }

    /**
     * Gói báo có thể đã đổi tình hình bang chiến. PHẢI đọc được gói trước (ném nếu hỏng): gói rác không được làm client
     * gửi lệnh nào ({@code InboundPacketFuzzTest}).
     */
    private static boolean signalsWarChange(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE) {
            return GetMyGuildResponse.parseFrom(frame.payload()).isInitialized();
        }
        if (opcode == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE) {
            return WorldChangedEvent.parseFrom(frame.payload()).isInitialized();
        }
        return isWarNews(frame);
    }

    private static boolean isWarNews(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
            return false;
        }
        ChatMessageEvent event = ChatMessageEvent.parseFrom(frame.payload());
        if (!event.getSenderName().isEmpty()) {
            return false; // người chơi gõ chữ "Bang chiến" không được làm client hỏi dồn
        }
        for (String marker : WAR_NEWS_MARKERS) {
            if (event.getBody().contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private void show(GetGuildWarStatusResponse status) {
        declareCost = status.getDeclareCostGold();
        long now = nowMillis.getAsLong();
        myWars.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GuildWarView war : status.getWarsList()) {
            long change = now + war.getSecondsUntilChange() * MILLIS_PER_SECOND;
            if (war.getMine()) {
                myWars.add(
                        war.getActive() ? new Window(now, change) : new Window(change, change + ROUGH_DURATION_MILLIS));
            }
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            war.getActive() ? "guildwar.row.active" : "guildwar.row.pending",
                            war.getAttackerGuildName(),
                            war.getAttackerKills(),
                            war.getDefenderKills(),
                            war.getDefenderGuildName(),
                            Math.max(0L, war.getSecondsUntilChange()) / SECONDS_PER_MINUTE),
                    war.getMine(),
                    ""));
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(text("guildwar.none"), false, ""));
        }
        rows.add(new ReportPanel.Row(
                KageI18n.formatOrKey(i18n, "guildwar.row.declare", declareCost), false, "guildwar.action.declare"));
        panel.setStatus(text("guildwar.hint"));
        panel.setRows(rows);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
        myWars.clear();
    }
}
