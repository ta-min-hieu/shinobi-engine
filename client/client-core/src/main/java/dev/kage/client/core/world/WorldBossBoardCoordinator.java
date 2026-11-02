package dev.kage.client.core.world;

import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WorldBossBoardEntry;
import dev.kage.protocol.WorldBossBoardEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng sát thương boss thế giới trực tiếp (ADR-157) — gói 414 tới mỗi ~2 giây khi mình có tên trong sổ; dựng vài dòng
 * chữ (tên boss, top 5, hạng của mình) cho HUD. {@code ended} ⇒ gỡ bảng. Chỉ hiển thị.
 */
final class WorldBossBoardCoordinator implements WindowFlow {

    /** Gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_LINES = 5;

    private final I18NBundle i18n = KageI18n.load();
    private Consumer<String> board = text -> {};

    /** Nối HUD — gọi một lần lúc dựng màn hình. */
    void useBoard(Consumer<String> setter) {
        this.board = setter;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_WORLD_BOSS_BOARD_EVENT_VALUE) {
            return false;
        }
        board.accept(describe(WorldBossBoardEvent.parseFrom(frame.payload())));
        return true;
    }

    /** Chữ của bảng; rỗng khi boss đã chết. Tách ra để test không cần HUD. */
    String describe(WorldBossBoardEvent event) {
        if (event.getEnded()) {
            return "";
        }
        StringBuilder text = new StringBuilder(KageI18n.formatOrKey(
                i18n,
                "boss_board.title",
                KageI18n.textOrKey(i18n, "hud.monster." + event.getBossCode()),
                event.getParticipants()));
        int line = 0;
        for (WorldBossBoardEntry entry : event.getTopList()) {
            if (line >= MAX_LINES) {
                break;
            }
            line++;
            text.append('\n')
                    .append(KageI18n.formatOrKey(i18n, "boss_board.row", line, entry.getName(), entry.getDamage()));
        }
        if (event.getMyRank() > 0) {
            text.append('\n')
                    .append(KageI18n.formatOrKey(i18n, "boss_board.me", event.getMyRank(), event.getMyDamage()));
        }
        return text.toString();
    }

    @Override
    public void reset() {
        board.accept("");
    }
}
