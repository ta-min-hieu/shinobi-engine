package dev.kage.server.gateway;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

import dev.kage.character.domain.Character;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.api.PartyGoldSplitFacade;
import dev.kage.protocol.GetBagValueResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SplitPartyGoldCommand;
import dev.kage.protocol.SplitPartyGoldOutcome;
import dev.kage.protocol.SplitPartyGoldResponse;
import dev.kage.server.world.WorldFacade;
import dev.kage.shop.domain.ShopListing;

/** Lớp lệnh lô 43 (ADR-271…) — MỘT thành phần của {@link Lot42Commands}. Gọi trên Virtual Thread. */
record Lot43Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 44 (ADR-279…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot44Commands lot44() {
        return new Lot44Commands(gateway, registry);
    }

    /**
     * ADR-278 — chia đều {@code amount} cho đồng đội ĐANG online ở CÙNG World với người chia (không gồm người chia).
     * Phần dư ở lại người chia. OK ⇒ báo mỗi người nhận một dòng chat hệ thống.
     *
     * @param worldOf World một nhân vật đang thật sự ở (hầm ngục / đấu trường / world chính)
     */
    SplitPartyGoldResponse splitGold(
            long characterId, SplitPartyGoldCommand command, LongFunction<Optional<WorldFacade>> worldOf) {
        SplitPartyGoldResponse.Builder response = SplitPartyGoldResponse.newBuilder();
        PartyFacade party = gateway.contentFacades().socialFacades().partyFacade();
        if (party.findMyParty(characterId).isEmpty()) {
            return response.setOutcome(SplitPartyGoldOutcome.SPLIT_PARTY_GOLD_OUTCOME_NOT_IN_PARTY)
                    .build();
        }
        Optional<WorldFacade> mine = worldOf.apply(characterId);
        List<Long> recipients = party.listMembers(characterId).stream()
                .map(PartyFacade.MemberView::characterId)
                .filter(id -> id != characterId && registry.isOnline(id))
                .filter(id -> mine.isPresent() && worldOf.apply(id).equals(mine))
                .toList();
        PartyGoldSplitFacade.Result result =
                facade().split(characterId, recipients, command.getAmount(), Instant.now());
        response.setOutcome(SplitPartyGoldOutcome.valueOf(
                "SPLIT_PARTY_GOLD_OUTCOME_" + result.outcome().name()));
        if (result.outcome() != PartyGoldSplitFacade.Outcome.OK) {
            return response.build();
        }
        byte[] notice = PartySignals.line(receivedText(nameOf(characterId), result.each()));
        for (long recipient : recipients) {
            registry.sendToCharacterIfOnline(recipient, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, notice);
        }
        return response.setEach(result.each())
                .setRecipients(recipients.size())
                .setGoldAfter(result.goldAfter())
                .build();
    }

    /**
     * ADR-274 — tổng giá bán cho cửa hàng của mọi món trong túi: giá {@code shop_item.sell_price} × số lượng (cộng
     * bão hoà, không tràn). Món không có trong bảng giá cửa hàng (không bán được cho NPC) đếm riêng, không cộng.
     */
    GetBagValueResponse bagValue(long characterId) {
        Map<Long, Long> sellPrices = new HashMap<>();
        for (ShopListing listing : gateway.contentFacades().shopFacade().listItems()) {
            sellPrices.put(listing.itemTemplateId(), listing.sellPrice());
        }
        long total = 0L;
        int priced = 0;
        int unpriced = 0;
        for (ItemStack stack :
                gateway.inventoryFacade().listInventory(characterId).inventory().items()) {
            long price = sellPrices.getOrDefault(stack.itemTemplateId(), 0L);
            if (price <= 0L) {
                unpriced++;
                continue;
            }
            priced++;
            total = saturatingAdd(total, saturatingMultiply(price, stack.quantity()));
        }
        return GetBagValueResponse.newBuilder()
                .setTotalGold(total)
                .setPricedStacks(priced)
                .setUnpricedStacks(unpriced)
                .build();
    }

    /** Cộng không tràn. Tĩnh để test. */
    static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /** Nhân không tràn (hai số không âm). Tĩnh để test. */
    static long saturatingMultiply(long a, long b) {
        return b != 0 && a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /** Dòng báo người nhận. Tĩnh để test. */
    static String receivedText(String giver, long each) {
        return giver + " chia cho bạn " + each + " linh thạch. / " + giver + " shared " + each + " gold with you.";
    }

    private String nameOf(long characterId) {
        return gateway.characterFacade()
                .findById(characterId)
                .map(Character::name)
                .orElse("?");
    }

    private PartyGoldSplitFacade facade() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .partyGoldSplit();
    }
}
