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
        BossTelegraphPresenter telegraphs,
        Lot27Windows lot27,
        Lot29Windows lot29,
        Lot30Windows lot30,
        Lot31Windows lot31) {

    static ExpansionWindows create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        // Cầu mua (ADR-116) — không phím, mở bằng lệnh chat /caumua.
        ReportPanel buyOrderPanel =
                new ReportPanel(skin, "buyorder.title", Input.Keys.UNKNOWN, BuyOrderCoordinator.TAB_KEYS);
        // Hồi sinh (ADR-117, server mở khi mình gục) + thương nhân du hành (ADR-118, /thuongnhan) — không phím.
        ReportPanel revivalPanel = new ReportPanel(skin, "revive.title", Input.Keys.UNKNOWN, List.of());
        ReportPanel merchantPanel = new ReportPanel(skin, "merchant.title", Input.Keys.UNKNOWN, List.of());
        // Linh điền (ADR-119) — không phím, mở bằng lệnh chat /linhdien.
        ReportPanel fieldPanel =
                new ReportPanel(skin, "field.title", Input.Keys.UNKNOWN, SpiritFieldCoordinator.TAB_KEYS);
        // Luyện khí (ADR-120, /luyenkhi) + kỳ ngộ (ADR-121, server mở khi gặp) — không phím.
        ReportPanel forgePanel = new ReportPanel(skin, "forge.title", Input.Keys.UNKNOWN, List.of());
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
                new BossTelegraphPresenter(), // ADR-145
                Lot27Windows.create(skin, sender, feeds), // ADR-147 /tangbaodo, ADR-148 /doiten
                Lot29Windows.create(skin, sender, feeds), // ADR-159 khoá vật phẩm
                Lot30Windows.create(skin, sender, feeds), // ADR-167 nhận hết thư
                Lot31Windows.create(skin, sender, feeds)); // ADR-175 bộ trang bị
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
        lot27.registerInto(panels);
        lot29.registerInto(panels);
        lot30.registerInto(panels);
        lot31.registerInto(panels);
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
        lot27.bossBoard().useBoard(hud::setBossBoard); // ADR-157
        lot29.expBuff().useBanner(hud::setExpBuff); // ADR-164
        // ADR-171 — đồng bộ công tắc "cho người khác soi" lên server lúc vào game và mỗi lần đổi.
        displaySettings.onChange(DisplaySettings.Option.ALLOW_INSPECT, lot30.inspect()::setAllowed);
        lot31.guildNotice().requestOnEnter(); // ADR-179 — thấy thông báo bang ngay lúc vào game
        lot30.restReminder().useSwitch(displaySettings.settings().switchFor(DisplaySettings.Option.REST_REMINDER));
        displaySettings.applyTo(titles, feedback, backdrop, weather.overlay());
    }

    /**
     * Lệnh chat dùng MÓN ĐANG CHỌN trong túi: dùng nhiều (ADR-176), khoe vật phẩm (ADR-188), góp vật phẩm cho bang
     * (ADR-207); và tự tìm đường (ADR-206) lái nhân vật qua {@link LocalIntentSender}.
     */
    void useLocalInput(
            InventoryCoordinator inventory, LocalIntentSender intents, MailCoordinator mail, QuestCoordinator quests) {
        lot31.bulkUse().useSelection(inventory::selectedItemId);
        lot31.lot32().itemLink().useSelection(inventory::selectedItem);
        Lot34Windows lot34 = lot31.lot32().lot33().lot34();
        intents.useAutoWalk(lot34.autoWalk().target());
        quests.useGoTo(lot34.autoWalk()::walkToNpc); // ADR-221 — nút "Đi tới" trong nhật ký nhiệm vụ
        lot34.lot35().guildItemDonation().useSelection(inventory::selectedItemId); // ADR-207 /gopdo
        lot34.lot35().partyMark().useTargeting(intents); // ADR-213 /danhdau, /theodau
        Lot36Windows lot36 = lot34.lot35().lot36();
        lot36.stackSplit().useSelection(inventory::selectedItemId); // ADR-215 /tach
        lot36.mailCleanup().useSelection(mail::selectedMailId); // ADR-217 /ghimthu
        lot36.lot37().combatLog().useLocalId(intents.localCharacterId()); // ADR-226 /nhatkychien
    }

    /**
     * ADR-220 — vẽ ký hiệu "mục tiêu nhóm" trên đầu con quái trưởng nhóm đánh dấu (ADR-213), giữa {@code begin}/{@code
     * end} của {@link RealmTitles}. Không cấp phát mỗi khung: khoá tra đã đóng hộp sẵn lúc nhận event.
     */
    void drawPartyMark(RealmTitles titles, CombatHudController combat) {
        lot31.lot32().lot33().lot34().lot35().partyMark().markedKey().ifPresent(key -> {
            CombatHudController.MonsterState monster = combat.monsters().get(key);
            if (monster != null) {
                titles.drawMarker(monster.x(), monster.y());
            }
        });
    }

    /** Cổng phím chung ({@code PanelHub#noneOpen}). */
    void gateKeyOpening(java.util.function.BooleanSupplier noneOpen) {
        // chưa có cửa sổ mở bằng phím
    }
}
