package dev.kage.client.core.world;

/**
 * Luồng lô 51 (ADR-335…) — lời nhắn trạng thái (ADR-335), rao vặt (ADR-336), lịch sử tử vong (ADR-337), biểu quyết đuổi
 * (ADR-338), màu kênh chat (ADR-339), báo đầy máu (ADR-340), rung màn hình (ADR-342). Nhấp đúp dùng/mặc đồ (ADR-341)
 * nằm trong {@code InventoryPanel}. MỘT thành phần của {@link Lot50Windows}.
 */
record Lot51Windows(
        StatusMessageCoordinator statusMessage,
        BulletinCoordinator bulletin,
        DeathHistoryCoordinator deathHistory,
        VoteKickCoordinator voteKick,
        ChatColorCoordinator chatColor,
        FullHealthCoordinator fullHealth,
        ScreenShakeCoordinator screenShake) {

    static Lot51Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot51Windows(
                new StatusMessageCoordinator(sender, feeds.chat()),
                new BulletinCoordinator(sender, feeds.chat()),
                new DeathHistoryCoordinator(sender, feeds.chat()),
                new VoteKickCoordinator(sender, feeds.chat()),
                new ChatColorCoordinator(feeds.chat()),
                new FullHealthCoordinator(feeds.chat()),
                new ScreenShakeCoordinator(feeds.chat()));
    }

    void registerInto(PanelHub panels) {
        panels.register(statusMessage);
        panels.register(bulletin);
        panels.register(deathHistory);
        panels.register(voteKick);
        panels.register(chatColor);
        panels.register(fullHealth);
        panels.register(screenShake);
    }
}
