package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Hai cửa sổ CHỈ ĐỌC dựng từ {@link ReportPanel}: Bảng nhân vật (phím C, ADR-056) và Bia Anh Danh (phím
 * L, ADR-057). Gom lại để constructor {@code WorldScreen} không vượt trần {@code MethodLength}.
 */
record ReportWindows(
        ReportPanel characterSheetPanel,
        CharacterSheetCoordinator characterSheet,
        ReportPanel leaderboardPanel,
        LeaderboardCoordinator leaderboard) {

    static ReportWindows create(KageSkin skin, WorldCommandSender sender, long localCharacterId) {
        ReportPanel sheetPanel = new ReportPanel(skin, "charsheet.title", Input.Keys.C, List.of());
        ReportPanel boardPanel =
                new ReportPanel(skin, "leaderboard.title", Input.Keys.L, LeaderboardCoordinator.TAB_KEYS);
        return new ReportWindows(
                sheetPanel,
                new CharacterSheetCoordinator(sheetPanel, sender),
                boardPanel,
                new LeaderboardCoordinator(boardPanel, sender, localCharacterId));
    }
}
