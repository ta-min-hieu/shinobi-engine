package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TameMonsterCommand;
import dev.kage.protocol.TameMonsterResponse;
import dev.kage.protocol.TameOutcome;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thu phục yêu thú (ADR-124) — lệnh chat {@code /thuphuc} dùng một lá Phong Yêu Phù lên con quái đang nhắm. Không có
 * cửa sổ: kết quả hiện thành dòng hệ thống. Server kiểm tầm, máu, loài và tung tỉ lệ; client chỉ gửi ý định.
 */
final class TamingCoordinator implements WindowFlow {

    private static final String COMMAND = "/thuphuc";

    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private LongSupplier currentTarget = () -> 0L;

    TamingCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        chat.addSlashCommands(this::onSlash);
    }

    /** Nguồn id mục tiêu đang nhắm — {@code WorldScreen} nối lúc dựng màn hình. */
    void useTarget(LongSupplier target) {
        this.currentTarget = target;
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        long target = currentTarget.getAsLong();
        if (target >= 0L) { // quái mang id ÂM; 0 = chưa nhắm, dương = người chơi
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "taming.need_target"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_TAME_MONSTER_COMMAND_VALUE,
                TameMonsterCommand.newBuilder().setMonsterId(target).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_TAME_MONSTER_RESPONSE_VALUE) {
            return false;
        }
        TameMonsterResponse response = TameMonsterResponse.parseFrom(frame.payload());
        if (response.getOutcome() == TameOutcome.TAME_OUTCOME_CAPTURED) {
            String pet = itemCatalog
                    .nameKey(response.getPetItemTemplateId())
                    .map(key -> KageI18n.textOrKey(i18n, key))
                    .orElseGet(() -> "#" + response.getPetItemTemplateId());
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, response.getMailed() ? "taming.captured_mailed" : "taming.captured", pet));
            sender.listInventory(); // túi vừa đổi
            return true;
        }
        String suffix =
                response.getOutcome().name().substring("TAME_OUTCOME_".length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "taming.outcome." + suffix));
        return true;
    }
}
