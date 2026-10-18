package dev.kage.inventory.application;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.StoragePolicy;
import dev.kage.inventory.domain.StoragePort;
import dev.kage.inventory.domain.StoredItem;
import dev.kage.inventory.domain.TradeOfferPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Gửi/rút/liệt kê kho đồ tông môn (ADR-061). Mỗi lần chuyển là MỘT transaction: khoá túi đồ trước, rồi
 * dòng kho (D4) — xoá/giảm ở bảng này, chèn/cộng ở bảng kia, ghi audit. Nhánh từ chối không ghi gì.
 *
 * <p>Đứng gần NPC giữ kho là việc của gateway ({@code NpcProximity}) — feature này không biết vị trí.
 */
public final class StorageUseCase {

    /** Kết quả gửi/rút. */
    public enum Outcome {
        OK,
        /** Món/dòng không thuộc về người gửi lệnh (gộp "không tồn tại" — SECURITY.md §4). */
        NOT_FOUND,
        /** Số lượng ngoài {@code 1..số đang có}. */
        INVALID_QUANTITY,
        /** Món đang mặc — gỡ ra trước. */
        EQUIPPED,
        /** Phần muốn gửi đang được chào trong một phiên giao dịch (dòng 80). */
        ITEM_IN_TRADE,
        STORAGE_FULL,
        BAG_FULL
    }

    /** Kết quả kèm kho SAU lệnh (rỗng nghĩa là kho trống, không phải "không biết"). */
    public record Result(Outcome outcome, List<StoredItem> storage) {
        public Result {
            storage = List.copyOf(storage);
        }
    }

    /** Gom port — trần {@code ParameterNumber} 4, cùng cách {@link UseItemUseCase.Ports}. */
    public record Ports(
            InventoryPort inventoryPort,
            EquipmentPort equipmentPort,
            ItemTemplatePort itemTemplatePort,
            TradeOfferPort tradeOfferPort) {}

    private final Ports ports;
    private final StoragePort storagePort;
    private final AuditPort auditPort;
    private final TransactionRunner tx;

    /** {@code false} = composition không có kho (test): kho luôn trống, gửi/rút trả {@link Outcome#NOT_FOUND}. */
    private final boolean available;

    public StorageUseCase(Ports ports, StoragePort storagePort, AuditPort auditPort, TransactionRunner tx) {
        this.ports = ports;
        this.storagePort = storagePort;
        this.auditPort = auditPort;
        this.tx = tx;
        this.available = storagePort != null;
    }

    /**
     * Bản không có kho (composition test) — kho luôn trống, gửi/rút trả {@link Outcome#NOT_FOUND}. KHÔNG
     * ném: fuzz opcode của gateway chạy trên composition này và coi mọi 500 là lỗi.
     */
    public static StorageUseCase unavailable() {
        return new StorageUseCase(null, null, null, null);
    }

    public List<StoredItem> list(long characterId) {
        return available ? storagePort.list(characterId) : List.of();
    }

    /** Gửi {@code quantity} cái của dòng túi {@code inventoryItemId} vào kho. */
    public Result deposit(long characterId, long inventoryItemId, int quantity) {
        if (!available) {
            return new Result(Outcome.NOT_FOUND, List.of());
        }
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        tx.inTransaction((Transaction t) -> outcome.set(depositIn(t, characterId, inventoryItemId, quantity)));
        return new Result(outcome.get(), storagePort.list(characterId));
    }

    /** Rút {@code quantity} cái của dòng kho {@code storedItemId} về túi. */
    public Result withdraw(long characterId, long storedItemId, int quantity) {
        if (!available) {
            return new Result(Outcome.NOT_FOUND, List.of());
        }
        AtomicReference<Outcome> outcome = new AtomicReference<>();
        tx.inTransaction((Transaction t) -> outcome.set(withdrawIn(t, characterId, storedItemId, quantity)));
        return new Result(outcome.get(), storagePort.list(characterId));
    }

