package dev.kage.client.core.world;

import java.util.EnumSet;
import java.util.Locale;
import java.util.StringJoiner;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Faction;
import dev.kage.protocol.FormationChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Trận pháp nhóm (ADR-128) — nhận gói 1327 (server chỉ gửi khi trận của mình lập/đổi/tan) và báo một dòng hệ thống.
 * Chỉ số thưởng đã nằm trong chỉ số server gửi; client không tính gì. Không có cửa sổ.
 */
final class FormationCoordinator implements WindowFlow {

    /** Số linh căn có thật (Hoả, Thuỷ, Phong, Lôi, Thổ). */
    private static final int MAX_FACTIONS = 5;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FormationCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_FORMATION_CHANGED_EVENT_VALUE) {
            return false;
        }
        FormationChangedEvent event = FormationChangedEvent.parseFrom(frame.payload());
        if (event.getMembers() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "formation.broken"));
            return true;
        }
        // Bỏ trùng + trần số linh căn có thật: gói dị dạng 100k phần tử không được biến thành 100k lượt tra i18n.
        StringJoiner factions = new StringJoiner(", ");
        EnumSet<Faction> seen = EnumSet.noneOf(Faction.class);
        for (Faction faction : event.getFactionsList()) {
            if (seen.size() >= MAX_FACTIONS) {
                break;
            }
            if (faction != Faction.UNRECOGNIZED && seen.add(faction)) {
                factions.add(KageI18n.textOrKey(i18n, "faction." + suffix(faction.name(), "FACTION_")));
            }
        }
        String tier = KageI18n.textOrKey(
                i18n, "formation.tier." + suffix(event.getTier().name(), "FORMATION_TIER_"));
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "formation.formed",
                tier,
                event.getMembers(),
                factions.toString(),
                event.getBonusAtk(),
                event.getBonusDef(),
                event.getBonusRes(),
                event.getBonusHp()));
        return true;
    }

    /** Đuôi tên enum làm khoá i18n; giá trị lạ ({@code UNRECOGNIZED} từ server mới hơn) về {@code unspecified}. */
    private static String suffix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()).toLowerCase(Locale.ROOT) : "unspecified";
    }
}
