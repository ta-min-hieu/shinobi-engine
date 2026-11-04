package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.ClaimAllMailCommand;
import dev.kage.protocol.ClaimAllMailResponse;
import dev.kage.protocol.GetGoldHistoryRequest;
import dev.kage.protocol.GetGoldHistoryResponse;
import dev.kage.protocol.GetJunkPreviewRequest;
import dev.kage.protocol.GetJunkPreviewResponse;
import dev.kage.protocol.InspectCharacterRequest;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.JunkLineView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellJunkCommand;
import dev.kage.protocol.SellJunkResponse;
import dev.kage.protocol.SetInspectPrivacyCommand;
import dev.kage.protocol.SetInspectPrivacyResponse;
import dev.kage.protocol.UnsocketGemCommand;
import dev.kage.protocol.UnsocketGemResponse;

/**
 * Bước lô 30: nhận hết thư (ADR-167) {@code claimAllMail}, tháo ngọc (ADR-168) {@code unsocket:<itemId>}, sổ vàng
 * (ADR-169) {@code goldLog}, bán rác (ADR-170) {@code junkPreview}, {@code sellJunk}, soi trang bị
 * (ADR-171) {@code inspect:<tên>}, {@code hideGear:<true|false>}.
 */
final class Lot30Steps {

    private final Lot23Scenario.Asker asker;

    Lot30Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "claimAllMail" -> claimAllMail();
            case "unsocket" -> unsocket(Long.parseLong(arg));
            case "goldLog" -> goldLog();
            case "junkPreview" -> junkPreview();
            case "sellJunk" -> sellJunk();
            case "inspect" -> inspect(arg);
            case "hideGear" -> hideGear(Boolean.parseBoolean(arg));
            default -> "bước không hiểu";
        };
    }

    private String claimAllMail() throws IOException {
        return asker.ask(
                Opcode.OPCODE_CLAIM_ALL_MAIL_COMMAND_VALUE,
                ClaimAllMailCommand.newBuilder().setDeleteClaimed(true).build(),
                Opcode.OPCODE_CLAIM_ALL_MAIL_RESPONSE_VALUE,
                p -> {
                    ClaimAllMailResponse r = ClaimAllMailResponse.parseFrom(p);
                    return "lĩnh=" + r.getClaimed() + " vàng=" + r.getGold() + " món=" + r.getItems() + " xoá="
                            + r.getDeleted() + " đầy=" + r.getBagFull();
                });
    }

    private String unsocket(long itemId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_UNSOCKET_GEM_COMMAND_VALUE,
                UnsocketGemCommand.newBuilder().setItemId(itemId).build(),
                Opcode.OPCODE_UNSOCKET_GEM_RESPONSE_VALUE,
                p -> {
                    UnsocketGemResponse r = UnsocketGemResponse.parseFrom(p);
                    return r.getOutcome().name() + " ngọc=" + r.getGemItemTemplateId() + " phí=" + r.getFee()
                            + " máuTốiĐa=" + r.getMaxHp();
                });
    }

    private String goldLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GOLD_HISTORY_REQUEST_VALUE,
                GetGoldHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GOLD_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder line = new StringBuilder();
                    GetGoldHistoryResponse.parseFrom(p).getEntriesList().forEach(e -> line.append(" [")
                            .append(e.getDelta())
                            .append(" còn ")
                            .append(e.getBalance())
                            .append(' ')
                            .append(e.getReasonKey())
                            .append(']'));
                    return line.toString().strip();
                });
    }

    private String junkPreview() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_JUNK_PREVIEW_REQUEST_VALUE,
                GetJunkPreviewRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_JUNK_PREVIEW_RESPONSE_VALUE,
                p -> lines(GetJunkPreviewResponse.parseFrom(p).getLinesList()));
    }

    private String sellJunk() throws IOException {
        return asker.ask(
                Opcode.OPCODE_SELL_JUNK_COMMAND_VALUE,
                SellJunkCommand.getDefaultInstance(),
                Opcode.OPCODE_SELL_JUNK_RESPONSE_VALUE,
                p -> {
                    SellJunkResponse r = SellJunkResponse.parseFrom(p);
                    return "vàng=" + r.getGold() + " đầy=" + r.getGoldFull() + " " + lines(r.getSoldList());
                });
    }

    private static String lines(java.util.List<JunkLineView> lines) {
        StringBuilder line = new StringBuilder();
        lines.forEach(l -> line.append(' ')
                .append(l.getItemTemplateId())
                .append('x')
                .append(l.getQuantity())
                .append('@')
                .append(l.getPriceEach()));
        return line.toString().strip();
    }

    private String inspect(String name) throws IOException {
        return asker.ask(
                Opcode.OPCODE_INSPECT_CHARACTER_REQUEST_VALUE,
                InspectCharacterRequest.newBuilder().setName(name).build(),
                Opcode.OPCODE_INSPECT_CHARACTER_RESPONSE_VALUE,
                p -> {
                    InspectCharacterResponse r = InspectCharacterResponse.parseFrom(p);
                    StringBuilder line = new StringBuilder(r.getOutcome().name())
                            .append(' ')
                            .append(r.getName())
                            .append(" cấp=")
                            .append(r.getLevel())
                            .append(" lựcChiến=")
                            .append(r.getCombatPower());
                    r.getEquipmentList().forEach(e -> line.append(" ô")
                            .append(e.getSlot())
                            .append('=')
                            .append(e.getItemTemplateId())
                            .append('+')
                            .append(e.getEnhanceLevel())
                            .append(
                                    e.getSocketGemTemplateId() != 0
                                            ? "[ngọc " + e.getSocketGemTemplateId() + "]"
                                            : ""));
                    return line.toString();
                });
    }

    private String hideGear(boolean hidden) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_INSPECT_PRIVACY_COMMAND_VALUE,
                SetInspectPrivacyCommand.newBuilder().setHidden(hidden).build(),
                Opcode.OPCODE_SET_INSPECT_PRIVACY_RESPONSE_VALUE,
                p -> "ẩn=" + SetInspectPrivacyResponse.parseFrom(p).getHidden());
    }
}
