package dev.kage.mail.application;

import dev.kage.mail.domain.MailRepositoryPort;

/**
 * Xoá thư — CẤM xoá thư CHƯA nhận mà còn gold/item đính kèm (mất thưởng âm thầm), phải nhận
 * ({@code ClaimMailUseCase}) trước. Thư không có gì đính kèm (thông báo thuần) xoá được ngay.
 */
public final class DeleteMailUseCase {

    private final MailRepositoryPort port;

    public DeleteMailUseCase(MailRepositoryPort port) {
        this.port = port;
    }

    public Result execute(long mailId, long characterId) {
        var mailOpt = port.findById(mailId);
        if (mailOpt.isEmpty() || mailOpt.get().recipientId() != characterId) {
            return Result.NOT_FOUND;
        }
        var mail = mailOpt.get();
        if (!mail.isClaimed() && mail.hasReward()) {
            return Result.MUST_CLAIM_FIRST;
        }
        port.delete(mailId, characterId);
        return Result.OK;
    }

    public enum Result { OK, NOT_FOUND, MUST_CLAIM_FIRST }
}
