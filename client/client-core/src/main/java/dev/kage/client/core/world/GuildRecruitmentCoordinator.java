package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.GuildRecruitmentView;
import dev.kage.protocol.ListGuildRecruitmentRequest;
import dev.kage.protocol.ListGuildRecruitmentResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PostGuildRecruitmentCommand;
import dev.kage.protocol.PostGuildRecruitmentOutcome;
import dev.kage.protocol.PostGuildRecruitmentResponse;
import dev.kage.protocol.SendGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng chiêu mộ bang (ADR-185) — {@code /chieumo} (hoặc {@code /recruit}) mở bảng: mỗi bang đang chiêu mộ một dòng (cấp
 * bang, số người, cấp tối thiểu, lời chiêu mộ) kèm nút "Xin vào" (gửi đúng {@code SendGuildJoinRequestCommand} như
 * cửa sổ bang). Bang chủ / phó: {@code /chieumo dang <cấp> <lời chiêu mộ>} để treo, {@code /chieumo go} để gỡ.
 */
final class GuildRecruitmentCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/chieumo", "/recruit");
    private static final String POST_PREFIX = "POST_GUILD_RECRUITMENT_OUTCOME_";
    /** Server gửi tối đa 50 — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 60;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    /** Tên bang của từng dòng (để nút "Xin vào" biết gửi đơn cho ai). */
    private final List<String> rowGuilds = new ArrayList<>();

    GuildRecruitmentCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("guild_recruit.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    static GuildRecruitmentCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new GuildRecruitmentCoordinator(
                new ReportPanel(skin, "guild_recruit.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    /** Lệnh treo chiêu mộ đọc từ đối số; rỗng nếu sai cú pháp. Tĩnh để test. */
    static java.util.Optional<PostGuildRecruitmentCommand> parsePost(String arguments) {
        String[] words = arguments.strip().split("\\s+", 3);
        String verb = words[0].toLowerCase(Locale.ROOT);
        if (words.length == 1 && ("go".equals(verb) || "gỡ".equals(verb) || "close".equals(verb))) {
            return java.util.Optional.of(
                    PostGuildRecruitmentCommand.newBuilder().setOpen(false).build());
        }
        boolean post = "dang".equals(verb) || "đăng".equals(verb) || "post".equals(verb);
        if (!post || words.length < 3 || !words[1].matches("[0-9]{1,3}")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(PostGuildRecruitmentCommand.newBuilder()
                .setOpen(true)
                .setMinLevel(Integer.parseInt(words[1]))
                .setMessage(words[2])
                .build());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        if (!COMMANDS.contains(words[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            panel.open();
            return true;
        }
        parsePost(words[1])
                .ifPresentOrElse(
                        command -> sender.sendReadRequest(Opcode.OPCODE_POST_GUILD_RECRUITMENT_COMMAND_VALUE, command),
                        () -> chat.showSystemNotice(text("guild_recruit.usage")));
        return true;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_GUILD_RECRUITMENT_REQUEST_VALUE, ListGuildRecruitmentRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rowGuilds.size()) {
            sender.sendGuildJoinRequest(rowGuilds.get(rowIndex));
        }
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
        if (opcode == Opcode.OPCODE_LIST_GUILD_RECRUITMENT_RESPONSE_VALUE) {
            show(ListGuildRecruitmentResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_POST_GUILD_RECRUITMENT_RESPONSE_VALUE) {
            chat.showSystemNotice(describe(i18n, PostGuildRecruitmentResponse.parseFrom(frame.payload())));
            return true;
        }
        if (opcode == Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE && panel.isOpen()) {
            // Đơn gửi từ bảng này: báo ngay trên bảng. KHÔNG nuốt gói — cửa sổ bang cũng đọc nó.
            SendGuildJoinRequestOutcome outcome =
                    SendGuildJoinRequestResponse.parseFrom(frame.payload()).getOutcome();
            panel.setStatus(GuildController.joinMessageKey(outcome)
                    .map(this::text)
                    .orElseGet(() -> text("guild.error.unknown")));
        }
        return false;
    }

    private void show(ListGuildRecruitmentResponse response) {
        rowGuilds.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (GuildRecruitmentView view : response.getEntriesList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            rows.add(new ReportPanel.Row(describe(i18n, view), true, "guild_recruit.action.apply"));
            rowGuilds.add(view.getGuildName());
        }
        if (rows.isEmpty()) {
            rows.add(new ReportPanel.Row(text("guild_recruit.empty"), false));
        }
        panel.setRows(rows);
    }

    /** Một dòng bảng — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GuildRecruitmentView view) {
        return KageI18n.formatOrKey(
                i18n,
                "guild_recruit.row",
                ChatController.sanitize(view.getGuildName()),
                view.getGuildLevel(),
                view.getMembers(),
                view.getMaxMembers(),
                view.getMinLevel(),
                ChatController.sanitize(view.getMessage()));
    }

    /** Dòng kết quả treo / gỡ — tĩnh để test. */
    static String describe(I18NBundle i18n, PostGuildRecruitmentResponse response) {
        PostGuildRecruitmentOutcome outcome = response.getOutcome();
        if (outcome == PostGuildRecruitmentOutcome.POST_GUILD_RECRUITMENT_OUTCOME_OK) {
            return KageI18n.textOrKey(i18n, response.getOpen() ? "guild_recruit.posted" : "guild_recruit.removed");
        }
        String suffix = outcome == PostGuildRecruitmentOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(POST_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "guild_recruit.outcome." + suffix);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowGuilds.clear();
        panel.setRows(List.of());
    }
}
