package dev.kage.party.application;

import dev.kage.party.domain.PartyPort;

/**
 * Rời party — dùng chung cho CẢ leader lẫn member thường (khác feature-guild tách riêng
 * Leave/Disband). Chuyển leader tự động / giải tán party rỗng đều xử lý nguyên tử trong
 * {@link PartyPort#leaveParty}, use case này chỉ chuyển tiếp kết quả.
 */
public final class LeavePartyUseCase {

    private final PartyPort port;

    public LeavePartyUseCase(PartyPort port) {
        this.port = port;
    }

    public Result execute(long characterId) {
        return switch (port.leaveParty(characterId)) {
            case OK -> Result.OK;
            case NOT_IN_PARTY -> Result.NOT_IN_PARTY;
        };
    }

    public enum Result {
        OK,
        NOT_IN_PARTY
    }
}
