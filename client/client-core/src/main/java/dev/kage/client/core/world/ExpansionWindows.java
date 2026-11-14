package dev.kage.client.core.world;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
        displaySettings.usePartyMembers(lot39().lot40().damageMeter()::isTracked); // ADR-285
        displaySettings.applyTo(titles, feedback, backdrop, weather.overlay());
        lot39().lot40().lot41().lot42().lot43().lot44().fps().useHud(hud::setFpsVisible); // ADR-283
        feedback.useCombo(
                lot39().lot40().lot41().lot42().lot43().lot44().lot45().combo()::enabled); // ADR-293
        Lot48Windows lot48 = lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48();
        lot48.pingHud().useHud(hud::setLatencyVisible); // ADR-316
        hud.useNearbyCount(lot48.lot49().nearby()::count); // ADR-325
        lot48.lot49().nearby().useHud(hud::setNearbyVisible);
        wireLot50Display(lot48.lot49().lot50(), hud, feedback);
        hud.setMinimal(displaySettings
                .settings()
                .switchFor(DisplaySettings.Option.MINIMAL_HUD)
                .getAsBoolean());
        displaySettings.onChange(DisplaySettings.Option.MINIMAL_HUD, hud::setMinimal); // ADR-318
        lot39().photoMode() // ADR-300 — F12 chụp sau khi khung vẽ xong
                .afterRender(lot39().lot40()
                        .lot41()
                        .lot42()
                        .lot43()
                        .lot44()
                        .lot45()
                        .lot46()
                        .screenshot());
    }

    /** Lớp hiển thị lô 50–53: chữ +EXP, đồng hồ phiên, mục tiêu cấp, đếm ngược reset, bảng phím F1, giảm VFX. */
    private void wireLot50Display(
            Lot50Windows lot50, dev.kage.client.core.ui.hud.CombatHud hud, CombatFeedbackPresenter feedback) {
        lot50.expGain().useSink(text -> feedback.showSelfText(text, FloatingCombatText.EXP_GAIN)); // ADR-331
        hud.useSessionClock(lot50.sessionClock()::elapsedSeconds); // ADR-332
        lot50.sessionClock().useHud(hud::setSessionClockVisible);
        lot50.levelGoal().useHud(hud::setLevelGoal); // ADR-334
        Lot53Windows lot53 = lot50.lot51().lot52().lot53();
        hud.useResetCountdown(lot53.resetCountdown()::secondsToReset); // ADR-356
        lot53.resetCountdown().useHud(hud::setResetCountdownVisible);
        hud.useKeyHelp(lot53::keyHelpText); // ADR-357
        feedback.useOthersVfxFilter(
                displaySettings.settings().switchFor(DisplaySettings.Option.HIDE_OTHERS_VFX)); // ADR-358
        Lot55Windows lot55 = lot53.lot54().lot55();
        hud.useLocalClock(lot55.hudExtras()::localMinuteOfDay); // ADR-372
        hud.useKillCount(lot39().lot40().lot41().lot42().lot43().session()::kills); // ADR-374
        lot55.hudExtras().useHud(hud::setLocalClockVisible, hud::setKillCountVisible);
        lot55.lot56().goldGain().useSink(text -> feedback.showSelfText(text, FloatingCombatText.GOLD_GAIN)); // ADR-379
        wireLot57Display(lot55.lot56().lot57(), hud, feedback);
        wireLot58Display(lot55.lot56().lot57().lot58(), hud, feedback);
    }

    /** Lớp hiển thị lô 57: rút gọn số sát thương (ADR-387), chữ bay tin riêng khi ẩn chat / ping cao (ADR-389/390). */
    private static void wireLot57Display(
            Lot57Windows lot57, dev.kage.client.core.ui.hud.CombatHud hud, CombatFeedbackPresenter feedback) {
        feedback.useCompactNumbers(lot57.compactDamage()::enabled);
        lot57.alerts().useSink(text -> feedback.showSelfText(text, FloatingCombatText.EMOTE));
        hud.useLatencyListener(lot57.alerts()::onLatency);
    }

    /** Lớp hiển thị lô 58: toạ độ trên HUD (ADR-396), công tắc ẩn nhãn mình (ADR-398). */
    private void wireLot58Display(
            Lot58Windows lot58, dev.kage.client.core.ui.hud.CombatHud hud, CombatFeedbackPresenter feedback) {
        lot58.chatView().useDisplaySettings(displaySettings.settings());
        lot58.chatView()
                .useCoordsHud(
                        meters -> hud.addCounterLine("hud.coords", meters, false),
                        lot39().lot40().lot41().lot42().coordinates()::meters);
        CombatTimerCoordinator timer = lot58.lot59().combatTimer(); // ADR-405
        timer.useHud(hud.addCounterLine("hud.combat_timer", timer::seconds, true));
        lot58.lot59() // ADR-411…414 — bốn dòng số lô 60
                .lot60()
                .hudStats()
                .useHud((line, value) -> hud.addCounterLine(line.prefixKey, value, line.clock));
        ChatLensCoordinator lens = lot58.lot59().lot60().lot61().lot62().lens(); // ADR-427 — tin mới khi ẩn chat
        lens.useHud(hud.addCounterLine("hud.unseen_chat", lens::unseenCount, false));
        wireLot63Display(lot58.lot59().lot60().lot61().lot62().lot63(), hud, feedback);
    }

    /** Lớp hiển thị lô 63: kỹ năng sẵn sàng (ADR-435), nhắc việc trên HUD (ADR-437), ẩn cụm dòng phụ (ADR-438). */
    private void wireLot63Display(
            Lot63Windows lot63, dev.kage.client.core.ui.hud.CombatHud hud, CombatFeedbackPresenter feedback) {
        HudPolishCoordinator polish = lot63.polish();
        polish.useSink(text -> feedback.showSelfText(text, FloatingCombatText.LEVEL_UP));
        hud.useSkillReadyListener(polish::onSkillReady);
        ReminderTimerCoordinator reminders = lot39().lot40().lot41().reminders();
        hud.addCounterLine("hud.next_reminder", () -> reminders.secondsToNext(System.currentTimeMillis()), true)
                .accept(true);
        polish.useInfoLines(hud::setInfoLinesVisible);
    }

    /**
     * Lệnh chat dùng MÓN ĐANG CHỌN trong túi: dùng nhiều (ADR-176), khoe vật phẩm (ADR-188), góp vật phẩm cho bang
     * (ADR-207); và tự tìm đường (ADR-206) lái nhân vật qua {@link LocalIntentSender}. Trả chính nó để nối tiếp.
     */
    ExpansionWindows useLocalInput(
            InventoryCoordinator inventory, LocalIntentSender intents, MailCoordinator mail, QuestCoordinator quests) {
        Supplier<Optional<Long>> selectedId = () -> inventory.selectedItem().map(InventoryController.Item::itemId);
        Lot49Windows lot49 = lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49();
        lot49.bagFilter().useItems(inventory::describedItems); // ADR-324
        lot49.newItems().useBag(inventory::isOpen); // ADR-326
        inventory.useNewMark(lot49.newItems()::isNew);
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .forward()
                .useMail(mail::selectedMail); // ADR-295
        lot31.bulkUse().useSelection(selectedId);
        lot31.lot32().itemLink().useSelection(inventory::selectedItem);
        Lot34Windows lot34 = lot31.lot32().lot33().lot34();
        intents.useAutoWalk(lot34.autoWalk().target());
        quests.useGoTo(lot34.autoWalk()::walkToNpc); // ADR-221 — nút "Đi tới" trong nhật ký nhiệm vụ
        lot34.lot35().guildItemDonation().useSelection(selectedId); // ADR-207 /gopdo
        lot34.lot35().partyMark().useTargeting(intents); // ADR-213 /danhdau, /theodau
        Lot36Windows lot36 = lot34.lot35().lot36();
        lot36.stackSplit().useSelection(selectedId); // ADR-215 /tach
        lot36.mailCleanup().useSelection(mail::selectedMailId); // ADR-217 /ghimthu
        lot36.lot37().combatLog().useLocalId(intents.localCharacterId()); // ADR-226 /nhatkychien
        lot36.lot37().nextQuest().useWalker(lot34.autoWalk()::walkToNpc); // ADR-228 /tieptheo di
        lot36.lot37().lot38().destroy().useSelection(selectedId); // ADR-231 /huy
        lot36.lot37().lot38().priceHint().useSelection(inventory::selectedItem); // ADR-234 /giacho
        Lot40Windows lot40 = lot36.lot37().lot38().lot39().lot40();
        lot40.bookmarks().useWalker(lot34.autoWalk().target()); // ADR-250 /vitri
        lot40.damageMeter().useLocalId(intents.localCharacterId()); // ADR-251 /satthuong
        lot40.bagSearch().useItems(inventory::describedItems); // ADR-254 /timdo
        lot40.lot41().enhancePreview().useSelection(selectedId); // ADR-257 /dutinh
        lot40.lot41().lot42().coordinates().usePosition(lot34.autoWalk().target(), zoneMap::state); // ADR-270
        lot40.lot41().lot42().lot43().session().useLocalId(intents.localCharacterId()); // ADR-271 đếm quái hạ
        useLot51Input(lot49.lot50().lot51(), intents.localCharacterId(), mail);
        return this;
    }

    /**
     * Lô 51–53: id nhân vật cho báo đầy máu (ADR-340) / rung màn hình (ADR-342), thư đang chọn cho lưu trữ (ADR-354).
     */
    private static void useLot51Input(Lot51Windows lot51, long localId, MailCoordinator mail) {
        lot51.fullHealth().useLocalId(localId);
        lot51.screenShake().useLocalId(localId);
        lot51.lot52().lot53().mailArchive().useSelection(mail::selectedMailId);
        Lot59Windows lot59 =
                lot51.lot52().lot53().lot54().lot55().lot56().lot57().lot58().lot59();
        lot59.combatTimer().useLocalId(localId);
        lot59.lot60().hudStats().useLocalId(localId); // ADR-413 — DPS của chính mình
    }

    /** ADR-276 — khung bạn bè xếp bạn yêu thích lên đầu. */
    ExpansionWindows useFriends(FriendCoordinator friends) {
        lot39().lot40().lot41().lot42().lot43().useFriends(friends);
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .friendPresence()
                .useIn(friends); // ADR-311
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .chatLines()
                .useFriends(friends::isFriendName); // ADR-364
        useLot58Friends(friends);
        return this;
    }

    /** ADR-397 — lọc chat chỉ bạn bè dùng chung nguồn "là bạn" với tô màu bạn bè. */
    private void useLot58Friends(FriendCoordinator friends) {
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot55()
                .lot56()
                .lot57()
                .lot58()
                .chatView()
                .useFriends(friends::isFriendName);
        useLot61Friends(friends);
    }

    /** ADR-421 — bong bóng chỉ bạn bè / người cùng nhóm. */
    private void useLot61Friends(FriendCoordinator friends) {
        ChatTidyCoordinator tidy = lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot55()
                .lot56()
                .lot57()
                .lot58()
                .lot59()
                .lot60()
                .lot61()
                .tidy();
        tidy.useRelations(friends::isFriendName, lot39().lot40().damageMeter()::isTracked);
        useLot62Friends(friends);
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .bubbles()
                .useAudience(tidy::bubbleAllowed);
    }

    /** ADR-430 — chế độ streamer giữ tên bạn bè. */
    private void useLot62Friends(FriendCoordinator friends) {
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot55()
                .lot56()
                .lot57()
                .lot58()
                .lot59()
                .lot60()
                .lot61()
                .lot62()
                .lens()
                .useFriends(friends::isFriendName);
    }

    /** ADR-323 / ADR-342 — nối thu phóng và rung camera vào camera thế giới 3D. */
    void useCamera(WorldModelRenderer models) {
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .cameraZoom()
                .useCamera(models::setZoom);
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .screenShake()
                .useCamera(models::shake); // ADR-342
    }

    /** Nối HUD chiến đấu: mục tiêu cho thuần phục (ADR-124), máu cho cảnh báo máu thấp (ADR-243). */
    void useCombat(CombatHudController combat) {
        taming.useTarget(combat::currentTargetId);
        lot39().lowHealth().useHealth(combat);
        lot39().lot40().lot41().monsterInfo().useTarget(combat); // ADR-255 /xemquai
        lot39().lot40()
                .lot41()
                .lot42()
                .lot43()
                .lot44()
                .lot45()
                .lot46()
                .lot47()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .screenShake()
                .useMaxHp(combat::playerMaxHp); // ADR-342
    }

    /** Luồng lô 39 (ADR-239…) — cho {@code WorldScreen} vẽ cảnh báo máu thấp / ẩn HUD. */
    Lot39Windows lot39() {
        return lot31.lot32().lot33().lot34().lot35().lot36().lot37().lot38().lot39();
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
