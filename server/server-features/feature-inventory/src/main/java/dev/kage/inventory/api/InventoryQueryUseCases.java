package dev.kage.inventory.api;

import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;

/**
 * Gộp 2 UseCase ĐỌC THUẦN của ADR-035 (liệt kê túi đồ 507/508, catalog vật phẩm 509/510) — cùng lý
 * do gộp như {@link EquipmentUseCases}/{@link ItemAcquisitionUseCases}: giữ constructor
 * {@link InventoryFacade} ở đúng 4 tham số (Checkstyle {@code ParameterNumber}).
 */
public record InventoryQueryUseCases(
        ListInventoryUseCase listInventoryUseCase, GetItemCatalogUseCase getItemCatalogUseCase) {}
