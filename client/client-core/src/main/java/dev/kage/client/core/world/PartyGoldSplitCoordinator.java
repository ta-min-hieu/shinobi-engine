package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalLong;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SplitPartyGoldCommand;
import dev.kage.protocol.SplitPartyGoldOutcome;
import dev.kage.protocol.SplitPartyGoldResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chia vàng cho nhóm (ADR-278) — {@code /chiavang <số>} (hoặc {@code /splitgold}). Server chọn người nhận (đồng đội
 * online cùng World), chia đều, trừ/cộng trong một transaction; client chỉ gửi tổng muốn chia và in kết quả.
 */
final class PartyGoldSplitCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PartyGoldSplitCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Số tiền từ đối số (chấp nhận dấu chấm/phẩy ngăn nghìn); rỗng khi không phải số dương. Tĩnh để test. */
    static OptionalLong amount(String argument) {
        String digits = argument.strip().replace(".", "").replace(",", "");
        if (!digits.matches("[0-9]{1,10}")) {
            return OptionalLong.empty();
        }
        long value = Long.parseLong(digits);
        return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/chiavang".equals(command) && !"/splitgold".equals(command)) {
            return false;
        }
        OptionalLong amount = amount(parts.length > 1 ? parts[1] : "");
        if (amount.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "splitgold.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SPLIT_PARTY_GOLD_COMMAND_VALUE,
                SplitPartyGoldCommand.newBuilder().setAmount(amount.getAsLong()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SPLIT_PARTY_GOLD_RESPONSE_VALUE) {
            return false;
        }
        SplitPartyGoldResponse response = SplitPartyGoldResponse.parseFrom(frame.payload());
        if (response.getOutcome() == SplitPartyGoldOutcome.SPLIT_PARTY_GOLD_OUTCOME_OK) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n, "splitgold.done", response.getEach(), response.getRecipients(), response.getGoldAfter()));
        } else {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, outcomeKey(response.getOutcome())));
        }
        return true;
    }

    /** Khoá i18n lý do bị từ chối. Tĩnh để test. */
    static String outcomeKey(SplitPartyGoldOutcome outcome) {
        return switch (outcome) {
            case SPLIT_PARTY_GOLD_OUTCOME_NOT_IN_PARTY -> "splitgold.not_in_party";
            case SPLIT_PARTY_GOLD_OUTCOME_NO_RECIPIENT -> "splitgold.no_recipient";
            case SPLIT_PARTY_GOLD_OUTCOME_NOT_ENOUGH_GOLD -> "splitgold.not_enough_gold";
            default -> "splitgold.usage";
        };
    }
}
