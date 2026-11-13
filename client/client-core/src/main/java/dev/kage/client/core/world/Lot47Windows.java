package dev.kage.client.core.world;

/**
 * Luồng lô 47 (ADR-303…) — xếp hạng sát thương boss + lượt thích (ADR-303/305), thu phóng giao diện (ADR-308). Chế độ
 * mù màu (ADR-307) và ẩn nhãn người ngoài nhóm (ADR-309) là dòng cài đặt hiển thị; chia sẻ vị trí (ADR-310) nằm trong
 * {@link CoordinatesCoordinator}; tin bang lúc vắng (ADR-304) và báo trước sự kiện (ADR-306) chỉ ở server. MỘT thành
 * phần của {@link Lot46Windows}.
 */
record Lot47Windows(BossDamageRankingCoordinator rankings, UiScaleCoordinator uiScale, Lot48Windows lot48) {

    static Lot47Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot47Windows(
                new BossDamageRankingCoordinator(sender, feeds.chat()),
                new UiScaleCoordinator(feeds.chat()),
                Lot48Windows.create(sender, feeds)); // lô 48 lồng ở đây
    }

    /** Đăng ký vào hub, rồi nối thu phóng giao diện vào chính hub đó. */
    void registerInto(PanelHub panels) {
        panels.register(rankings);
        panels.register(uiScale);
        uiScale.useHub(panels);
        lot48.registerInto(panels);
    }
}
