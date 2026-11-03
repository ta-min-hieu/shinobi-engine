package dev.kage.client.core.world;

import java.util.Locale;
import java.util.StringJoiner;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SweepDungeonCommand;
import dev.kage.protocol.SweepDungeonOutcome;
import dev.kage.protocol.SweepDungeonResponse;
import dev.kage.protocol.SweepLootView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Quét nhanh Bí Cảnh (ADR-162) — lệnh chat {@code /quetbicanh} (hoặc {@code /sweep}). Server kiểm đã hạ boss, lượt
 * ngày và vàng; client chỉ gửi ý định và in kết quả (EXP, lượt còn, đồ gửi qua thư) thành dòng hệ thống.
 */
final class DungeonSweepCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/quetbicanh";
    private static final String COMMAND_EN = "/sweep";
    private static final String OUTCOME_PREFIX = "SWEEP_DUNGEON_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    DungeonSweepCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.chat = feeds.chat();
        this.itemCatalog = feeds.itemCatalog();
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SWEEP_DUNGEON_COMMAND_VALUE,
                SweepDungeonCommand.newBuilder()
                        .setDungeonCode(DungeonController.DUNGEON_CODE)
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SWEEP_DUNGEON_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(SweepDungeonResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(SweepDungeonResponse response) {
        SweepDungeonOutcome outcome = response.getOutcome();
        if (outcome == SweepDungeonOutcome.SWEEP_DUNGEON_OUTCOME_OK) {
            StringJoiner loot = new StringJoiner(", ");
            for (SweepLootView item : response.getLootList()) {
                String name = itemCatalog
                        .nameKey(item.getItemTemplateId())
                        .map(key -> KageI18n.textOrKey(i18n, key))
                        .orElseGet(() -> "#" + item.getItemTemplateId());
                loot.add(name + " x" + item.getQuantity());
            }
            return KageI18n.formatOrKey(
                    i18n,
                    "sweep.ok",
                    response.getExpGained(),
                    response.getEntriesLeft(),
                    loot.length() == 0 ? KageI18n.textOrKey(i18n, "sweep.no_loot") : loot.toString());
        }
        String suffix = outcome == SweepDungeonOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "sweep.outcome." + suffix);
    }
}
