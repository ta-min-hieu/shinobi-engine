package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.StoragePolicy;
import dev.kage.inventory.domain.StoredItem;
import dev.kage.protocol.DepositItemCommand;
import dev.kage.protocol.DepositItemResponse;
import dev.kage.protocol.ListStorageRequest;
import dev.kage.protocol.ListStorageResponse;
import dev.kage.protocol.StorageKind;
import dev.kage.protocol.StorageOutcome;
import dev.kage.protocol.StoredItemView;
import dev.kage.protocol.WithdrawItemCommand;
import dev.kage.protocol.WithdrawItemResponse;

/**
 * Ba lệnh kho đồ tông môn (ADR-061) — tách khỏi {@code GatewayChannelHandler} cho gọn. Chạy trên Virtual
 * Thread (chạm DB). Đứng gần người giữ kho được kiểm Ở ĐÂY, trước khi gọi feature (D3, Điều 15).
 */
final class SectStorageCommands {

    private static final Map<StorageUseCase.Outcome, StorageOutcome> OUTCOMES =
            new EnumMap<>(StorageUseCase.Outcome.class);

    static {
        OUTCOMES.put(StorageUseCase.Outcome.OK, StorageOutcome.STORAGE_OUTCOME_OK);
        OUTCOMES.put(StorageUseCase.Outcome.NOT_FOUND, StorageOutcome.STORAGE_OUTCOME_NOT_FOUND);
        OUTCOMES.put(StorageUseCase.Outcome.INVALID_QUANTITY, StorageOutcome.STORAGE_OUTCOME_INVALID_QUANTITY);
        OUTCOMES.put(StorageUseCase.Outcome.EQUIPPED, StorageOutcome.STORAGE_OUTCOME_EQUIPPED);
        OUTCOMES.put(StorageUseCase.Outcome.ITEM_IN_TRADE, StorageOutcome.STORAGE_OUTCOME_ITEM_IN_TRADE);
        OUTCOMES.put(StorageUseCase.Outcome.SOCKETED, StorageOutcome.STORAGE_OUTCOME_SOCKETED);
        OUTCOMES.put(StorageUseCase.Outcome.LOCKED, StorageOutcome.STORAGE_OUTCOME_LOCKED);
        OUTCOMES.put(StorageUseCase.Outcome.STORAGE_FULL, StorageOutcome.STORAGE_OUTCOME_STORAGE_FULL);
        OUTCOMES.put(StorageUseCase.Outcome.BAG_FULL, StorageOutcome.STORAGE_OUTCOME_BAG_FULL);
    }

    private final InventoryFacade inventory;
    private final NpcProximity proximity;
    private final dev.kage.inventory.api.AccountStorageFacade accountStorage;

    SectStorageCommands(
            InventoryFacade inventory,
            NpcProximity proximity,
            dev.kage.inventory.api.AccountStorageFacade accountStorage) {
        this.inventory = inventory;
        this.proximity = proximity;
        this.accountStorage = accountStorage;
    }

    /** Dựng từ composition — một dòng trong constructor handler (trần {@code MethodLength}). */
    static SectStorageCommands of(InventoryFacade inventory, NpcProximity proximity, GatewayFacades facades) {
        return new SectStorageCommands(
                inventory, proximity, facades.progression().expansion().lot33().accountStorage());
    }

    /** Kho tông môn (lối cũ: hội thoại tự mở, phím làm mới). */
    ListStorageResponse list(long characterId) {
        return list(characterId, ListStorageRequest.getDefaultInstance());
    }

    /** ADR-197 — {@code storage_kind} chọn kho. Xem không cần đứng gần người giữ kho. */
    ListStorageResponse list(long characterId, ListStorageRequest request) {
        StorageKind kind = known(request.getStorageKind());
        boolean shared = kind == StorageKind.STORAGE_KIND_ACCOUNT;
        return ListStorageResponse.newBuilder()
                .addAllItems(views(shared ? accountStorage.list(characterId) : inventory.listStorage(characterId)))
                .setCapacity(StoragePolicy.CAPACITY)
                .setStorageKind(kind)
                .build();
    }

    DepositItemResponse deposit(long characterId, DepositItemCommand command) {
        StorageKind kind = known(command.getStorageKind());
        boolean shared = kind == StorageKind.STORAGE_KIND_ACCOUNT;
        if (!nearKeeper(characterId)) {
            return DepositItemResponse.newBuilder()
                    .setOutcome(StorageOutcome.STORAGE_OUTCOME_TOO_FAR)
                    .setStorageKind(kind)
                    .build();
        }
        StorageUseCase.Result result = shared
                ? accountStorage.deposit(characterId, command.getInventoryItemId(), command.getQuantity())
                : inventory.depositToStorage(characterId, command.getInventoryItemId(), command.getQuantity());
        return DepositItemResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .setStorageKind(kind)
                .build();
    }

    WithdrawItemResponse withdraw(long characterId, WithdrawItemCommand command) {
        StorageKind kind = known(command.getStorageKind());
        boolean shared = kind == StorageKind.STORAGE_KIND_ACCOUNT;
        if (!nearKeeper(characterId)) {
            return WithdrawItemResponse.newBuilder()
                    .setOutcome(StorageOutcome.STORAGE_OUTCOME_TOO_FAR)
                    .setStorageKind(kind)
                    .build();
        }
        StorageUseCase.Result result = shared
                ? accountStorage.withdraw(characterId, command.getStoredItemId(), command.getQuantity())
                : inventory.withdrawFromStorage(characterId, command.getStoredItemId(), command.getQuantity());
        return WithdrawItemResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .setStorageKind(kind)
                .build();
    }

    /** Giá trị lạ (client mới hơn / gói hỏng) ⇒ kho tông môn — không bao giờ đặt UNRECOGNIZED vào response. */
    private static StorageKind known(StorageKind kind) {
        return kind == StorageKind.STORAGE_KIND_ACCOUNT ? kind : StorageKind.STORAGE_KIND_SECT;
    }

    private boolean nearKeeper(long characterId) {
        return proximity.isWithinReach(characterId, StoragePolicy.KEEPER_NPC_CODE);
    }

    private static List<StoredItemView> views(List<StoredItem> items) {
        return items.stream()
                .map(item -> StoredItemView.newBuilder()
                        .setStoredItemId(item.id())
                        .setItemTemplateId(item.itemTemplateId())
                        .setQuantity(item.quantity())
                        .setEnhanceLevel(item.enhanceLevel())
                        .build())
                .toList();
    }
}
