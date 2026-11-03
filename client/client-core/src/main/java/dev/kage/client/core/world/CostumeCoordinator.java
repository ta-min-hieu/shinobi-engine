package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetCostumeCommand;
import dev.kage.protocol.SetCostumeOutcome;
import dev.kage.protocol.SetCostumeResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Huyễn hình trang phục (ADR-166) — lệnh chat {@code /huyenhinh <1|2|3|tudong>} (hoặc {@code /costume <1|2|3|auto>}):
 * chọn HIỂN THỊ một bậc trang phục đã mở. Server kiểm bậc đã mở, trừ phí, báo người xung quanh; client chỉ gửi ý định
 * và in kết quả.
 */
final class CostumeCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/huyenhinh";
    private static final String COMMAND_EN = "/costume";
    private static final String OUTCOME_PREFIX = "SET_COSTUME_OUTCOME_";

    /** Chữ người gõ → bậc; "tự động" = trở về theo cảnh giới. */
    private static final Map<String, CostumeTier> TIERS = Map.of(
            "1", CostumeTier.COSTUME_TIER_B1,
            "2", CostumeTier.COSTUME_TIER_B2,
            "3", CostumeTier.COSTUME_TIER_B3,
            "tudong", CostumeTier.COSTUME_TIER_UNSPECIFIED,
            "auto", CostumeTier.COSTUME_TIER_UNSPECIFIED);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    CostumeCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().toLowerCase(Locale.ROOT).split("\\s+", 2);
        if (!COMMAND_VI.equals(words[0]) && !COMMAND_EN.equals(words[0])) {
            return false;
        }
        CostumeTier tier = words.length == 2 ? TIERS.get(words[1]) : null;
        if (tier == null) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "costume.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_COSTUME_COMMAND_VALUE,
                SetCostumeCommand.newBuilder().setTier(tier).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_COSTUME_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(SetCostumeResponse.parseFrom(frame.payload())));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(SetCostumeResponse response) {
        SetCostumeOutcome outcome = response.getOutcome();
        if (outcome == SetCostumeOutcome.SET_COSTUME_OUTCOME_OK) {
            return KageI18n.formatOrKey(i18n, "costume.ok", response.getShownTierValue(), response.getFee());
        }
        String suffix = outcome == SetCostumeOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "costume.outcome." + suffix);
    }
}
