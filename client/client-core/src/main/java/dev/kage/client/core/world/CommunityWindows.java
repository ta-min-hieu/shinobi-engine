package dev.kage.client.core.world;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cửa sổ lô 21 — Quan hệ (phím Q: chặn, sư đồ, đạo lữ), Hằng ngày (phím E: bế quan, online, linh thảo) và luồng biểu
 * cảm không cửa sổ. Tách khỏi {@link ReportWindows} để {@code ReportWindows.create} không vượt trần
 * {@code MethodLength}.
 */
record CommunityWindows(
        ReportPanel relationshipPanel,
        RelationshipCoordinator relationships,
        EmoteCoordinator emotes,
        ReportPanel dailyPanel,
        DailyCoordinator daily) {

    static CommunityWindows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        ReportPanel relationshipPanel =
                new ReportPanel(skin, "relation.title", Input.Keys.Q, RelationshipCoordinator.TAB_KEYS);
        ReportPanel dailyPanel = new ReportPanel(skin, "daily.title", Input.Keys.E, DailyCoordinator.TAB_KEYS);
        return new CommunityWindows(
                relationshipPanel,
                new RelationshipCoordinator(relationshipPanel, sender, feeds.chat()),
                new EmoteCoordinator(sender, feeds.chat(), feeds.emoteDisplay()),
                dailyPanel,
                new DailyCoordinator(dailyPanel, sender, feeds.chat()));
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        relationshipPanel.setCanOpen(noneOpen);
        dailyPanel.setCanOpen(noneOpen);
    }
}
