package dev.kage.client.core.world;

import java.util.List;

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
        DailyCoordinator daily,
        ReportPanel guildWarPanel,
        GuildWarCoordinator guildWars,
        ReportPanel noticePanel,
        ServiceDeskCoordinator serviceDesk,
        ReportPanel bestiaryPanel,
        BestiaryCoordinator bestiary,
        ReportPanel refinementPanel,
        RefinementCoordinator refinement,
        ReportPanel auctionPanel,
        GuildAuctionCoordinator auctions,
        ExpansionWindows expansion) {

    static CommunityWindows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        ReportPanel relationshipPanel =
                new ReportPanel(skin, "relation.title", Input.Keys.Q, RelationshipCoordinator.TAB_KEYS);
        ReportPanel dailyPanel = new ReportPanel(skin, "daily.title", Input.Keys.E, DailyCoordinator.TAB_KEYS);
        // Bang chiến (ADR-102) — không phím, mở từ nút cửa sổ Bang.
        ReportPanel guildWarPanel = new ReportPanel(skin, "guildwar.title", Input.Keys.UNKNOWN, List.of());
        // Thông báo server (ADR-103) — tự mở khi server gửi, không phím.
        ReportPanel noticePanel = new ReportPanel(skin, "notice.title", Input.Keys.UNKNOWN, List.of());
        // Sổ sưu tầm yêu thú (ADR-108) — phím Y (N đã là của giao dịch).
        ReportPanel bestiaryPanel = new ReportPanel(skin, "bestiary.title", Input.Keys.Y, List.of());
        // Tẩy luyện pháp khí (ADR-111) — phím X.
        ReportPanel refinementPanel = new ReportPanel(skin, "refine.title", Input.Keys.X, List.of());
        // Đấu giá bang (ADR-112) — mở từ nút trong cửa sổ Bang, không phím.
        ReportPanel auctionPanel = new ReportPanel(skin, "auction.title", Input.Keys.UNKNOWN, List.of());
        return new CommunityWindows(
                relationshipPanel,
                new RelationshipCoordinator(relationshipPanel, sender, feeds.chat()),
                new EmoteCoordinator(sender, feeds.chat(), feeds.emoteDisplay()),
                dailyPanel,
                new DailyCoordinator(dailyPanel, sender, feeds.chat()),
                guildWarPanel,
                new GuildWarCoordinator(guildWarPanel, sender, feeds.chat()),
                noticePanel,
                new ServiceDeskCoordinator(noticePanel, sender, feeds.chat()),
                bestiaryPanel,
                new BestiaryCoordinator(bestiaryPanel, sender),
                refinementPanel,
                new RefinementCoordinator(refinementPanel, sender, feeds),
                auctionPanel,
                new GuildAuctionCoordinator(auctionPanel, sender, feeds),
                ExpansionWindows.create(skin, sender, feeds));
    }

    /** Đăng ký mọi cửa sổ ở đây vào hub — tách khỏi {@code WorldScreen#registerPanels} (trần {@code MethodLength}). */
    void registerInto(PanelHub panels) {
        panels.register(relationshipPanel, relationships);
        panels.register(dailyPanel, daily);
        panels.register(emotes);
        panels.register(guildWarPanel, guildWars);
        panels.register(noticePanel, serviceDesk);
        panels.register(bestiaryPanel, bestiary);
        panels.register(refinementPanel, refinement);
        panels.register(auctionPanel, auctions);
        expansion.registerInto(panels);
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        relationshipPanel.setCanOpen(noneOpen);
        dailyPanel.setCanOpen(noneOpen);
        bestiaryPanel.setCanOpen(noneOpen);
        refinementPanel.setCanOpen(noneOpen);
        expansion.gateKeyOpening(noneOpen);
    }
}
