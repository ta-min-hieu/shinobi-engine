package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ArtifactOutcome;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UseArtifactCommand;
import dev.kage.protocol.UseArtifactResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Pháp bảo (ADR-127) — lệnh chat {@code /phapbao} kích hoạt chiêu của món đang mặc ở ô pháp bảo. Server đọc món đang
 * mặc, giữ hồi chiêu và áp hiệu ứng; client chỉ gửi ý định và báo lại kết quả.
 */
final class ArtifactCoordinator implements WindowFlow {

    private static final String COMMAND = "/phapbao";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ArtifactCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        sender.sendReadRequest(Opcode.OPCODE_USE_ARTIFACT_COMMAND_VALUE, UseArtifactCommand.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_USE_ARTIFACT_RESPONSE_VALUE) {
            return false;
        }
        UseArtifactResponse response = UseArtifactResponse.parseFrom(frame.payload());
        if (response.getOutcome() == ArtifactOutcome.ARTIFACT_OUTCOME_OK) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "artifact.used." + response.getEffect().toLowerCase(Locale.ROOT),
                    response.getMagnitudePercent(),
                    response.getDurationSeconds(),
                    response.getCooldownSeconds()));
        } else if (response.getOutcome() == ArtifactOutcome.ARTIFACT_OUTCOME_COOLDOWN) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "artifact.cooldown", response.getCooldownSeconds()));
        } else {
            String suffix = response.getOutcome()
                    .name()
                    .substring("ARTIFACT_OUTCOME_".length())
                    .toLowerCase(Locale.ROOT);
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "artifact.outcome." + suffix));
        }
        return true;
    }
}
