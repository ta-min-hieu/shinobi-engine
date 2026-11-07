package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.StoredItem;

/**
 * Kho chung tài khoản (ADR-197) — cùng luật gửi / rút với kho tông môn ({@link StorageUseCase}), khác nơi lưu (theo tài
 * khoản). Chạm DB — Virtual Thread. Đứng gần người giữ kho do gateway kiểm.
 */
public final class AccountStorageFacade {

    /** Composition không có kho chung (test): kho trống, gửi / rút trả {@code NOT_FOUND}. */
    public static final AccountStorageFacade DISABLED = new AccountStorageFacade(StorageUseCase.unavailable());

    private final StorageUseCase useCase;

    /** Dựng ở composition root với một {@link StorageUseCase} cắm adapter kho theo tài khoản. */
    public AccountStorageFacade(StorageUseCase useCase) {
        this.useCase = useCase;
    }

    /** Kho chung của tài khoản sở hữu {@code characterId}. */
    public List<StoredItem> list(long characterId) {
        return useCase.list(characterId);
    }

    /** Gửi một món từ túi {@code characterId} vào kho chung. */
    public StorageUseCase.Result deposit(long characterId, long inventoryItemId, int quantity) {
        return useCase.deposit(characterId, inventoryItemId, quantity);
    }

    /** Rút một dòng kho chung vào túi {@code characterId}. */
    public StorageUseCase.Result withdraw(long characterId, long storedItemId, int quantity) {
        return useCase.withdraw(characterId, storedItemId, quantity);
    }
}
