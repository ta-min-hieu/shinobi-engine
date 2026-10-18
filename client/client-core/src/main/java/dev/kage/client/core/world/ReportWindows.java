package dev.kage.client.core.world;

import java.util.List;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Sáu cửa sổ dựng từ {@link ReportPanel}: Bảng nhân vật (phím C, ADR-056), Bia Anh Danh (phím L, ADR-057),
 * kho đồ tông môn (không phím — mở qua hội thoại, ADR-061), Thành tựu (phím U, ADR-062) và phường thị (phím
 * O, ADR-063), khảm Linh Ngọc (phím V, ADR-064). Gom lại để
 * constructor {@code WorldScreen} không vượt trần {@code MethodLength}.
 */
record ReportWindows(
        ReportPanel characterSheetPanel,
        CharacterSheetCoordinator characterSheet,
        ReportPanel leaderboardPanel,
        LeaderboardCoordinator leaderboard,
        ReportPanel storagePanel,
        StorageCoordinator storage,
        ReportPanel achievementPanel,
        AchievementCoordinator achievements,
        ReportPanel marketPanel,
        MarketCoordinator market,
        ReportPanel gemPanel,
        GemCoordinator gems) {

    /**
     * Luồng nền các cửa sổ này mượn: catalog vật phẩm (tên món), nhật ký chat (báo mở thành tựu, đã bán), và
     * nơi nhận MÁU TỐI ĐA mới (khảm Linh Ngọc vào món đang mặc — ADR-064 D4).
     */
    record Feeds(ItemCatalogCoordinator itemCatalog, ChatCoordinator chat, IntConsumer onOwnMaxHpChanged) {}

    static ReportWindows create(KageSkin skin, WorldCommandSender sender, long localCharacterId, Feeds feeds) {
        ReportPanel sheetPanel = new ReportPanel(skin, "charsheet.title", Input.Keys.C, List.of());
        ReportPanel boardPanel =
                new ReportPanel(skin, "leaderboard.title", Input.Keys.L, LeaderboardCoordinator.TAB_KEYS);
        // Không phím mở: Input.Keys.UNKNOWN không bao giờ tới, và StorageCoordinator khoá luôn canOpen.
        ReportPanel storagePanel = new ReportPanel(skin, "storage.title", Input.Keys.UNKNOWN, List.of());
        // Phím U — H đã là của khung chọn hệ (ADR-062 D6).
        ReportPanel achievementPanel = new ReportPanel(skin, "achievement.title", Input.Keys.U, List.of());
        ReportPanel marketPanel = new ReportPanel(skin, "market.title", Input.Keys.O, MarketCoordinator.TAB_KEYS);
        ReportPanel gemPanel = new ReportPanel(skin, "gem.title", Input.Keys.V, List.of());
        return new ReportWindows(
                sheetPanel,
                new CharacterSheetCoordinator(sheetPanel, sender),
                boardPanel,
                new LeaderboardCoordinator(boardPanel, sender, localCharacterId),
                storagePanel,
                new StorageCoordinator(storagePanel, sender, feeds.itemCatalog()),
                achievementPanel,
                new AchievementCoordinator(achievementPanel, sender, feeds.chat()),
                marketPanel,
                new MarketCoordinator(marketPanel, sender, feeds),
                gemPanel,
                new GemCoordinator(gemPanel, sender, feeds));
    }

    /** Cổng phím CHUNG của các cửa sổ mở bằng phím ({@code PanelHub#noneOpen}); kho đồ không có phím. */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        characterSheetPanel.setCanOpen(noneOpen);
        leaderboardPanel.setCanOpen(noneOpen);
        achievementPanel.setCanOpen(noneOpen);
        marketPanel.setCanOpen(noneOpen);
        gemPanel.setCanOpen(noneOpen);
    }
}
