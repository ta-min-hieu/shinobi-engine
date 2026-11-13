package dev.kage.client.core.world;

/**
 * Luồng lô 49 (ADR-319…) — ghi chú thành viên bang (ADR-319), mã giới thiệu (ADR-320), lịch sử đột phá (ADR-322), thu
 * phóng camera (ADR-323), lọc túi (ADR-324), đếm người xung quanh (ADR-325), nhãn đồ mới (ADR-326). Báo cường hoá cao
 * (ADR-321) chỉ ở server. MỘT thành phần của {@link Lot48Windows}.
 */
record Lot49Windows(
        GuildMemberNoteCoordinator memberNotes,
        ReferralCoordinator referral,
        BreakthroughHistoryCoordinator breakthroughs,
        CameraZoomCoordinator cameraZoom,
        BagFilterCoordinator bagFilter,
        NearbyCountCoordinator nearby,
        NewItemsCoordinator newItems) {

    static Lot49Windows create(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new Lot49Windows(
                new GuildMemberNoteCoordinator(sender, feeds.chat()),
                new ReferralCoordinator(sender, feeds.chat()),
                new BreakthroughHistoryCoordinator(sender, feeds.chat()),
                new CameraZoomCoordinator(),
                new BagFilterCoordinator(feeds.chat(), feeds.itemCatalog()),
                new NearbyCountCoordinator(feeds.chat(), feeds.emoteDisplay()),
                new NewItemsCoordinator());
    }

    /** Đăng ký vào hub — thu phóng camera đăng ký CUỐI để mọi bảng nhận lăn chuột trước nó. */
    void registerInto(PanelHub panels) {
        panels.register(memberNotes);
        panels.register(referral);
        panels.register(breakthroughs);
        panels.register(bagFilter);
        panels.register(nearby);
        panels.register(newItems);
        panels.register(cameraZoom);
    }
}
