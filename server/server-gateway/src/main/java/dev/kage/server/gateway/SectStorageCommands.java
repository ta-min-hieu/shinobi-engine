package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.guild.api.GuildFacade;
import dev.kage.inventory.api.AccountStorageFacade;
import dev.kage.inventory.api.GuildBankFacade;
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
 * Ba lệnh kho đồ (ADR-061) cho ba kho — tông môn, chung tài khoản (ADR-197), bang (ADR-198) — chọn bằng {@code
 * storage_kind}. Chạy trên Virtual Thread (chạm DB). Ai được làm gì được kiểm Ở ĐÂY, trước khi gọi feature (D3, Điều
 * 15): kho tông môn / tài khoản phải đứng gần người giữ kho; kho bang thì phải ở trong bang, rút chỉ bang chủ / phó.
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

    /** Ba kho phụ (ngoài kho tông môn nằm trong {@link InventoryFacade}) và thông tin bang để kiểm quyền. */
    record Vaults(AccountStorageFacade account, GuildBankFacade guild, GuildFacade guilds) {}

    private final InventoryFacade inventory;
    private final NpcProximity proximity;
    private final Vaults vaults;

    SectStorageCommands(InventoryFacade inventory, NpcProximity proximity, Vaults vaults) {
        this.inventory = inventory;
        this.proximity = proximity;
        this.vaults = vaults;
    }

    /** Dựng từ composition — một dòng trong constructor handler (trần {@code MethodLength}). */
    static SectStorageCommands of(InventoryFacade inventory, NpcProximity proximity, GatewayFacades facades) {
        Lot33Facades lot33 = facades.progression().expansion().lot33();
        return new SectStorageCommands(
                inventory,
                proximity,
                new Vaults(
                        lot33.accountStorage(),
                        lot33.guildBank(),
                        facades.contentFacades().socialFacades().guildFacade()));
    }

    /** Kho tông môn (lối cũ: hội thoại tự mở, phím làm mới). */
    ListStorageResponse list(long characterId) {
        return list(characterId, ListStorageRequest.getDefaultInstance());
    }

    /** {@code storage_kind} chọn kho. Xem không cần đứng gần người giữ kho; kho bang rỗng khi chưa vào bang. */
    ListStorageResponse list(long characterId, ListStorageRequest request) {
        StorageKind kind = known(request.getStorageKind());
        List<StoredItem> items =
                switch (kind) {
                    case STORAGE_KIND_ACCOUNT -> vaults.account().list(characterId);
                    case STORAGE_KIND_GUILD -> vaults.guild().list(characterId);
                    default -> inventory.listStorage(characterId);
                };
        return ListStorageResponse.newBuilder()
                .addAllItems(views(items))
                .setCapacity(StoragePolicy.CAPACITY)
                .setStorageKind(kind)
                .build();
    }

    DepositItemResponse deposit(long characterId, DepositItemCommand command) {
        StorageKind kind = known(command.getStorageKind());
        DepositItemResponse.Builder response = DepositItemResponse.newBuilder().setStorageKind(kind);
        Optional<StorageOutcome> refused = refuse(characterId, kind, false);
        if (refused.isPresent()) {
            return response.setOutcome(refused.get()).build();
        }
        long itemId = command.getInventoryItemId();
        int quantity = command.getQuantity();
        StorageUseCase.Result result =
                switch (kind) {
                    case STORAGE_KIND_ACCOUNT -> vaults.account().deposit(characterId, itemId, quantity);
                    case STORAGE_KIND_GUILD -> vaults.guild().deposit(characterId, itemId, quantity);
                    default -> inventory.depositToStorage(characterId, itemId, quantity);
                };
        return response.setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .build();
    }

    WithdrawItemResponse withdraw(long characterId, WithdrawItemCommand command) {
        StorageKind kind = known(command.getStorageKind());
        WithdrawItemResponse.Builder response =
                WithdrawItemResponse.newBuilder().setStorageKind(kind);
        Optional<StorageOutcome> refused = refuse(characterId, kind, true);
        if (refused.isPresent()) {
            return response.setOutcome(refused.get()).build();
        }
        long rowId = command.getStoredItemId();
        int quantity = command.getQuantity();
        StorageUseCase.Result result =
                switch (kind) {
                    case STORAGE_KIND_ACCOUNT -> vaults.account().withdraw(characterId, rowId, quantity);
                    case STORAGE_KIND_GUILD -> vaults.guild().withdraw(characterId, rowId, quantity);
                    default -> inventory.withdrawFromStorage(characterId, rowId, quantity);
                };
        return response.setOutcome(OUTCOMES.get(result.outcome()))
                .addAllItems(views(result.storage()))
                .setCapacity(StoragePolicy.CAPACITY)
                .build();
    }

    /** Lý do không cho chạm kho (trước khi gọi feature); rỗng = cho. */
    private Optional<StorageOutcome> refuse(long characterId, StorageKind kind, boolean withdraw) {
        if (kind != StorageKind.STORAGE_KIND_GUILD) {
            return nearKeeper(characterId) ? Optional.empty() : Optional.of(StorageOutcome.STORAGE_OUTCOME_TOO_FAR);
        }
        Optional<GuildFacade.MyGuildView> guild = vaults.guilds().findMyGuild(characterId);
        if (guild.isEmpty()) {
            return Optional.of(StorageOutcome.STORAGE_OUTCOME_NOT_IN_GUILD);
        }
        boolean officer = guild.get().isLeader() || guild.get().isViceLeader();
        return withdraw && !officer ? Optional.of(StorageOutcome.STORAGE_OUTCOME_NO_PERMISSION) : Optional.empty();
    }

    /** Giá trị lạ (client mới hơn / gói hỏng) ⇒ kho tông môn — không bao giờ đặt UNRECOGNIZED vào response. */
    private static StorageKind known(StorageKind kind) {
        return kind == StorageKind.STORAGE_KIND_ACCOUNT || kind == StorageKind.STORAGE_KIND_GUILD
                ? kind
                : StorageKind.STORAGE_KIND_SECT;
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
