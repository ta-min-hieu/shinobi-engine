package dev.kage.inventory.api;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;

/**
 * Gộp 2 UseCase "item đi VÀO túi" — {@link InventoryFacade} đã chạm giới hạn 4 tham số
 * (CODING-STANDARDS §4, Checkstyle {@code ParameterNumber}) từ Phase 5 lát cắt 4, nên UseCase mới
 * của ADR-035 phải gộp thay vì thêm tham số thứ 5 (feature-inventory/CLAUDE.md ghi rõ ràng buộc
 * này). Cùng pattern {@link EquipmentUseCases}.
 */
public record ItemAcquisitionUseCases(AddItemUseCase addItemUseCase, GrantLootUseCase grantLootUseCase) {}
