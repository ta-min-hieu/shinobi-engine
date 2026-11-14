package dev.kage.client.core.world;

/**
 * Luồng lô 58 (ADR-391…) — xếp hạng sát thủ / tu luyện + thâm niên bang (ADR-391/392/393), lọc chat bạn bè / toạ độ
 * HUD / ẩn nhãn mình (ADR-396/397/398). Cuộn khung chat (ADR-395) nằm trong {@code ChatPanel}; quà quay lại (ADR-394)
 * là thư hệ thống. MỘT thành phần của {@link Lot57Windows}.
 */
record Lot58Windows(LifetimeRankingCoordinator rankings, ChatViewCoordinator chatView) {

    static Lot58Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot58Windows(
                new LifetimeRankingCoordinator(sender, feeds.chat(), java.time.ZoneId.systemDefault()),
                new ChatViewCoordinator(feeds.chat()));
    }

    void registerInto(PanelHub panels) {
        panels.register(rankings);
        panels.register(chatView);
    }
}
