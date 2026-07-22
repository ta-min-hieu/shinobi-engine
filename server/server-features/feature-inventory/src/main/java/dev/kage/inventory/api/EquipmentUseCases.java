package dev.kage.inventory.api;

import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;

/**
 * Gộp 3 UseCase liên quan trang bị — tránh {@link InventoryFacade} vượt quá 4 tham số
 * (CODING-STANDARDS §4, Checkstyle {@code ParameterNumber}), cùng pattern
 * {@code dev.kage.server.gateway.GatewayFacades}.
 */
public record EquipmentUseCases(
        EquipItemUseCase equipItemUseCase,
        UnequipItemUseCase unequipItemUseCase,
        ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase) {
}
