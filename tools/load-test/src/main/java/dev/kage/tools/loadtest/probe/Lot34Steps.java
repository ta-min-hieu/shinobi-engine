package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.Opcode;

/**
 * Bước lô 34: hệ số cường hoá trong catalog (ADR-199) {@code catalogStep}; mốc làm mới (ADR-200) {@code resetTimers};
 * mua lại (ADR-202) {@code buybackList}, {@code buyback:<entry id>}. Nối sau {@link Lot33Steps}.
 */
final class Lot34Steps {

    private final Lot23Scenario.Asker asker;

    Lot34Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "catalogStep" -> catalogStep();
            case "resetTimers" -> resetTimers();
            case "buybackList" -> buybackList();
            case "buyback" -> buyback(Long.parseLong(arg));
            default -> "bước không hiểu";
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
}
