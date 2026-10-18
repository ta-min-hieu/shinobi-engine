package dev.kage.inventory.api;

import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.StorageUseCase;
import dev.kage.inventory.application.UseItemUseCase;

/**
 * Gộp 2 UseCase "thao tác lên MỘT món đang nằm trong túi" — cường hoá và dùng.
 *
 * <p>Lý do gộp giống {@link ItemAcquisitionUseCases}: {@link InventoryFacade} đã chạm giới hạn 4
 * tham số (CODING-STANDARDS §4, Checkstyle {@code ParameterNumber}) từ Phase 5, nên UseCase mới của
 * ADR-046 D5 phải vào chung một record với {@code EnhanceItemUseCase} thay vì thêm tham số thứ 5.
 */
public record ItemActionUseCases(
        EnhanceItemUseCase enhanceItemUseCase, UseItemUseCase useItemUseCase, StorageUseCase storageUseCase) {

    /** Composition không có kho đồ (test) — gọi tới kho là lỗi lập trình. */
    public ItemActionUseCases(EnhanceItemUseCase enhanceItemUseCase, UseItemUseCase useItemUseCase) {
        this(enhanceItemUseCase, useItemUseCase, StorageUseCase.unavailable());
    }
}
