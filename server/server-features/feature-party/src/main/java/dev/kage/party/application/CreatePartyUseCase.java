package dev.kage.party.application;

import dev.kage.party.domain.DuplicatePartyMembershipException;
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
        try {
            return new Result(Outcome.OK, port.createParty(characterId));
        } catch (DuplicatePartyMembershipException e) {
            // findMembershipOf ở trên rồi createParty không atomic — 2 lệnh của CÙNG 1 nhân vật (tạo
            // 2 lần, hoặc tạo trong lúc chấp nhận lời mời) có thể cùng vượt qua check đó. DB (UNIQUE
            // party_member.character_id) chặn ở createParty; dịch về outcome nghiệp vụ thay vì để
            // exception thành error.server_internal ở gateway.
            return new Result(Outcome.ALREADY_IN_PARTY, null);
        }
    }

    public enum Outcome {
        OK,
        ALREADY_IN_PARTY
    }

    public record Result(Outcome outcome, Party party) {}
}
