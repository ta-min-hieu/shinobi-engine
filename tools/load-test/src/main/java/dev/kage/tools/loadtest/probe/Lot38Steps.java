package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 38: huỷ vật phẩm (ADR-231) {@code destroy:<itemId>}; phân giải hàng loạt (ADR-232) {@code
 * dismantleAll:<cấp>}; lịch sử cường hoá (ADR-233) {@code enhanceLog}; giá gợi ý (ADR-234) {@code
 * priceHint:<templateId>}; thư toàn bang (ADR-235) {@code guildMail:<tiêu đề>/<nội dung>}; riêng tư (ADR-236/237)
 * {@code privacy:dnd-on|dnd-off|inv-on|inv-off|?}; lịch sử đăng nhập (ADR-238) {@code loginLog}. Nối sau {@link
 * Lot37Steps}.
 */
final class Lot38Steps {

    private final Lot23Scenario.Asker asker;

    Lot38Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "destroy" -> destroy(arg);
            case "dismantleAll" -> dismantleAll(arg);
            case "enhanceLog" -> enhanceLog();
            case "priceHint" -> priceHint(arg);
            case "guildMail" -> guildMail(arg);
            case "privacy" -> privacy(arg);
            case "loginLog" -> loginLog();
            default -> "bước không hiểu";
        };
    }

    private String destroy(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_DESTROY_ITEM_COMMAND_VALUE,
                dev.kage.protocol.DestroyItemCommand.newBuilder()
                        .setItemId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_DESTROY_ITEM_RESPONSE_VALUE,
                p -> dev.kage.protocol.DestroyItemResponse.parseFrom(p)
                        .getOutcome()
                        .name());
    }

    private String dismantleAll(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_BULK_DISMANTLE_COMMAND_VALUE,
                dev.kage.protocol.BulkDismantleCommand.newBuilder()
                        .setMaxRequiredLevel(Integer.parseInt(arg))
                        .build(),
                Opcode.OPCODE_BULK_DISMANTLE_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.BulkDismantleResponse r = dev.kage.protocol.BulkDismantleResponse.parseFrom(p);
                    return "phânGiải=" + r.getDismantled() + " vàng=" + r.getGold() + " thảo=" + r.getHerbs();
                });
    }

    private String enhanceLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ENHANCE_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetEnhanceHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_ENHANCE_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("cườngHoá=");
                    dev.kage.protocol.GetEnhanceHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ')
                                    .append(e.getItemTemplateId())
                                    .append(':')
                                    .append(e.getFromLevel())
                                    .append('>')
                                    .append(e.getToLevel())
                                    .append('/')
                                    .append(e.getResult()));
                    return out.toString();
                });
    }

    private String priceHint(String arg) throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_MARKET_PRICE_HINT_REQUEST_VALUE,
                dev.kage.protocol.GetMarketPriceHintRequest.newBuilder()
                        .setItemTemplateId(Long.parseLong(arg))
                        .build(),
                Opcode.OPCODE_GET_MARKET_PRICE_HINT_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetMarketPriceHintResponse r =
                            dev.kage.protocol.GetMarketPriceHintResponse.parseFrom(p);
                    return "rẻNhất=" + r.getLowestActive() + " tbBán=" + r.getAverageSold() + " lượt="
                            + r.getSoldListings();
                });
    }

    private String guildMail(String arg) throws IOException {
        String[] parts = arg.split("/", 2);
        return asker.ask(
                Opcode.OPCODE_SEND_GUILD_MAIL_COMMAND_VALUE,
                dev.kage.protocol.SendGuildMailCommand.newBuilder()
                        .setSubject(parts[0])
                        .setBody(parts.length > 1 ? parts[1] : "")
                        .build(),
                Opcode.OPCODE_SEND_GUILD_MAIL_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SendGuildMailResponse r = dev.kage.protocol.SendGuildMailResponse.parseFrom(p);
                    return r.getOutcome().name() + " nhận=" + r.getRecipients() + " cònLại=" + r.getRemainingToday();
                });
    }

    private String privacy(String arg) throws IOException {
        dev.kage.protocol.SetContactPrivacyCommand.Builder command =
                dev.kage.protocol.SetContactPrivacyCommand.newBuilder();
        if (arg.startsWith("dnd")) {
            command.setSetting(dev.kage.protocol.ContactPrivacySetting.CONTACT_PRIVACY_SETTING_DO_NOT_DISTURB);
        } else if (arg.startsWith("inv")) {
            command.setSetting(dev.kage.protocol.ContactPrivacySetting.CONTACT_PRIVACY_SETTING_BLOCK_STRANGER_INVITES);
        }
        command.setEnabled(arg.endsWith("-on"));
        return asker.ask(
                Opcode.OPCODE_SET_CONTACT_PRIVACY_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_SET_CONTACT_PRIVACY_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.SetContactPrivacyResponse r =
                            dev.kage.protocol.SetContactPrivacyResponse.parseFrom(p);
                    return "dnd=" + r.getDoNotDisturb() + " chặnMời=" + r.getBlockStrangerInvites();
                });
    }

    private String loginLog() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_LOGIN_HISTORY_REQUEST_VALUE,
                dev.kage.protocol.GetLoginHistoryRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_LOGIN_HISTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("đăngNhập=");
                    dev.kage.protocol.GetLoginHistoryResponse.parseFrom(p)
                            .getEntriesList()
                            .forEach(e -> out.append(' ').append(e.getMaskedIp()));
                    return out.toString();
                });
    }
}
