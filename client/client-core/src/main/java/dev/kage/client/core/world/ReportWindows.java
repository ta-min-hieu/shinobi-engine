package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Ba cửa sổ dựng từ {@link ReportPanel}: Bảng nhân vật (phím C, ADR-056), Bia Anh Danh (phím L, ADR-057)
 * và kho đồ tông môn (không phím — mở qua hội thoại, ADR-061). Gom lại để constructor {@code WorldScreen}
 * không vượt trần {@code MethodLength}.
 */
record ReportWindows(
        ReportPanel characterSheetPanel,
        CharacterSheetCoordinator characterSheet,
        ReportPanel leaderboardPanel,
        LeaderboardCoordinator leaderboard,
        ReportPanel storagePanel,
        StorageCoordinator storage) {

    static ReportWindows create(
            KageSkin skin, WorldCommandSender sender, long localCharacterId, ItemCatalogCoordinator itemCatalog) {
        ReportPanel sheetPanel = new ReportPanel(skin, "charsheet.title", Input.Keys.C, List.of());
        ReportPanel boardPanel =
                new ReportPanel(skin, "leaderboard.title", Input.Keys.L, LeaderboardCoordinator.TAB_KEYS);
        // Không phím mở: Input.Keys.UNKNOWN không bao giờ tới, và StorageCoordinator khoá luôn canOpen.
        ReportPanel storagePanel = new ReportPanel(skin, "storage.title", Input.Keys.UNKNOWN, List.of());
        return new ReportWindows(
                sheetPanel,
                new CharacterSheetCoordinator(sheetPanel, sender),
                boardPanel,
                new LeaderboardCoordinator(boardPanel, sender, localCharacterId),
                storagePanel,
                new StorageCoordinator(storagePanel, sender, itemCatalog));
    }
}
