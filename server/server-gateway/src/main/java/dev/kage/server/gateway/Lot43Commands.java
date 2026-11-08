package dev.kage.server.gateway;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

import dev.kage.character.domain.Character;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.api.PartyGoldSplitFacade;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SplitPartyGoldCommand;
import dev.kage.protocol.SplitPartyGoldOutcome;
import dev.kage.protocol.SplitPartyGoldResponse;
import dev.kage.server.world.WorldFacade;

/** Lớp lệnh lô 43 (ADR-271…) — MỘT thành phần của {@link Lot42Commands}. Gọi trên Virtual Thread. */
record Lot43Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

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
