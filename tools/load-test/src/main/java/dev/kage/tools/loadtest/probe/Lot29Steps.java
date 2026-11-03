package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.UUID;

import dev.kage.protocol.CombineGemsCommand;
import dev.kage.protocol.CombineGemsResponse;
import dev.kage.protocol.DismantleItemCommand;
import dev.kage.protocol.DismantleItemResponse;
import dev.kage.protocol.GetGemRecipesRequest;
import dev.kage.protocol.GetGemRecipesResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SetItemLockCommand;
import dev.kage.protocol.SetItemLockResponse;
import dev.kage.protocol.SortInventoryCommand;
import dev.kage.protocol.SortInventoryResponse;
import dev.kage.protocol.SweepDungeonCommand;
import dev.kage.protocol.SweepDungeonResponse;

/**
 * Bước lô 29: túi đồ {@code bag} (in id:templateId×số lượng, 🔒 nếu khoá), khoá vật phẩm (ADR-159) {@code
 * lock:<itemId>}, {@code unlock:<itemId>}, cùng hai đường làm đồ rời túi để thử khoá: {@code sell:<itemId>}, {@code
 * dismantle:<itemId>}; sắp xếp túi (ADR-160) {@code sortBag}; ghép
 * ngọc (ADR-161) {@code gemRecipes}, {@code combineGem:<templateId nguồn>}; quét Bí
 * Cảnh (ADR-162) {@code sweep[:<mã>]}.
 */
final class Lot29Steps {

    private final Lot23Scenario.Asker asker;

    Lot29Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "bag" -> bag();
            case "lock" -> lock(Long.parseLong(arg), true);
            case "unlock" -> lock(Long.parseLong(arg), false);
            case "sell" -> sell(Long.parseLong(arg));
            case "dismantle" -> dismantle(Long.parseLong(arg));
            case "sortBag" -> sortBag();
            case "gemRecipes" -> gemRecipes();
            case "combineGem" -> combineGem(Long.parseLong(arg));
            case "sweep" -> sweep(arg.isEmpty() ? "sample_dungeon" : arg);
            default -> "bước không hiểu";
        };
    }

    private String bag() throws IOException {
        return asker.ask(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.getDefaultInstance(),
                Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE,
                p -> {
                    StringBuilder line = new StringBuilder();
                    ListInventoryResponse.parseFrom(p).getItemsList().forEach(i -> line.append(' ')
                            .append(i.getItemId())
                            .append(':')
                            .append(i.getItemTemplateId())
                            .append('x')
                            .append(i.getQuantity())
                            .append(i.getLocked() ? "[LOCK]" : ""));
                    return line.toString().strip();
                });
    }

    private String lock(long itemId, boolean locked) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SET_ITEM_LOCK_COMMAND_VALUE,
                SetItemLockCommand.newBuilder()
                        .setItemId(itemId)
                        .setLocked(locked)
                        .build(),
                Opcode.OPCODE_SET_ITEM_LOCK_RESPONSE_VALUE,
                p -> {
                    SetItemLockResponse r = SetItemLockResponse.parseFrom(p);
                    return r.getOutcome().name() + " item=" + r.getItemId() + " locked=" + r.getLocked();
                });
    }

    private String sortBag() throws IOException {
        return asker.ask(
                Opcode.OPCODE_SORT_INVENTORY_COMMAND_VALUE,
                SortInventoryCommand.getDefaultInstance(),
                Opcode.OPCODE_SORT_INVENTORY_RESPONSE_VALUE,
                p -> {
                    SortInventoryResponse r = SortInventoryResponse.parseFrom(p);
                    return "ok=" + r.getOk() + " gom=" + r.getMergedRows();
                });
    }

    private String gemRecipes() throws IOException {
        return asker.ask(
                Opcode.OPCODE_GET_GEM_RECIPES_REQUEST_VALUE,
                GetGemRecipesRequest.getDefaultInstance(),
                Opcode.OPCODE_GET_GEM_RECIPES_RESPONSE_VALUE,
                p -> describe(GetGemRecipesResponse.parseFrom(p)));
    }

    private String combineGem(long sourceTemplateId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_COMBINE_GEMS_COMMAND_VALUE,
                CombineGemsCommand.newBuilder()
                        .setSourceItemTemplateId(sourceTemplateId)
                        .build(),
                Opcode.OPCODE_COMBINE_GEMS_RESPONSE_VALUE,
                p -> {
                    CombineGemsResponse r = CombineGemsResponse.parseFrom(p);
                    return r.getOutcome().name() + " ra=" + r.getResultItemTemplateId() + " "
                            + describe(r.getRecipes());
                });
    }

    private String sweep(String dungeonCode) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SWEEP_DUNGEON_COMMAND_VALUE,
                SweepDungeonCommand.newBuilder().setDungeonCode(dungeonCode).build(),
                Opcode.OPCODE_SWEEP_DUNGEON_RESPONSE_VALUE,
                p -> {
                    SweepDungeonResponse r = SweepDungeonResponse.parseFrom(p);
                    StringBuilder line = new StringBuilder(r.getOutcome().name())
                            .append(" exp=")
                            .append(r.getExpGained())
                            .append(" còn=")
                            .append(r.getEntriesLeft());
                    r.getLootList().forEach(l -> line.append(' ')
                            .append(l.getItemTemplateId())
                            .append('x')
                            .append(l.getQuantity()));
                    return line.toString();
                });
    }

    private static String describe(GetGemRecipesResponse recipes) {
        StringBuilder line = new StringBuilder();
        recipes.getRecipesList().forEach(r -> line.append(' ')
                .append(r.getSourceItemTemplateId())
                .append('>')
                .append(r.getResultItemTemplateId())
                .append(" có=")
                .append(r.getOwned()));
        return line.toString().strip();
    }

    private String sell(long itemId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE,
                SellItemCommand.newBuilder()
                        .setItemId(itemId)
                        .setQuantity(1)
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build(),
                Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE,
                p -> SellItemResponse.parseFrom(p).getOutcome().name());
    }

    private String dismantle(long itemId) throws IOException {
        return asker.ask(
                Opcode.OPCODE_DISMANTLE_ITEM_COMMAND_VALUE,
                DismantleItemCommand.newBuilder().setItemId(itemId).build(),
                Opcode.OPCODE_DISMANTLE_ITEM_RESPONSE_VALUE,
                p -> {
                    DismantleItemResponse r = DismantleItemResponse.parseFrom(p);
                    return r.getOutcome().name() + " vàng=" + r.getGold();
                });
    }
}
