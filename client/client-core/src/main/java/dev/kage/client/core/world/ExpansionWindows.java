package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageSkin;

/** Cửa sổ/luồng lô 23 (ADR-115…126) — tách khỏi {@link CommunityWindows} để nó không phình thêm. */
record ExpansionWindows(
        SecurityPinCoordinator securityPin,
        ReportPanel buyOrderPanel,
        BuyOrderCoordinator buyOrders,
        ReportPanel revivalPanel,
        RevivalCoordinator revival,
        ReportPanel merchantPanel,
        MerchantCoordinator merchant,
        ReportPanel fieldPanel,
        SpiritFieldCoordinator field,
        ReportPanel forgePanel,
        ForgeCoordinator forge,
        ReportPanel encounterPanel,
        EncounterCoordinator encounter,
        QuizCoordinator quiz,
        DayNightCoordinator dayNight,
        TamingCoordinator taming,
        TournamentCoordinator tournament,
        TribulationCoordinator tribulation,
        ArtifactCoordinator artifact,
        FormationCoordinator formation,
        CaveCoordinator cave) {

    static ExpansionWindows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        // Cầu mua (ADR-116) — không phím, mở bằng lệnh chat /caumua.
        ReportPanel buyOrderPanel =
                new ReportPanel(skin, "buyorder.title", Input.Keys.UNKNOWN, BuyOrderCoordinator.TAB_KEYS);
        // Hồi sinh (ADR-117) — không phím, server mở khi mình gục.
        ReportPanel revivalPanel = new ReportPanel(skin, "revive.title", Input.Keys.UNKNOWN, List.of());
        // Thương nhân du hành (ADR-118) — không phím, mở bằng lệnh chat /thuongnhan.
        ReportPanel merchantPanel = new ReportPanel(skin, "merchant.title", Input.Keys.UNKNOWN, List.of());
        // Linh điền (ADR-119) — không phím, mở bằng lệnh chat /linhdien.
        ReportPanel fieldPanel =
                new ReportPanel(skin, "field.title", Input.Keys.UNKNOWN, SpiritFieldCoordinator.TAB_KEYS);
        // Luyện khí (ADR-120) — không phím, mở bằng lệnh chat /luyenkhi.
        ReportPanel forgePanel = new ReportPanel(skin, "forge.title", Input.Keys.UNKNOWN, List.of());
        // Kỳ ngộ (ADR-121) — không phím, server mở khi gặp kỳ ngộ.
        ReportPanel encounterPanel = new ReportPanel(skin, "encounter.title", Input.Keys.UNKNOWN, List.of());
        return new ExpansionWindows(
                new SecurityPinCoordinator(sender, feeds.chat()),
                buyOrderPanel,
                new BuyOrderCoordinator(buyOrderPanel, sender, feeds),
                revivalPanel,
                new RevivalCoordinator(revivalPanel, sender),
                merchantPanel,
                new MerchantCoordinator(merchantPanel, sender, feeds),
                fieldPanel,
                new SpiritFieldCoordinator(fieldPanel, sender, feeds),
                forgePanel,
                new ForgeCoordinator(forgePanel, sender, feeds),
                encounterPanel,
                new EncounterCoordinator(encounterPanel, sender, feeds),
                new QuizCoordinator(feeds.chat()),
                new DayNightCoordinator(feeds.chat()),
                new TamingCoordinator(sender, feeds),
                new TournamentCoordinator(sender, feeds.chat()),
                new TribulationCoordinator(sender, feeds.chat()),
                new ArtifactCoordinator(sender, feeds.chat()),
                new FormationCoordinator(feeds.chat()),
                new CaveCoordinator(sender, feeds.chat()));
    }

    /** Đăng ký vào hub. */
    void registerInto(PanelHub panels) {
        panels.register(securityPin);
        panels.register(buyOrderPanel, buyOrders);
        panels.register(revivalPanel, revival);
        panels.register(merchantPanel, merchant);
        panels.register(fieldPanel, field);
        panels.register(forgePanel, forge);
        panels.register(encounterPanel, encounter);
        panels.register(quiz);
        panels.register(dayNight);
        panels.register(taming);
        panels.register(tournament);
        panels.register(tribulation);
        panels.register(artifact);
        panels.register(formation);
        panels.register(cave);
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        // chưa có cửa sổ mở bằng phím
    }
}