    private Outcome depositIn(Transaction t, long characterId, long inventoryItemId, int quantity) {
        Inventory inventory = lockInventory(t, characterId);
        Optional<ItemStack> found = inventory.findItem(inventoryItemId);
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        ItemStack item = found.get();
        Optional<Outcome> refused = refuseDeposit(t, characterId, item, quantity);
        if (refused.isPresent()) {
            return refused.get();
        }
        List<StoredItem> storage = storagePort.listForUpdate(t, characterId);
        int maxStack = maxStackOf(item.itemTemplateId());
        Optional<StoredItem> mergeInto = storage.stream()
                .filter(row -> row.canMerge(item.itemTemplateId(), item.enhanceLevel(), quantity, maxStack))
                .findFirst();
        if (mergeInto.isEmpty() && storage.size() >= StoragePolicy.CAPACITY) {
            return Outcome.STORAGE_FULL;
        }
        for (int i = 0; i < quantity; i++) {
            inventory.consumeOne(inventoryItemId);
        }
        ports.inventoryPort().save(t, inventory);
        if (mergeInto.isPresent()) {
            storagePort.updateQuantity(t, mergeInto.get().id(), mergeInto.get().quantity() + quantity);
        } else {
            storagePort.insert(
                    t, characterId, new StoredItem(0L, item.itemTemplateId(), quantity, item.enhanceLevel()));
        }
        auditPort.log(t, characterId, "STORAGE_DEPOSIT", detail(item.itemTemplateId(), quantity, item.enhanceLevel()));
        return Outcome.OK;
    }

    /** Ba lý do món có trong túi vẫn không gửi được (D4) — tách khỏi {@link #depositIn} vì trần độ phức tạp. */
    private Optional<Outcome> refuseDeposit(Transaction t, long characterId, ItemStack item, int quantity) {
        if (quantity < 1 || quantity > item.quantity()) {
            return Optional.of(Outcome.INVALID_QUANTITY);
        }
        if (isEquipped(characterId, item.id())) {
            return Optional.of(Outcome.EQUIPPED);
        }
        if (item.quantity() - ports.tradeOfferPort().offeredQuantity(t, characterId, item.id()) < quantity) {
            return Optional.of(Outcome.ITEM_IN_TRADE);
        }
        return Optional.empty();
    }

    private Outcome withdrawIn(Transaction t, long characterId, long storedItemId, int quantity) {
        Inventory inventory = lockInventory(t, characterId);
        Optional<StoredItem> found = storagePort.listForUpdate(t, characterId).stream()
                .filter(row -> row.id() == storedItemId)
                .findFirst();
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        StoredItem row = found.get();
        if (quantity < 1 || quantity > row.quantity()) {
            return Outcome.INVALID_QUANTITY;
        }
        ItemStack stack = ItemStack.newItem(row.itemTemplateId(), quantity, row.enhanceLevel());
        if (inventory.add(stack, maxStackOf(row.itemTemplateId())) instanceof Inventory.Full) {
            return Outcome.BAG_FULL;
        }
        ports.inventoryPort().save(t, inventory);
        if (quantity == row.quantity()) {
            storagePort.delete(t, storedItemId);
        } else {
            storagePort.updateQuantity(t, storedItemId, row.quantity() - quantity);
        }
        auditPort.log(t, characterId, "STORAGE_WITHDRAW", detail(row.itemTemplateId(), quantity, row.enhanceLevel()));
        return Outcome.OK;
    }

    private Inventory lockInventory(Transaction t, long characterId) {
        return ports.inventoryPort()
                .findByCharacterForUpdate(t, characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
    }

    private boolean isEquipped(long characterId, long inventoryItemId) {
        return ports.equipmentPort()
                .findByCharacter(characterId)
                .map(Equipment::slots)
                .map(slots -> slots.containsValue(inventoryItemId))
                .orElse(false);
    }

    private int maxStackOf(long itemTemplateId) {
        return ports.itemTemplatePort()
                .findById(itemTemplateId)
                .map(ItemTemplate::maxStack)
                .orElse(1);
    }

    private static String detail(long itemTemplateId, int quantity, int enhanceLevel) {
        return "{\"itemTemplateId\":" + itemTemplateId + ",\"quantity\":" + quantity + ",\"enhanceLevel\":"
                + enhanceLevel + "}";
    }
}
