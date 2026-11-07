package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnemyView;
import dev.kage.protocol.ListEnemiesRequest;
import dev.kage.protocol.ListEnemiesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RemoveEnemyCommand;
import dev.kage.protocol.RemoveEnemyResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Danh sách cừu nhân (ADR-194) — {@code /cuunhan} (hoặc {@code /enemies}) in danh sách vào khung chat; {@code /cuunhan
 * xoa <tên>} (hoặc {@code remove}) xoá một dòng. Server ghi dòng khi mình bị người chơi hạ trong dải PvP.
 */
final class EnemyListCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/cuunhan";
    private static final String COMMAND_EN = "/enemies";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    EnemyListCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length == 1) {
            sender.sendReadRequest(Opcode.OPCODE_LIST_ENEMIES_REQUEST_VALUE, ListEnemiesRequest.getDefaultInstance());
            return true;
        }
        String verb = words[1].toLowerCase(Locale.ROOT);
        if (words.length == 3 && ("xoa".equals(verb) || "remove".equals(verb))) {
            sender.sendReadRequest(
                    Opcode.OPCODE_REMOVE_ENEMY_COMMAND_VALUE,
                    RemoveEnemyCommand.newBuilder().setName(words[2].strip()).build());
        } else {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "enemy.usage"));
        }
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_ENEMIES_RESPONSE_VALUE) {
            describe(
                            i18n,
                            ListEnemiesResponse.parseFrom(frame.payload()),
                            java.time.Instant.now().getEpochSecond())
                    .forEach(chat::showSystemNotice);
            return true;
        }
        if (opcode == Opcode.OPCODE_REMOVE_ENEMY_RESPONSE_VALUE) {
            RemoveEnemyResponse response = RemoveEnemyResponse.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    response.getRemoved() ? "enemy.removed" : "enemy.not_found",
                    ChatController.sanitize(response.getName())));
            return true;
        }
        return false;
    }

    /** Các dòng hệ thống cho một danh sách — tĩnh để test không cần màn hình. */
    static List<String> describe(I18NBundle i18n, ListEnemiesResponse response, long nowEpochSeconds) {
        List<String> lines = new ArrayList<>();
        if (response.getEnemiesCount() == 0) {
            lines.add(KageI18n.textOrKey(i18n, "enemy.empty"));
            return lines;
        }
        lines.add(KageI18n.formatOrKey(i18n, "enemy.header", response.getEnemiesCount()));
        for (EnemyView enemy : response.getEnemiesList()) {
            lines.add(KageI18n.formatOrKey(
                    i18n,
                    "enemy.row",
                    ChatController.sanitize(enemy.getName()),
                    enemy.getKillCount(),
                    LastOnlineText.describe(i18n, enemy.getLastKilledEpochSeconds(), nowEpochSeconds),
                    KageI18n.textOrKey(i18n, enemy.getOnline() ? "friend.status.online" : "friend.status.offline")));
        }
        return lines;
    }
}
