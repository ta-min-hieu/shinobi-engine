package dev.kage.server.gateway;

import dev.kage.protocol.SortInventoryResponse;

/** Lớp lệnh lô 29 (ADR-159…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot29Commands(ItemLockCommands itemLock, GemCombineCommands gemCombine, Lot29Facades facades) {

    static Lot29Commands of(Lot29Facades facades) {
        return new Lot29Commands(
                new ItemLockCommands(facades.itemLock()), new GemCombineCommands(facades.gemCombine()), facades);
    }

    /** ADR-160 — Virtual Thread; {@code characterId} từ session. */
    SortInventoryResponse sortInventory(long characterId) {
        int merged = facades.bagSort().sort(characterId);
        return SortInventoryResponse.newBuilder()
                .setOk(true)
                .setMergedRows(merged)
                .build();
    }
}
