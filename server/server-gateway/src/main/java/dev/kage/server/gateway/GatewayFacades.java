package dev.kage.server.gateway;

import dev.kage.account.api.AccountFacade;
import dev.kage.character.api.CharacterFacade;
import dev.kage.inventory.api.InventoryFacade;

/**
 * Gộp 4 Facade nghiệp vụ (account/character/inventory/{@link ContentFacades shop+dialogue}) —
 * tránh {@link GatewayServer}/{@link GatewayChannelHandler} vượt quá 4 tham số (CODING-STANDARDS
 * §4, Checkstyle {@code ParameterNumber}), cùng pattern {@code CharacterBaseStats}/
 * {@code NewCharacter}. {@code shopFacade} trước đây là tham số riêng — đổi sang
 * {@link ContentFacades} khi thêm {@code dialogueFacade} (Phase 6 lát cắt 2) mới CHẠM giới hạn.
 */
record GatewayFacades(
        AccountFacade accountFacade,
        CharacterFacade characterFacade,
        InventoryFacade inventoryFacade,
        ContentFacades contentFacades) {}
