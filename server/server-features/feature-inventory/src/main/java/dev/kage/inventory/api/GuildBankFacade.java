package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.domain.StoredItem;

/**
 * Kho bang (ADR-198) — cùng luật gửi / rút với kho tông môn ({@link StorageUseCase}), khác nơi lưu (theo bang). Chạm
 * DB — Virtual Thread. Ai được gửi / rút (thành viên / bang chủ + phó) do gateway kiểm.
 */
public final class GuildBankFacade {

    /** Composition không có kho bang (test): kho trống, gửi / rút trả {@code NOT_FOUND}. */
    public static final GuildBankFacade DISABLED = new GuildBankFacade(StorageUseCase.unavailable());

    private final StorageUseCase useCase;

    /** Dựng ở composition root với một {@link StorageUseCase} cắm adapter kho theo bang. */
    public GuildBankFacade(StorageUseCase useCase) {
        this.useCase = useCase;
    }

    /** Kho của bang {@code characterId} đang ở (rỗng nếu chưa vào bang). */
    public List<StoredItem> list(long characterId) {
        return useCase.list(characterId);
    }

    /** Gửi một món từ túi {@code characterId} vào kho bang. */
    public StorageUseCase.Result deposit(long characterId, long inventoryItemId, int quantity) {
        return useCase.deposit(characterId, inventoryItemId, quantity);
    }

    /** Rút một dòng kho bang vào túi {@code characterId}. */
    public StorageUseCase.Result withdraw(long characterId, long storedItemId, int quantity) {
        return useCase.withdraw(characterId, storedItemId, quantity);
    }
}
