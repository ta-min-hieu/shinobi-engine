package dev.kage.client.core.world;

import java.util.List;

import com.badlogic.gdx.Input;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.DisplaySettings;
import dev.kage.client.core.ui.theme.GdxPreferencesDisplaySettingsStore;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.ListZoneMapRequest;
import dev.kage.protocol.Opcode;

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
        CaveCoordinator cave,
        ZoneMapCoordinator zoneMap,
        ReportPanel displaySettingsPanel,
        DisplaySettingsCoordinator displaySettings,
        WeatherCoordinator weather,
        BossTelegraphPresenter telegraphs) {

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
        // Cài đặt hiển thị (ADR-143) — không phím, mở bằng lệnh chat /caidat.
        ReportPanel displaySettingsPanel = new ReportPanel(skin, "settings.title", Input.Keys.UNKNOWN, List.of());
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
                new CaveCoordinator(sender, feeds.chat()),
                zoneMap(sender, feeds), // ADR-141
                displaySettingsPanel,
                new DisplaySettingsCoordinator(
                        displaySettingsPanel,
                        DisplaySettings.load(new GdxPreferencesDisplaySettingsStore()),
                        feeds.chat()),
                new WeatherCoordinator(), // ADR-144
                new BossTelegraphPresenter()); // ADR-145
    }

    /** ADR-141 — biển vùng PvP: hỏi bản đồ vùng qua {@code sender}, báo vào/ra qua kênh hệ thống. */
    private static ZoneMapCoordinator zoneMap(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new ZoneMapCoordinator(
                () -> sender.sendReadRequest(
                        Opcode.OPCODE_LIST_ZONE_MAP_REQUEST_VALUE, ListZoneMapRequest.getDefaultInstance()),
                feeds.chat()::showSystemNotice);
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
        panels.register(zoneMap);
        panels.register(displaySettingsPanel, displaySettings);
        panels.register(weather);
        panels.register(telegraphs);
    }

    /**
     * Nối lớp hiển thị của lô 26: biển vùng PvP lên HUD (ADR-141), công tắc cài đặt vào từng lớp vẽ (ADR-143) và lớp
     * thời tiết (ADR-144).
     */
    void wireDisplay(
            dev.kage.client.core.ui.hud.CombatHud hud,
            RealmTitles titles,
            CombatFeedbackPresenter feedback,
            HorizonBackdrop backdrop) {
        zoneMap.useBanner(hud::setZoneBanner);
        displaySettings.applyTo(titles, feedback, backdrop, weather.overlay());
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        // chưa có cửa sổ mở bằng phím
    }
}
