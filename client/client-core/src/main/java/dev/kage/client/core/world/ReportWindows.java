package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Bốn cửa sổ dựng từ {@link ReportPanel}: Bảng nhân vật (phím C, ADR-056), Bia Anh Danh (phím L, ADR-057),
 * kho đồ tông môn (không phím — mở qua hội thoại, ADR-061) và Thành tựu (phím U, ADR-062). Gom lại để
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
        AchievementCoordinator achievements) {

    /** Hai luồng nền các cửa sổ này mượn: catalog vật phẩm (kho đồ) và nhật ký chat (báo mở thành tựu). */
    record Feeds(ItemCatalogCoordinator itemCatalog, ChatCoordinator chat) {}

    static ReportWindows create(KageSkin skin, WorldCommandSender sender, long localCharacterId, Feeds feeds) {
        ReportPanel sheetPanel = new ReportPanel(skin, "charsheet.title", Input.Keys.C, List.of());
        ReportPanel boardPanel =
                new ReportPanel(skin, "leaderboard.title", Input.Keys.L, LeaderboardCoordinator.TAB_KEYS);
        // Không phím mở: Input.Keys.UNKNOWN không bao giờ tới, và StorageCoordinator khoá luôn canOpen.
        ReportPanel storagePanel = new ReportPanel(skin, "storage.title", Input.Keys.UNKNOWN, List.of());
        // Phím U — H đã là của khung chọn hệ (ADR-062 D6).
        ReportPanel achievementPanel = new ReportPanel(skin, "achievement.title", Input.Keys.U, List.of());
        return new ReportWindows(
                sheetPanel,
                new CharacterSheetCoordinator(sheetPanel, sender),
                boardPanel,
                new LeaderboardCoordinator(boardPanel, sender, localCharacterId),
                storagePanel,
                new StorageCoordinator(storagePanel, sender, feeds.itemCatalog()),
                achievementPanel,
                new AchievementCoordinator(achievementPanel, sender, feeds.chat()));
    }

    /** Cổng phím CHUNG của các cửa sổ mở bằng phím ({@code PanelHub#noneOpen}); kho đồ không có phím. */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        characterSheetPanel.setCanOpen(noneOpen);
        leaderboardPanel.setCanOpen(noneOpen);
        achievementPanel.setCanOpen(noneOpen);
    }
}
