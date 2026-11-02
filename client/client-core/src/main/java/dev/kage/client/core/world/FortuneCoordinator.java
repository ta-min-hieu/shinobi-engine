package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DrawFortuneCommand;
import dev.kage.protocol.DrawFortuneOutcome;
import dev.kage.protocol.DrawFortuneResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Quẻ bói hằng ngày (ADR-149) — lệnh chat {@code /xinque} (hoặc {@code /fortune}) khi đứng gần trưởng lão Chu Hoành.
 * Server tung quẻ, giữ hiệu lực EXP và cấp quà; client in quẻ thành dòng hệ thống.
 */
final class FortuneCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/xinque";
    private static final String COMMAND_EN = "/fortune";
    private static final String OUTCOME_PREFIX = "DRAW_FORTUNE_OUTCOME_";

    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    FortuneCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_DRAW_FORTUNE_COMMAND_VALUE, DrawFortuneCommand.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_DRAW_FORTUNE_RESPONSE_VALUE) {
            return false;
        }
        DrawFortuneResponse response = DrawFortuneResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(response));
        if (response.getOutcome() == DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_OK && response.getItemTemplateId() != 0) {
            sender.listInventory(); // quà vừa vào túi
        }
        return true;
    }

    private String describe(DrawFortuneResponse response) {
        DrawFortuneOutcome outcome = response.getOutcome();
        boolean hasFortune = !response.getNameKey().isEmpty()
                && (outcome == DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_OK
                        || outcome == DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_ALREADY_DRAWN);
        if (!hasFortune) {
            String suffix = outcome == DrawFortuneOutcome.UNRECOGNIZED
                    ? "unspecified"
                    : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
            return text("fortune.outcome." + suffix);
        }
        StringBuilder line = new StringBuilder();
        if (outcome == DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_ALREADY_DRAWN) {
            line.append(text("fortune.already")).append(' ');
        }
        line.append(KageI18n.formatOrKey(i18n, "fortune.drawn", text(response.getNameKey())))
                .append(' ')
                .append(text(response.getTextKey()));
        int percent = response.getExpPercent();
        if (percent != 0) {
            line.append(' ')
                    .append(KageI18n.formatOrKey(
                            i18n, percent > 0 ? "fortune.exp.up" : "fortune.exp.down", Math.abs(percent)));
        }
        if (response.getGold() > 0) {
            line.append(' ').append(KageI18n.formatOrKey(i18n, "fortune.reward.gold", response.getGold()));
        }
        if (response.getItemTemplateId() != 0) {
            String name = itemCatalog
                    .nameKey(response.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + response.getItemTemplateId());
            line.append(' ').append(KageI18n.formatOrKey(i18n, "fortune.reward.item", name, response.getQuantity()));
        }
        return line.toString();
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
