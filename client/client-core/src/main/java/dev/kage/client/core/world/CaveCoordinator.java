package dev.kage.client.core.world;

import java.util.Locale;
import java.util.StringJoiner;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.CaveAction;
import dev.kage.protocol.CaveCommand;
import dev.kage.protocol.CaveDecorationView;
import dev.kage.protocol.CaveInviteEvent;
import dev.kage.protocol.CaveOutcome;
import dev.kage.protocol.CaveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Động phủ (ADR-129) — lệnh chat {@code /dongphu} (vào), {@code xem}, {@code thu}, {@code nang}, {@code bay <mã>},
 * {@code moi <tên>}, {@code tham <tên>}. Server giữ mọi con số; client chỉ gửi ý định và in dòng hệ thống.
 */
final class CaveCoordinator implements WindowFlow {

    private static final String COMMAND = "/dongphu";
    /** Danh mục bày trí thật chỉ có vài món — gói dị dạng không được kéo theo hàng nghìn lượt tra i18n. */
    private static final int MAX_DECORATIONS = 8;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    CaveCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 3);
        if (!COMMAND.equals(words[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        String verb = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "";
        String arg = words.length > 2 ? words[2].strip() : "";
        CaveCommand.Builder command = CaveCommand.newBuilder().setAction(actionOf(verb));
        if (command.getAction() == CaveAction.CAVE_ACTION_DECORATE) {
            command.setDecorationCode(arg);
        } else {
            command.setTargetName(arg);
        }
        sender.sendReadRequest(Opcode.OPCODE_CAVE_COMMAND_VALUE, command.build());
        return true;
    }

    private static CaveAction actionOf(String verb) {
        return switch (verb) {
            case "" -> CaveAction.CAVE_ACTION_ENTER;
            case "thu" -> CaveAction.CAVE_ACTION_COLLECT;
            case "nang" -> CaveAction.CAVE_ACTION_UPGRADE;
            case "bay" -> CaveAction.CAVE_ACTION_DECORATE;
            case "moi" -> CaveAction.CAVE_ACTION_INVITE;
            case "tham" -> CaveAction.CAVE_ACTION_VISIT;
            default -> CaveAction.CAVE_ACTION_VIEW;
        };
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_CAVE_INVITE_EVENT_VALUE) {
            CaveInviteEvent event = CaveInviteEvent.parseFrom(frame.payload());
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "cave.invited", event.getOwnerName(), event.getMinutes()));
            return true;
        }
        if (opcode != Opcode.OPCODE_CAVE_RESPONSE_VALUE) {
            return false;
        }
        CaveResponse response = CaveResponse.parseFrom(frame.payload());
        if (response.getOutcome() != CaveOutcome.CAVE_OUTCOME_OK) {
            chat.showSystemNotice(KageI18n.textOrKey(
                    i18n, "cave.outcome." + suffix(response.getOutcome().name(), "CAVE_OUTCOME_")));
        } else if (response.getAction() != CaveAction.CAVE_ACTION_VIEW) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "cave.ok." + suffix(response.getAction().name(), "CAVE_ACTION_"),
                    response.getCollected(),
                    response.getVeinLevel(),
                    response.getTargetName()));
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "cave.status",
                response.getVeinLevel(),
                response.getPerHour(),
                response.getStored(),
                response.getCapacity(),
                response.getUpgradeCost()));
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "cave.decorations", decorations(response)));
        return true;
    }

    private String decorations(CaveResponse response) {
        StringJoiner line = new StringJoiner(", ");
        int shown = 0;
        for (CaveDecorationView decoration : response.getDecorationsList()) {
            if (shown++ >= MAX_DECORATIONS) {
                break;
            }
            String name = KageI18n.textOrKey(i18n, decoration.getNameKey());
            line.add(
                    decoration.getPlaced()
                            ? KageI18n.formatOrKey(i18n, "cave.decoration.placed", name, decoration.getBonusPercent())
                            : KageI18n.formatOrKey(
                                    i18n,
                                    "cave.decoration.for_sale",
                                    name,
                                    decoration.getCode(),
                                    decoration.getGoldCost(),
                                    decoration.getBonusPercent()));
        }
        return line.toString();
    }

    /** Đuôi tên enum làm khoá i18n; giá trị lạ ({@code UNRECOGNIZED} từ server mới hơn) về {@code unspecified}. */
    private static String suffix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()).toLowerCase(Locale.ROOT) : "unspecified";
    }
}
