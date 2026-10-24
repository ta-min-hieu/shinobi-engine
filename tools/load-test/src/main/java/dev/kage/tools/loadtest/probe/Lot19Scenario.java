package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import dev.kage.protocol.BuyGuildShopItemCommand;
import dev.kage.protocol.BuyGuildShopItemResponse;
import dev.kage.protocol.CraftItemCommand;
import dev.kage.protocol.CraftItemResponse;
import dev.kage.protocol.GuildShopItemView;
import dev.kage.protocol.JoinPartyFinderCommand;
import dev.kage.protocol.JoinPartyFinderResponse;
import dev.kage.protocol.ListGuildShopRequest;
import dev.kage.protocol.ListGuildShopResponse;
import dev.kage.protocol.ListPartyFinderRequest;
import dev.kage.protocol.ListPartyFinderResponse;
import dev.kage.protocol.ListRecipesRequest;
import dev.kage.protocol.ListRecipesResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyFinderListingView;
import dev.kage.protocol.PartyGoal;
import dev.kage.protocol.PostPartyFinderCommand;
import dev.kage.protocol.PostPartyFinderResponse;
import dev.kage.protocol.RecipeMaterialView;
import dev.kage.protocol.RecipeView;
import dev.kage.protocol.RemovePartyFinderCommand;
import dev.kage.protocol.RemovePartyFinderResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Ba tính năng lô 19 chỉ client có giao diện mới gửi được — lò đan (ADR-079, 1800–1803), cửa hàng bang (ADR-080,
 * 723–726), bảng tìm nhóm (ADR-082, 1319–1326). Không có kịch bản này thì ba mục đó chỉ kiểm được bằng tay.
 */
