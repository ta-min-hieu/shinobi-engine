package dev.kage.character.application;

import java.util.Optional;

import dev.kage.character.domain.RebirthOutcome;
import dev.kage.character.domain.RebirthPolicy;
import dev.kage.character.domain.RebirthPort;
import dev.kage.character.domain.TransactionRunner;

/**
 * Độ Kiếp Chuyển Thế (ADR-089): ở cấp tối đa, trùng tu từ cấp 1 — đọc + kiểm + ghi dưới khoá dòng nhân vật (hai lệnh
 * song song không chuyển thế hai lần). Pháp khí, danh hiệu, bậc chiêu, linh căn giữ nguyên.
 */
public final class RebirthUseCase {

    /**
     * Kết quả kèm số lần chuyển thế SAU lệnh.
     *
     * @param rebirthCount số lần đã chuyển thế sau lệnh (không đổi nếu từ chối)
     */
    public record Result(RebirthOutcome outcome, int rebirthCount) {}

    private final RebirthPort port;
    private final TransactionRunner tx;

    public RebirthUseCase(RebirthPort port, TransactionRunner tx) {
        this.port = port;
        this.tx = tx;
    }

    public Result rebirth(long characterId) {
        Result[] result = {new Result(RebirthOutcome.NOT_FOUND, 0)};
        tx.inTransaction(t -> {
            Optional<RebirthPort.Progress> progress = port.lock(t, characterId);
            if (progress.isEmpty()) {
                return;
            }
            Optional<RebirthOutcome> refusal =
                    RebirthPolicy.refusal(progress.get().level(), progress.get().rebirthCount());
            if (refusal.isPresent()) {
                result[0] = new Result(refusal.get(), progress.get().rebirthCount());
                return;
            }
            port.rebirth(t, characterId);
            result[0] = new Result(RebirthOutcome.OK, progress.get().rebirthCount() + 1);
        });
        return result[0];
    }

    public int rebirthCountOf(long characterId) {
        return port.rebirthCountOf(characterId);
    }
}
