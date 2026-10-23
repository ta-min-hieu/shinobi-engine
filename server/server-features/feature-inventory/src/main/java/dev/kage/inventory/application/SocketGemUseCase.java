package dev.kage.inventory.application;

import java.io.Serial;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.GemPolicy;
import dev.kage.inventory.domain.GemPort;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.IdempotencyKeyReusedException;
import dev.kage.inventory.domain.IdempotencyPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.TradeOfferPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Khảm một viên Linh Ngọc vào một trang bị (ADR-064 D3). Một transaction dưới khoá túi đồ: trừ phí → khoá
 * chống trùng → tiêu một viên ngọc → roll (có bảo hiểm) → ghi ô + bảo hiểm → audit. Nhánh từ chối không ghi
 * gì; khoá trùng ⇒ rollback trọn.
 */
public final class SocketGemUseCase {

    /** Kết quả. */
    public enum Outcome {
        SUCCESS,
        FAILED,
        /** Món đích hoặc viên ngọc không nằm trong túi người gửi. */
        NOT_FOUND,
        /** Món đích không phải trang bị. */
        NOT_EQUIPMENT,
        /** Món "ngọc" không phải Linh Ngọc. */
        NOT_A_GEM,
        INSUFFICIENT_GOLD,
        DUPLICATE_REQUEST,
        /**
         * Món đích đang được chào, hoặc không còn viên ngọc nào ngoài phần đang chào, trong một giao dịch còn mở
         * (docs/README.md dòng 82, theo tiền lệ cường hoá 2026-09-29). Gateway trả {@code error.item_in_trade},
         * không có giá trị {@code SocketGemOutcome} tương ứng.
         */
        ITEM_IN_TRADE
    }

    /**
     * Kết quả kèm bảo hiểm.
     *
     * @param failuresAfter số lần hỏng liên tiếp SAU lần này (0 sau khi thành công)
     */
    public record Result(Outcome outcome, int failuresAfter) {
        static Result of(Outcome outcome) {
            return new Result(outcome, 0);
        }
    }

    /** Gom port — trần {@code ParameterNumber}. */
    public record Ports(
            InventoryPort inventory,
            ItemTemplatePort templates,
            GemPort gems,
            GoldPort gold,
            TradeOfferPort tradeOffers) {}

    /** Khoá chống trùng + audit. */
    public record Records(IdempotencyPort idempotency, AuditPort audit) {}

    private final Ports ports;
    private final Records records;
    private final TransactionRunner tx;
    private final RandomGenerator random;

    public SocketGemUseCase(Ports ports, Records records, TransactionRunner tx, RandomGenerator random) {
        this.ports = ports;
        this.records = records;
        this.tx = tx;
        this.random = random;
    }

    /** Bản không có Linh Ngọc (composition test) — mọi lệnh {@code NOT_FOUND}, không ô khảm nào. */
    public static SocketGemUseCase unavailable() {
        return new SocketGemUseCase(
                new Ports(null, null, GemPort.NONE, null, null), new Records(null, null), null, null);
    }

    /** Ô khảm trong túi nhân vật — {@code inventory_item.id → item_template.id} của ngọc. */
    public Map<Long, Long> socketsOf(long characterId) {
        return ports.gems().socketsOf(characterId);
    }

    /** Chỉ số mỗi loại ngọc. */
    public Map<Long, EquipmentBonus> gemBonuses() {
        return ports.gems().gemBonuses();
    }

    public Result execute(long characterId, long targetItemId, long gemItemId, String idempotencyKey) {
        if (tx == null) {
            return Result.of(Outcome.NOT_FOUND);
        }
        AtomicReference<Result> result = new AtomicReference<>();
        Pick pick = new Pick(targetItemId, gemItemId);
        try {
            tx.inTransaction(t -> result.set(socketIn(t, characterId, pick, idempotencyKey)));
        } catch (Abort abort) {
            return Result.of(abort.outcome);
        } catch (IdempotencyKeyReusedException e) {
            return Result.of(Outcome.DUPLICATE_REQUEST);
        }
        return result.get();
    }

    /** Món đích + viên ngọc người chơi chọn. */
    private record Pick(long targetItemId, long gemItemId) {}

    private Result socketIn(Transaction t, long characterId, Pick pick, String idempotencyKey) {
        Inventory inventory = ports.inventory()
                .findByCharacterForUpdate(t, characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        ItemStack gem = validate(inventory, pick);
        rejectIfOffered(t, characterId, pick, gem);
        if (!ports.gold().deductGold(t, characterId, GemPolicy.SOCKET_FEE)) {
            throw new Abort(Outcome.INSUFFICIENT_GOLD);
        }
        records.idempotency().recordOrThrow(t, idempotencyKey);
        inventory.consumeOne(gem.id());
        ports.inventory().save(t, inventory);
        int failures = ports.gems().lockFailures(t, characterId);
        boolean success = GemPolicy.succeeds(failures, random.nextDouble());
        int failuresAfter = success ? 0 : failures + 1;
        if (success) {
            ports.gems().socket(t, pick.targetItemId(), gem.itemTemplateId());
        }
        ports.gems().setFailures(t, characterId, failuresAfter);
        records.audit().log(t, characterId, "GEM_SOCKET", detail(pick, gem.itemTemplateId(), success));
        return new Result(success ? Outcome.SUCCESS : Outcome.FAILED, failuresAfter);
    }

    /** Món đích là trang bị trong túi, viên ngọc là Linh Ngọc trong túi; trả viên ngọc. */
    private ItemStack validate(Inventory inventory, Pick pick) {
        Optional<ItemStack> target = inventory.findItem(pick.targetItemId());
        Optional<ItemStack> gem = inventory.findItem(pick.gemItemId());
        if (target.isEmpty() || gem.isEmpty()) {
            throw new Abort(Outcome.NOT_FOUND);
        }
        boolean equippable = ports.templates()
                .findById(target.get().itemTemplateId())
                .map(ItemTemplate::isEquippable)
                .orElse(false);
        if (!equippable) {
            throw new Abort(Outcome.NOT_EQUIPMENT);
        }
        if (!ports.gems().gemBonuses().containsKey(gem.get().itemTemplateId())) {
            throw new Abort(Outcome.NOT_A_GEM);
        }
        return gem.get();
    }

    /**
     * Khảm vào món đang chào đổi giá trị của nó SAU khi bên kia đã đồng ý; tiêu viên ngọc đang chào làm giao dịch
     * hỏng lúc xác nhận. Hỏi SAU khoá túi đồ (xem {@link TradeOfferPort}), TRƯỚC phí và khoá chống trùng: huỷ giao
     * dịch xong thử lại bằng chính khoá đó vẫn là cùng một lần khảm.
     */
    private void rejectIfOffered(Transaction t, long characterId, Pick pick, ItemStack gem) {
        boolean targetOffered = ports.tradeOffers().isOfferedInActiveTrade(t, characterId, pick.targetItemId());
        boolean noFreeGem = gem.quantity() - ports.tradeOffers().offeredQuantity(t, characterId, gem.id()) < 1;
        if (targetOffered || noFreeGem) {
            throw new Abort(Outcome.ITEM_IN_TRADE);
        }
    }

    private static String detail(Pick pick, long gemTemplateId, boolean success) {
        return "{\"targetItemId\":" + pick.targetItemId() + ",\"gemItemTemplateId\":" + gemTemplateId + ",\"success\":"
                + success + ",\"fee\":" + GemPolicy.SOCKET_FEE + "}";
    }

    /** Từ chối giữa transaction — ném để rollback (không "return sớm" sau khi đã ghi). */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
