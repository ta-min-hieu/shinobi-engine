package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.domain.DismantlePort;
import dev.kage.inventory.domain.ItemHistoryPort;

/** API phân giải hàng loạt (ADR-232) và lịch sử cường hoá (ADR-233). */
public final class ItemHistoryFacade {

    /** Số món tối đa một lần phân giải hàng loạt — chặn một lệnh thành hàng trăm transaction. */
    public static final int MAX_BULK_DISMANTLE = 40;

    /** Số dòng lịch sử cường hoá trả về. */
    public static final int HISTORY_LIMIT = 20;

    /** Không có gì (composition test). */
    public static final ItemHistoryFacade DISABLED = new ItemHistoryFacade(new ItemHistoryPort() {
        @Override
        public List<Long> dismantleCandidates(long characterId, int maxRequiredLevel) {
            return List.of();
        }

        @Override
        public List<EnhanceRecord> enhanceHistory(long characterId, int limit) {
            return List.of();
        }
    });

    /** Tổng thu về của một lần phân giải hàng loạt. */
    public record BulkDismantle(int dismantled, long gold, int herbs) {}

    private final ItemHistoryPort port;

    /** Dựng ở composition root. */
    public ItemHistoryFacade(ItemHistoryPort port) {
        this.port = port;
    }

    /**
     * ADR-232 — phân giải mọi ứng viên cấp yêu cầu ≤ {@code maxRequiredLevel} (tối đa {@value #MAX_BULK_DISMANTLE}
     * món), mỗi món đi đúng đường phân giải từng món ({@code dismantle}) — mọi luật cũ giữ nguyên, món nào bị từ chối
     * thì bỏ qua.
     */
    public BulkDismantle dismantleAll(long characterId, int maxRequiredLevel, DismantleFacade dismantle) {
        int count = 0;
        long gold = 0L;
        int herbs = 0;
        for (long itemId : port.dismantleCandidates(characterId, maxRequiredLevel)) {
            if (count >= MAX_BULK_DISMANTLE) {
                break;
            }
            DismantlePort.Result result = dismantle.dismantle(characterId, itemId);
            if (result.outcome() == DismantlePort.Outcome.OK) {
                count++;
                gold += result.gold();
                herbs += result.herbs();
            }
        }
        return new BulkDismantle(count, gold, herbs);
    }

    /** ADR-233 — {@value #HISTORY_LIMIT} lần cường hoá gần nhất. */
    public List<ItemHistoryPort.EnhanceRecord> enhanceHistory(long characterId) {
        return port.enhanceHistory(characterId, HISTORY_LIMIT);
    }
}
