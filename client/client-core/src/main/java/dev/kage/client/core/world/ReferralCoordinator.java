package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnterReferralCommand;
import dev.kage.protocol.EnterReferralResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Mã giới thiệu (ADR-320) — {@code /magioithieu} xem mã của mình (= tên nhân vật); {@code /magioithieu <tên>} nhập mã
 * người giới thiệu (trước cấp 10). Tới cấp 10 cả hai nhận thư quà (server làm).
 */
final class ReferralCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ReferralCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/magioithieu".equals(command) && !"/referral".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_ENTER_REFERRAL_COMMAND_VALUE,
                EnterReferralCommand.newBuilder()
                        .setReferrerName(words.length > 1 ? words[1].strip() : "")
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_ENTER_REFERRAL_RESPONSE_VALUE) {
            return false;
        }
        EnterReferralResponse response = EnterReferralResponse.parseFrom(frame.payload());
        String key = "referral."
                + response.getOutcome()
                        .name()
                        .replace("ENTER_REFERRAL_OUTCOME_", "")
                        .toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, key, response.getCode(), response.getRewardLevel()));
        return true;
    }
}
