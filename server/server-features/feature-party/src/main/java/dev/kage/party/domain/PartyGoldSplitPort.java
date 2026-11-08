package dev.kage.party.domain;

import java.time.Instant;
import java.util.List;

/**
 * Chia vàng cho nhóm (ADR-278): trừ người chia, cộng đều cho từng người nhận trong MỘT transaction, mỗi bên một dòng sổ
 * vàng + một dòng audit. Việc chọn ai được nhận (đồng đội online cùng World) do lớp gọi quyết định.
 */
public interface PartyGoldSplitPort {

    /** Kết quả một lần chia. */
    enum Outcome {
        OK,
        NOT_ENOUGH_GOLD
    }

    /** Kết quả + vàng còn lại của người chia (0 khi không đủ). */
    record Split(Outcome outcome, long goldAfter) {}

    /** Trừ {@code each × recipients.size()} của {@code giverId}, cộng {@code each} cho từng người nhận. */
    Split split(long giverId, List<Long> recipients, long each, Instant at);

    /** Không có DB (test composition). */
    PartyGoldSplitPort DISABLED = (giverId, recipients, each, at) -> new Split(Outcome.NOT_ENOUGH_GOLD, 0L);
}
