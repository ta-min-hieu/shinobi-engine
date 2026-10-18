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
import dev.kage.protocol.ListStorageResponse;
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
        OUTCOMES.put(StorageUseCase.Outcome.STORAGE_FULL, StorageOutcome.STORAGE_OUTCOME_STORAGE_FULL);
        OUTCOMES.put(StorageUseCase.Outcome.BAG_FULL, StorageOutcome.STORAGE_OUTCOME_BAG_FULL);
    }

    private final InventoryFacade inventory;
    private final NpcProximity proximity;

    SectStorageCommands(InventoryFacade inventory, NpcProximity proximity) {
        this.inventory = inventory;
        this.proximity = proximity;
    }

    ListStorageResponse list(long characterId) {
        return ListStorageResponse.newBuilder()
                .addAllItems(views(inventory.listStorage(characterId)))
                .setCapacity(StoragePolicy.CAPACITY)
                .build();
    }

    DepositItemResponse deposit(long characterId, DepositItemCommand command) {
        if (!nearKeeper(characterId)) {
            return DepositItemResponse.newBuilder()
                    .setOutcome(StorageOutcome.STORAGE_OUTCOME_TOO_FAR)
                    .build();
        }
        StorageUseCase.Result result =
                inventory.depositToStorage(characterId, command.getInventoryItemId(), command.getQuantity());
        return DepositItemResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .build();
    }

    WithdrawItemResponse withdraw(long characterId, WithdrawItemCommand command) {
        if (!nearKeeper(characterId)) {
            return WithdrawItemResponse.newBuilder()
                    .setOutcome(StorageOutcome.STORAGE_OUTCOME_TOO_FAR)
                    .build();
        }
        StorageUseCase.Result result =
                inventory.withdrawFromStorage(characterId, command.getStoredItemId(), command.getQuantity());
        return WithdrawItemResponse.newBuilder()
                .setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .build();
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
