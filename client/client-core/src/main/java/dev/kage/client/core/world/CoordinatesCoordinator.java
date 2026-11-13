package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Toạ độ hiện tại (ADR-270) — {@code /toado} (hoặc {@code /coords}) in hoành độ đang đứng (đơn vị mét, làm tròn) và
 * trạng thái vùng (thường / an toàn / PvP — ADR-141); {@code /toado gui} (hoặc {@code send}) gửi dòng đó vào kênh chat
 * đang chọn như một tin thường (server vẫn kiểm duyệt, giới hạn tốc độ như mọi tin).
 */
final class CoordinatesCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Optional<AutoWalkTarget> position = Optional.empty();
    private Supplier<ZoneMapCoordinator.ZoneState> zone = () -> ZoneMapCoordinator.ZoneState.OPEN;

    CoordinatesCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối nguồn hoành độ (đích tự đi ADR-206 theo dõi vị trí mỗi khung) và trạng thái vùng. */
    void usePosition(AutoWalkTarget target, Supplier<ZoneMapCoordinator.ZoneState> zoneState) {
        this.position = Optional.of(target);
        this.zone = zoneState;
    }

    /** Hoành độ làm tròn về mét. Tĩnh để test. */
    static long roundedX(float x) {
        return Math.round(x);
    }

    /** Khoá i18n tên trạng thái vùng. Tĩnh để test. */
    static String zoneKey(ZoneMapCoordinator.ZoneState state) {
        return switch (state) {
            case PVP -> "coords.zone.pvp";
            case SAFE -> "coords.zone.safe";
            case OPEN -> "coords.zone.open";
        };
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        boolean share = "/chiasevitri".equals(command) || "/sharepos".equals(command); // ADR-310
        if (!"/toado".equals(command) && !"/coords".equals(command) && !share) {
            return false;
        }
        if (position.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "coords.unknown"));
            return true;
        }
        String text = KageI18n.formatOrKey(
                i18n,
                "coords.line",
                roundedX(position.get().lastKnownX()),
                KageI18n.textOrKey(i18n, zoneKey(zone.get())));
        String argument = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "";
        if (share) {
            sender.sendChatMessage(dev.kage.protocol.ChatChannel.CHAT_CHANNEL_PARTY, "", text);
        } else if ("gui".equals(argument) || "gửi".equals(argument) || "send".equals(argument)) {
            sender.sendChatMessage(chat.currentChannel(), "", text);
        } else {
            chat.showSystemNotice(text);
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
