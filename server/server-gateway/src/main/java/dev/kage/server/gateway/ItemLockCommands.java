package dev.kage.server.gateway;

import java.util.Set;

import dev.kage.inventory.api.ItemLockFacade;
import dev.kage.inventory.domain.ItemLockPort;
import dev.kage.protocol.SetItemLockCommand;
import dev.kage.protocol.SetItemLockOutcome;
import dev.kage.protocol.SetItemLockResponse;

/** Khoá vật phẩm (ADR-159) — bật/tắt khoá; danh sách túi đồ hỏi {@link #lockedOf} để gắn cờ. */
final class ItemLockCommands {

    private final ItemLockFacade facade;

    ItemLockCommands(ItemLockFacade facade) {
        this.facade = facade;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SetItemLockResponse set(long characterId, SetItemLockCommand command) {
        ItemLockPort.Outcome outcome = facade.setLocked(characterId, command.getItemId(), command.getLocked());
        return SetItemLockResponse.newBuilder()
                .setOutcome(
                        outcome == ItemLockPort.Outcome.OK
                                ? SetItemLockOutcome.SET_ITEM_LOCK_OUTCOME_OK
                                : SetItemLockOutcome.SET_ITEM_LOCK_OUTCOME_NOT_FOUND)
                .setItemId(command.getItemId())
                .setLocked(outcome == ItemLockPort.Outcome.OK && command.getLocked())
                .build();
    }

    /** Virtual Thread. */
    Set<Long> lockedOf(long characterId) {
        return facade.lockedItems(characterId);
    }
}
