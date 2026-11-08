package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;

import dev.kage.character.domain.Character;
import dev.kage.guild.api.GuildFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.GuildMemberOnlineEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RespondReadyCheckCommand;
import dev.kage.protocol.RespondReadyCheckOutcome;
import dev.kage.protocol.RespondReadyCheckResponse;
import dev.kage.protocol.RollDiceCommand;
import dev.kage.protocol.RollDiceOutcome;
import dev.kage.protocol.RollDiceResponse;
import dev.kage.protocol.StartPartyCountdownCommand;
import dev.kage.protocol.StartPartyCountdownOutcome;
import dev.kage.protocol.StartPartyCountdownResponse;
import dev.kage.protocol.StartReadyCheckOutcome;
import dev.kage.protocol.StartReadyCheckResponse;

/**
 * Lớp lệnh lô 42 (ADR-263…) — MỘT thành phần của {@link Lot41Commands}. Gọi trên Virtual Thread. Trạng thái nằm ở
 * {@link PartySignals} của registry (record này không trạng thái).
 */
record Lot42Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 43 (ADR-271…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot43Commands lot43() {
        return new Lot43Commands(gateway, registry);
    }

    /** ADR-263 — trưởng nhóm mở kiểm tra sẵn sàng; mọi thành viên đang online nhận lời mời trong chat. */
    StartReadyCheckResponse startReadyCheck(long characterId) {
        StartReadyCheckResponse.Builder response =
                StartReadyCheckResponse.newBuilder().setSeconds(PartySignals.READY_CHECK_SECONDS);
        Optional<PartyFacade.MyPartyView> party = party().findMyParty(characterId);
        if (party.isEmpty() || !party.get().isLeader()) {
            return response.setOutcome(StartReadyCheckOutcome.START_READY_CHECK_OUTCOME_NOT_LEADER)
                    .build();
        }
        PartySignals.StartOutcome outcome =
                registry.partySignals.startReadyCheck(party.get().partyId(), characterId, audience(characterId));
        return response.setOutcome(
                        outcome == PartySignals.StartOutcome.OK
                                ? StartReadyCheckOutcome.START_READY_CHECK_OUTCOME_OK
                                : StartReadyCheckOutcome.START_READY_CHECK_OUTCOME_ALREADY_ACTIVE)
                .build();
    }

    /** ADR-263 — thành viên trả lời sẵn sàng / chưa. */
    RespondReadyCheckResponse respondReadyCheck(long characterId, RespondReadyCheckCommand command) {
        boolean recorded = party().findMyParty(characterId)
                .map(view -> registry.partySignals.respondReadyCheck(view.partyId(), characterId, command.getReady()))
                .orElse(false);
        return RespondReadyCheckResponse.newBuilder()
                .setOutcome(
                        recorded
                                ? RespondReadyCheckOutcome.RESPOND_READY_CHECK_OUTCOME_OK
                                : RespondReadyCheckOutcome.RESPOND_READY_CHECK_OUTCOME_NO_CHECK)
                .build();
    }

    /** ADR-264 — trưởng nhóm đếm ngược 3–10 giây cho cả nhóm. */
    StartPartyCountdownResponse startCountdown(long characterId, StartPartyCountdownCommand command) {
        StartPartyCountdownResponse.Builder response = StartPartyCountdownResponse.newBuilder();
        Optional<PartyFacade.MyPartyView> party = party().findMyParty(characterId);
        if (party.isEmpty() || !party.get().isLeader()) {
            return response.setOutcome(StartPartyCountdownOutcome.START_PARTY_COUNTDOWN_OUTCOME_NOT_LEADER)
                    .build();
        }
        if (!PartySignals.validCountdown(command.getSeconds())) {
            return response.setOutcome(StartPartyCountdownOutcome.START_PARTY_COUNTDOWN_OUTCOME_INVALID_SECONDS)
                    .build();
        }
        PartySignals.StartOutcome outcome = registry.partySignals.startCountdown(
                party.get().partyId(), command.getSeconds(), characterId, audience(characterId));
        return response.setOutcome(
                        outcome == PartySignals.StartOutcome.OK
                                ? StartPartyCountdownOutcome.START_PARTY_COUNTDOWN_OUTCOME_OK
                                : StartPartyCountdownOutcome.START_PARTY_COUNTDOWN_OUTCOME_ALREADY_ACTIVE)
                .build();
    }

    /** ADR-265 — gieo 1..max; kết quả tới cả nhóm đang online (không có nhóm ⇒ chỉ mình). */
    RollDiceResponse rollDice(long characterId, RollDiceCommand command) {
        RollDiceResponse.Builder response = RollDiceResponse.newBuilder();
        Optional<Integer> max = PartySignals.diceMax(command.getMax());
        if (max.isEmpty()) {
            return response.setOutcome(RollDiceOutcome.ROLL_DICE_OUTCOME_INVALID_MAX)
                    .build();
        }
        Optional<Integer> value = registry.partySignals.rollDice(characterId, max.get());
        if (value.isEmpty()) {
            return response.setOutcome(RollDiceOutcome.ROLL_DICE_OUTCOME_RATE_LIMITED)
                    .setMax(max.get())
                    .build();
        }
        byte[] line = PartySignals.line(PartySignals.diceText(nameOf(characterId), value.get(), max.get()));
        List<Long> members = onlineMembers(characterId);
        for (long member : members.isEmpty() ? List.of(characterId) : members) {
            sendChat(member, line);
        }
        return response.setOutcome(RollDiceOutcome.ROLL_DICE_OUTCOME_OK)
                .setValue(value.get())
                .setMax(max.get())
                .build();
    }

    /**
     * ADR-266 — báo các thành viên KHÁC đang online của bang rằng {@code characterId} vừa vào game. Việc phụ của lần
     * vào world (người gọi bắt lỗi). Không vào bang ⇒ không làm gì.
     */
    void announceGuildOnline(long characterId) {
        List<GuildFacade.MemberView> members =
                gateway.contentFacades().socialFacades().guildFacade().listMembers(characterId);
        if (members.isEmpty()) {
            return;
        }
        byte[] event = GuildMemberOnlineEvent.newBuilder()
                .setCharacterId(characterId)
                .setName(nameOf(characterId))
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : members) {
            if (member.characterId() != characterId) {
                registry.sendToCharacterIfOnline(
                        member.characterId(), Opcode.OPCODE_GUILD_MEMBER_ONLINE_EVENT_VALUE, 0, event);
            }
        }
    }

    private PartySignals.Audience audience(long characterId) {
        return new PartySignals.Audience(onlineMembers(characterId), this::nameOf, this::sendChat);
    }

    /** Thành viên nhóm của {@code characterId} đang online (kể cả chính mình); rỗng khi không có nhóm. */
    private List<Long> onlineMembers(long characterId) {
        return party().listMembers(characterId).stream()
                .map(PartyFacade.MemberView::characterId)
                .filter(registry::isOnline)
                .toList();
    }

    private void sendChat(long characterId, byte[] chatEvent) {
        registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, chatEvent);
    }

    private String nameOf(long characterId) {
        return gateway.characterFacade()
                .findById(characterId)
                .map(Character::name)
                .orElse("?");
    }

    private PartyFacade party() {
        return gateway.contentFacades().socialFacades().partyFacade();
    }
}
