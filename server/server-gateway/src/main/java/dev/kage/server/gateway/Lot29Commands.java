package dev.kage.server.gateway;

/** Lớp lệnh lô 29 (ADR-159…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot29Commands(ItemLockCommands itemLock) {

    static Lot29Commands of(Lot29Facades facades) {
        return new Lot29Commands(new ItemLockCommands(facades.itemLock()));
    }
}
