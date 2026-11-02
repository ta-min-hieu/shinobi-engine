package dev.kage.client.core.world;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Luồng lô 27–28 — Tàng Bảo Đồ (ADR-147), Dịch Danh Phù (ADR-148), quẻ bói (ADR-149), xúc xắc chia đồ (ADR-150),
 * vòng quay may mắn (ADR-151),
 * Truyền Âm Phù (ADR-152), quà mốc cảnh giới (ADR-153), nhiệm vụ tuần (ADR-154),
 * cửa hàng vinh dự (ADR-155), bảng sát thương boss trực tiếp (ADR-157).
 * Gom thành MỘT thành phần của {@link ExpansionWindows} để {@code create} ở đó không vượt trần {@code MethodLength}.
 */
record Lot27Windows(
        TreasureCoordinator treasure,
        RenameCoordinator rename,
        FortuneCoordinator fortune,
        LootRollCoordinator lootRoll,
        LuckyWheelCoordinator luckyWheel,
        BroadcastCoordinator broadcast,
        MilestoneCoordinator milestones,
        WeeklyTaskCoordinator weeklyTasks,
        HonorShopCoordinator honorShop,
        WorldBossBoardCoordinator bossBoard) {

    static Lot27Windows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot27Windows(
                TreasureCoordinator.create(skin, sender, feeds),
                new RenameCoordinator(sender, feeds.chat()),
                new FortuneCoordinator(sender, feeds),
                LootRollCoordinator.create(skin, sender, feeds),
                LuckyWheelCoordinator.create(skin, sender, feeds),
                new BroadcastCoordinator(sender, feeds.chat()),
                MilestoneCoordinator.create(skin, sender, feeds),
                WeeklyTaskCoordinator.create(skin, sender, feeds),
                HonorShopCoordinator.create(skin, sender, feeds),
                new WorldBossBoardCoordinator());
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(treasure.panel(), treasure);
        panels.register(rename);
        panels.register(fortune);
        panels.register(lootRoll.panel(), lootRoll);
        panels.register(luckyWheel.panel(), luckyWheel);
        panels.register(broadcast);
        panels.register(milestones.panel(), milestones);
        panels.register(weeklyTasks.panel(), weeklyTasks);
        panels.register(honorShop.panel(), honorShop);
        panels.register(bossBoard);
    }
}