final class Lot19Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    Lot19Scenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(ProbeConfig.Lot19Plan plan) throws IOException {
        if (!plan.craftRecipes().isEmpty()) {
            printRecipes(listRecipes());
            for (String recipe : plan.craftRecipes()) {
                craft(recipe);
            }
        }
        if (!plan.guildShopBuy().isEmpty()) {
            printShop("trước", listGuildShop());
            for (String templateId : plan.guildShopBuy()) {
                buyGuildShop(Long.parseLong(templateId.strip()));
            }
        }
        for (String step : plan.partyFinderSteps()) {
            partyFinderStep(step.strip());
        }
        new Lot20Scenario(connection, session).run(plan.lot20Steps());
        new Lot21Scenario(connection, session).run(plan.lot21Steps());
    }

    private ListRecipesResponse listRecipes() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_RECIPES_REQUEST_VALUE,
                ListRecipesRequest.getDefaultInstance().toByteArray());
        return ListRecipesResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_RECIPES_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
    }

    private void printRecipes(ListRecipesResponse recipes) {
        StringBuilder line = new StringBuilder("công thức lò đan:");
        for (RecipeView recipe : recipes.getRecipesList()) {
            line.append(String.format(
                    Locale.ROOT,
                    " | %s → #%d x%d, %d vàng, Lv%d, cần",
                    recipe.getCode(),
                    recipe.getOutputItemTemplateId(),
                    recipe.getOutputQuantity(),
                    recipe.getGoldCost(),
                    recipe.getRequiredLevel()));
            for (RecipeMaterialView material : recipe.getMaterialsList()) {
                line.append(
                        String.format(Locale.ROOT, " #%d x%d", material.getItemTemplateId(), material.getQuantity()));
            }
        }
        session.note(line.toString());
    }

    private void craft(String recipe) throws IOException {
        connection.send(
                Opcode.OPCODE_CRAFT_ITEM_COMMAND_VALUE,
                CraftItemCommand.newBuilder().setRecipeCode(recipe).build().toByteArray());
        CraftItemResponse response = CraftItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CRAFT_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("luyện " + recipe + ": " + response.getOutcome());
    }

    private ListGuildShopResponse listGuildShop() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_GUILD_SHOP_REQUEST_VALUE,
                ListGuildShopRequest.getDefaultInstance().toByteArray());
        return ListGuildShopResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_GUILD_SHOP_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
    }

    private void printShop(String label, ListGuildShopResponse shop) {
        StringBuilder line = new StringBuilder(String.format(
                Locale.ROOT,
                "cửa hàng bang %s: trongBang=%s cấpBang=%d cốngHiếnCòn=%d",
                label,
                shop.getInGuild(),
                shop.getGuildLevel(),
                shop.getContributionAvailable()));
        for (GuildShopItemView item : shop.getItemsList()) {
            line.append(String.format(
                    Locale.ROOT,
                    " | #%d %d cốngHiến (bang Lv%d)",
                    item.getItemTemplateId(),
                    item.getContributionCost(),
                    item.getRequiredGuildLevel()));
        }
        session.note(line.toString());
    }

    private void buyGuildShop(long templateId) throws IOException {
        connection.send(
                Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_COMMAND_VALUE,
                BuyGuildShopItemCommand.newBuilder()
                        .setItemTemplateId(templateId)
                        .build()
                        .toByteArray());
        BuyGuildShopItemResponse response = BuyGuildShopItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("đổi hàng bang #" + templateId + ": " + response.getOutcome());
        printShop("sau", response.getShop());
    }

    /** {@code post:LEVELING[:minLevel]}, {@code list}, {@code join:<leaderId>}, {@code remove}. */
    private void partyFinderStep(String step) throws IOException {
        String[] parts = step.split(":", -1);
        switch (parts[0]) {
            case "post" -> {
                PostPartyFinderCommand.Builder command = PostPartyFinderCommand.newBuilder()
                        .setGoal(PartyGoal.valueOf("PARTY_GOAL_" + parts[1].toUpperCase(Locale.ROOT)))
                        .setMinLevel(parts.length > 2 ? Integer.parseInt(parts[2]) : 1);
                connection.send(
                        Opcode.OPCODE_POST_PARTY_FINDER_COMMAND_VALUE,
                        command.build().toByteArray());
                PostPartyFinderResponse response = PostPartyFinderResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_POST_PARTY_FINDER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload());
                session.note("tìm nhóm đăng " + parts[1] + ": " + response.getOutcome());
                printBoard(response.getBoard());
            }
            case "join" -> {
                connection.send(
                        Opcode.OPCODE_JOIN_PARTY_FINDER_COMMAND_VALUE,
                        JoinPartyFinderCommand.newBuilder()
                                .setLeaderCharacterId(Long.parseLong(parts[1]))
                                .build()
                                .toByteArray());
                JoinPartyFinderResponse response = JoinPartyFinderResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_JOIN_PARTY_FINDER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload());
                session.note("tìm nhóm vào nhóm của " + parts[1] + ": " + response.getOutcome());
                printBoard(response.getBoard());
            }
            case "remove" -> {
                connection.send(
                        Opcode.OPCODE_REMOVE_PARTY_FINDER_COMMAND_VALUE,
                        RemovePartyFinderCommand.getDefaultInstance().toByteArray());
                RemovePartyFinderResponse response = RemovePartyFinderResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_REMOVE_PARTY_FINDER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload());
                session.note("tìm nhóm gỡ tin: removed=" + response.getRemoved());
                printBoard(response.getBoard());
            }
            default -> {
                connection.send(
                        Opcode.OPCODE_LIST_PARTY_FINDER_REQUEST_VALUE,
                        ListPartyFinderRequest.getDefaultInstance().toByteArray());
                printBoard(ListPartyFinderResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_LIST_PARTY_FINDER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload()));
            }
        }
    }

    private void printBoard(ListPartyFinderResponse board) {
        List<PartyFinderListingView> listings = board.getListingsList();
        StringBuilder line = new StringBuilder("bảng tìm nhóm (" + listings.size() + " tin):");
        for (PartyFinderListingView listing : listings) {
            line.append(String.format(
                    Locale.ROOT,
                    " | %s#%d %s từLv%d còn%ds",
                    listing.getLeaderName(),
                    listing.getLeaderCharacterId(),
                    listing.getGoal(),
                    listing.getMinLevel(),
                    listing.getExpiresInSeconds()));
        }
        session.note(line.toString());
    }
}
