package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.Opcode;

/**
 * Bước lô 34: hệ số cường hoá trong catalog (ADR-199) {@code catalogStep}; mốc làm mới (ADR-200) {@code resetTimers};
 * mua lại (ADR-202) {@code buybackList}, {@code buyback:<entry id>}; lọc nhặt đồ (ADR-204) {@code autoSell:on|off|?};
 * xoá nhân vật (ADR-205) {@code deleteChar:<id>}; chỗ đứng NPC cho tự tìm đường (ADR-206) {@code npcLocations}. Nối
 * sau {@link Lot33Steps}; bước lạ chuyển tiếp cho {@link Lot35Steps}.
 */
final class Lot34Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot35Steps lot35;

    Lot34Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot35 = new Lot35Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "catalogStep" -> catalogStep();
            case "resetTimers" -> resetTimers();
            case "buybackList" -> buybackList();
            case "buyback" -> buyback(Long.parseLong(arg));
            case "autoSell" -> autoSell(arg);
            case "deleteChar" -> deleteChar(Long.parseLong(arg));
            case "npcLocations" -> npcLocations();
            default -> lot35.step(verb, arg);
        };
    }

    private String catalogStep() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE,
                GetItemCatalogRequest.getDefaultInstance(),
                Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE,
                p -> {
                    ItemCatalogResponse r = ItemCatalogResponse.parseFrom(p);
                    return "enhanceStepPercent=" + r.getEnhanceStepPercent() + " món=" + r.getEntriesCount();
                });
    }

    private String resetTimers() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_RESET_TIMERS_REQUEST_VALUE,
                dev.kage.protocol.GetResetTimersRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_RESET_TIMERS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.GetResetTimersResponse r = dev.kage.protocol.GetResetTimersResponse.parseFrom(p);
                    return "now=" + java.time.Instant.ofEpochSecond(r.getServerNowEpochSeconds()) + " ngày="
                            + java.time.Instant.ofEpochSecond(r.getNextDailyEpochSeconds()) + " tuần="
                            + java.time.Instant.ofEpochSecond(r.getNextWeeklyEpochSeconds());
                });
    }

    private String buybackList() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_BUYBACK_REQUEST_VALUE,
                dev.kage.protocol.ListBuybackRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_BUYBACK_RESPONSE_VALUE,
                p -> describe(dev.kage.protocol.ListBuybackResponse.parseFrom(p).getEntriesList()));
    }

    private String buyback(long entryId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_BUYBACK_COMMAND_VALUE,
                dev.kage.protocol.BuybackCommand.newBuilder()
                        .setEntryId(entryId)
                        .build(),
                Opcode.OPCODE_BUYBACK_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.BuybackResponse r = dev.kage.protocol.BuybackResponse.parseFrom(p);
                    return r.getOutcome().name() + " còn=" + describe(r.getEntriesList());
                });
    }

    private static String describe(java.util.List<dev.kage.protocol.BuybackView> entries) {
        StringBuilder out = new StringBuilder();
        for (dev.kage.protocol.BuybackView e : entries) {
            out.append('#')
                    .append(e.getEntryId())
                    .append(" tpl")
                    .append(e.getItemTemplateId())
                    .append('x')
                    .append(e.getQuantity())
                    .append('+')
                    .append(e.getEnhanceLevel())
                    .append(" giá=")
                    .append(e.getPrice())
                    .append("; ");
        }
        return out.toString();
    }

    private String autoSell(String arg) throws IOException {
        dev.kage.protocol.SetAutoSellJunkCommand.Builder command =
                dev.kage.protocol.SetAutoSellJunkCommand.newBuilder();
        if ("?".equals(arg)) {
            command.setQueryOnly(true);
        } else {
            command.setEnabled("on".equals(arg));
        }
        return asker.ask(
                Opcode.OPCODE_SET_AUTO_SELL_JUNK_COMMAND_VALUE,
                command.build(),
                Opcode.OPCODE_SET_AUTO_SELL_JUNK_RESPONSE_VALUE,
                p -> "enabled="
                        + dev.kage.protocol.SetAutoSellJunkResponse.parseFrom(p).getEnabled());
    }

    private String deleteChar(long characterId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_DELETE_CHARACTER_REQUEST_VALUE,
                dev.kage.protocol.DeleteCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build(),
                Opcode.OPCODE_DELETE_CHARACTER_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.DeleteCharacterResponse r =
                            dev.kage.protocol.DeleteCharacterResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder(r.getOutcome().name()).append(" danh sách=");
                    r.getCharactersList().forEach(c -> out.append(c.getName())
                            .append(c.getDeletionEpochSeconds() > 0 ? "(chờ xoá)" : "")
                            .append(' '));
                    return out.toString();
                });
    }

    private String npcLocations() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_NPC_LOCATIONS_REQUEST_VALUE,
                dev.kage.protocol.ListNpcLocationsRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_NPC_LOCATIONS_RESPONSE_VALUE,
                p -> {
                    dev.kage.protocol.ListNpcLocationsResponse r =
                            dev.kage.protocol.ListNpcLocationsResponse.parseFrom(p);
                    StringBuilder out = new StringBuilder("worldChính=").append(r.getInMainWorld());
                    r.getLocationsList().forEach(l -> out.append(' ')
                            .append(l.getNpcCode())
                            .append('@')
                            .append(Math.round(l.getX())));
                    return out.toString();
                });
    }
}
