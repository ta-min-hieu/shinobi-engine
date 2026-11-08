package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ChatChannel;

/**
 * Lọc kênh chat (ADR-229) — {@code /an <kênh>} / {@code /hien <kênh>} (hoặc {@code /hide}, {@code /show}) ẩn / hiện tin
 * của một kênh trong khung chat của mình; {@code /kenh} ({@code /channels}) liệt kê trạng thái. Lưu theo máy
 * (Preferences libGDX, cùng tệp với ngôn ngữ / phím tắt), nạp lại lúc vào world. Dòng hệ thống không bao giờ bị ẩn.
 */
final class ChatFilterCoordinator implements WindowFlow {

    private static final String PREF_KEY = "chat.hiddenChannels";

    /** Tên gõ được (không dấu, vi + en) → kênh. */
    static final Map<String, ChatChannel> NAMES = Map.ofEntries(
            Map.entry("thegioi", ChatChannel.CHAT_CHANNEL_WORLD),
            Map.entry("world", ChatChannel.CHAT_CHANNEL_WORLD),
            Map.entry("khuvuc", ChatChannel.CHAT_CHANNEL_AREA),
            Map.entry("area", ChatChannel.CHAT_CHANNEL_AREA),
            Map.entry("bang", ChatChannel.CHAT_CHANNEL_GUILD),
            Map.entry("guild", ChatChannel.CHAT_CHANNEL_GUILD),
            Map.entry("nhom", ChatChannel.CHAT_CHANNEL_PARTY),
            Map.entry("party", ChatChannel.CHAT_CHANNEL_PARTY),
            Map.entry("ketnghia", ChatChannel.CHAT_CHANNEL_SWORN),
            Map.entry("sworn", ChatChannel.CHAT_CHANNEL_SWORN),
            Map.entry("gd", ChatChannel.CHAT_CHANNEL_TRADE),
            Map.entry("trade", ChatChannel.CHAT_CHANNEL_TRADE),
            Map.entry("loa", ChatChannel.CHAT_CHANNEL_BROADCAST),
            Map.entry("broadcast", ChatChannel.CHAT_CHANNEL_BROADCAST));

    /** Thứ tự liệt kê. */
    private static final List<ChatChannel> LISTED = List.of(
            ChatChannel.CHAT_CHANNEL_WORLD,
            ChatChannel.CHAT_CHANNEL_TRADE,
            ChatChannel.CHAT_CHANNEL_AREA,
            ChatChannel.CHAT_CHANNEL_GUILD,
            ChatChannel.CHAT_CHANNEL_PARTY,
            ChatChannel.CHAT_CHANNEL_SWORN,
            ChatChannel.CHAT_CHANNEL_BROADCAST);

    private static final Map<ChatChannel, String> CHANNEL_KEYS = Map.of(
            ChatChannel.CHAT_CHANNEL_WORLD, "chat.channel.world",
            ChatChannel.CHAT_CHANNEL_TRADE, "chat.channel.trade",
            ChatChannel.CHAT_CHANNEL_AREA, "chat.channel.area",
            ChatChannel.CHAT_CHANNEL_GUILD, "chat.channel.guild",
            ChatChannel.CHAT_CHANNEL_PARTY, "chat.channel.party",
            ChatChannel.CHAT_CHANNEL_SWORN, "chat.channel.sworn",
            ChatChannel.CHAT_CHANNEL_BROADCAST, "chat.channel.broadcast");

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;

    ChatFilterCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        preferences.ifPresent(prefs -> parse(java.util.Objects.requireNonNullElse(prefs.getString(PREF_KEY, ""), ""))
                .forEach(channel -> chat.setChannelVisible(channel, false)));
        chat.addSlashCommands(this::onSlash);
    }

    /** Danh sách kênh từ chuỗi lưu ("WORLD,TRADE"); tên lạ bị bỏ qua. Tĩnh để test. */
    static List<ChatChannel> parse(String saved) {
        List<ChatChannel> channels = new ArrayList<>();
        for (String part : saved.split(",", -1)) {
            String name = part.strip();
            for (ChatChannel channel : LISTED) {
                if (channel.name().equals("CHAT_CHANNEL_" + name)) {
                    channels.add(channel);
                }
            }
        }
        return channels;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        boolean hide = "/an".equals(command) || "/hide".equals(command);
        boolean show = "/hien".equals(command) || "/show".equals(command);
        if (!hide && !show && !"/kenh".equals(command) && !"/channels".equals(command)) {
            return false;
        }
        if (hide || show) {
            ChatChannel channel = NAMES.get(words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "");
            if (channel == null) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "chat_filter.usage"));
                return true;
            }
            chat.setChannelVisible(channel, show);
            save();
        }
        chat.showSystemNotice(status());
        return true;
    }

    private String status() {
        List<String> parts = new ArrayList<>();
        for (ChatChannel channel : LISTED) {
            parts.add(KageI18n.formatOrKey(
                    i18n,
                    chat.isChannelVisible(channel) ? "chat_filter.shown" : "chat_filter.hidden",
                    KageI18n.textOrKey(i18n, CHANNEL_KEYS.get(channel))));
        }
        return KageI18n.formatOrKey(i18n, "chat_filter.status", String.join(", ", parts));
    }

    private void save() {
        preferences.ifPresent(prefs -> {
            List<String> hidden = new ArrayList<>();
            for (ChatChannel channel : LISTED) {
                if (!chat.isChannelVisible(channel)) {
                    hidden.add(channel.name().substring("CHAT_CHANNEL_".length()));
                }
            }
            prefs.putString(PREF_KEY, String.join(",", hidden));
            prefs.flush();
        });
    }

    @Override
    public boolean dispatch(dev.kage.protocol.wire.PacketCodec.Frame frame) {
        return false;
    }
}
