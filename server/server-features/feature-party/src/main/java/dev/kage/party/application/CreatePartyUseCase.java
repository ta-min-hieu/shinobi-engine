package dev.kage.party.application;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyPort;

/** Tạo party mới — người tạo tự động thành LEADER. Chỉ tạo được khi chưa ở party nào khác. */
public final class CreatePartyUseCase {

    private final PartyPort port;

    public CreatePartyUseCase(PartyPort port) {
        this.port = port;
    }

    public Result execute(long characterId) {
        if (port.findMembershipOf(characterId).isPresent()) {
            return new Result(Outcome.ALREADY_IN_PARTY, null);
        }
        return new Result(Outcome.OK, port.createParty(characterId));
    }

    public enum Outcome { OK, ALREADY_IN_PARTY }

    public record Result(Outcome outcome, Party party) {
    }
}
