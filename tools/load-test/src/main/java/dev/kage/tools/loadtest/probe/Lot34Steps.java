package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.Opcode;

/** Bước lô 34: hệ số cường hoá trong catalog (ADR-199) {@code catalogStep}. Nối sau {@link Lot33Steps}. */
final class Lot34Steps {

    private final Lot23Scenario.Asker asker;

    Lot34Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "catalogStep" -> catalogStep();
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
}
