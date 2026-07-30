package dev.kage.party.application;

import java.util.Optional;

import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;

/** Thông tin party hiện tại của {@code characterId} — rỗng nếu chưa vào party nào. */
public final class FindMyPartyUseCase {

    private final PartyPort port;

    public FindMyPartyUseCase(PartyPort port) {
        this.port = port;
    }

    public Optional<Result> execute(long characterId) {
        return port.findMembershipOf(characterId).map(member -> new Result(member.partyId(), member.role()));
    }

    public record Result(long partyId, PartyRole myRole) {}
}
