package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.FactionCount;
import dev.kage.protocol.GetOnlineCountRequest;
import dev.kage.protocol.GetOnlineCountResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Đếm người online (ADR-281) — {@code /online}: tổng, theo linh căn, theo cảnh giới. Không có tên ai. */
final class OnlineCountCoordinator implements WindowFlow {

    /** Khoá tên cảnh giới theo chỉ số (cùng thứ tự {@code CultivationRealm}). */
    static final List<String> REALM_KEYS = List.of(
            "realm.luyen_khi", "realm.truc_co", "realm.tu_phu", "realm.kim_dan", "realm.nguyen_anh", "realm.hoa_than");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    OnlineCountCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Khoá i18n tên linh căn ({@code NONE} ⇒ chưa thức tỉnh). Tĩnh để test. */
    static String factionKey(String faction) {
        return "NONE".equals(faction) ? "faction.unspecified" : "faction." + faction.toLowerCase(Locale.ROOT);
    }

    private boolean onSlash(String line) {
        if (!"/online".equals(line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_ONLINE_COUNT_REQUEST_VALUE, GetOnlineCountRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_ONLINE_COUNT_RESPONSE_VALUE) {
            return false;
        }
        GetOnlineCountResponse response = GetOnlineCountResponse.parseFrom(frame.payload());
        List<String> factions = new ArrayList<>();
        for (FactionCount count : response.getByFactionList()) {
            factions.add(KageI18n.textOrKey(i18n, factionKey(count.getFaction())) + " " + count.getCount());
        }
        List<String> realms = new ArrayList<>();
        for (int i = 0; i < response.getByRealmCount() && i < REALM_KEYS.size(); i++) {
            if (response.getByRealm(i) > 0) {
                realms.add(KageI18n.textOrKey(i18n, REALM_KEYS.get(i)) + " " + response.getByRealm(i));
            }
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n, "online.line", response.getTotal(), String.join(", ", factions), String.join(", ", realms)));
        return true;
    }
}
