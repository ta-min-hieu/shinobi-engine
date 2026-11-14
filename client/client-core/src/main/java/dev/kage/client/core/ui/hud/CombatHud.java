package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * HUD combat tối thiểu (UI.md §3 — gộp {@code PlayerStatusHud}+{@code SkillBarHud} thành 1 lớp cho
 * bản demo đầu tiên, tách ra khi HUD có thêm bề mặt). Sở hữu {@link Stage} riêng — HUD luôn vẽ theo
 * toạ độ màn hình, không theo world unit như camera 3D/2D của {@code WorldScreen}.
 *
 * <p>CHỈ nhận dữ liệu qua setter, không tự đọc ECS/state gameplay (UI.md §4, §10) — caller
 * ({@code WorldScreen}, dựa trên {@code CombatHudController}) gọi các setter này mỗi tick.
 */
public final class CombatHud implements HudWindow {

    private static final float BADGE_SIZE = 52f;

    /** ADR-357 — bề rộng bảng phím tắt (pixel). */
    private static final float KEY_HELP_WIDTH = 520f;

    /**
     * Ô gợi ý phím. 46 → 52 (2026-10-01, song ngữ): "Chọn/Chuột" đã sát viền ở 46, "Target/Mouse"
     * thì tràn — đo bằng {@code HudButtonLabelFitTest}.
     */
    static final float SLOT_SIZE = 52f;

    /** Cỡ chữ (tỉ lệ) trong ô gợi ý phím — ô vuông {@link #SLOT_SIZE}, chữ phải vừa ở cả hai ngôn ngữ. */
    static final float HINT_LABEL_SCALE = 0.65f;

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;

    // Sáu cụm, mỗi cụm tự giữ widget + giá trị đang hiện của nó (tách 2026-10-17, dòng 58(d) — trước đó
    // 45 field rời trong một lớp). Lớp này chỉ còn bố cục và chuyển tiếp setter.
    private final HudVitals vitals;
    private final HudProgress progress;
    private final HudTargetFrame target;
    private final HudActionButtons actions;
    private final HudQuestCard quest;
    private final HudBanners banners;
    private java.util.function.LongConsumer latencyListener = roundTripMs -> {}; // ADR-390
    private Runnable skillReadyListener = () -> {}; // ADR-435
    private boolean skillWasReady = true;
    private final StringBuilder fpsText = new StringBuilder(); // ADR-283
    private final Table hintBar; // ADR-318
    private final Label fpsLabel;
    private int lastFps = -1;
    private final Label nearbyLabel; // ADR-325
    private final StringBuilder nearbyText = new StringBuilder(); // ADR-325
    private java.util.function.IntSupplier nearbyCount = () -> 0; // ADR-325
    private int lastNearby = -1;
    private final HudInfoLines infoLines; // ADR-332, ADR-334
    private final Table keyHelp = new Table(); // ADR-357
    private final Label keyHelpLabel;
    private java.util.function.Supplier<String> keyHelpText = () -> "";

    /**
     * Bố cục theo ảnh Ninja School Online chủ dự án gửi (2026-08-09): cụm chỉ số góc trên-trái, mục
     * tiêu giữa-trên, gợi ý phím giữa-dưới, nút hành động tròn góc dưới-phải.
     */
    public CombatHud() {
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.skin = new KageSkin();
        this.i18n = KageI18n.load();
        this.vitals = new HudVitals(skin);
        this.progress = new HudProgress(skin, i18n);
        this.target = new HudTargetFrame(skin, i18n);
        this.actions = new HudActionButtons(skin, i18n);
        this.quest = new HudQuestCard(skin, i18n);
        this.banners = new HudBanners(skin, i18n);
        this.fpsLabel = new Label("", skin.statLabelStyle);
        this.nearbyLabel = new Label("", skin.statLabelStyle);
        this.infoLines = new HudInfoLines(skin, i18n);
        this.keyHelpLabel = new Label("", skin.labelStyle);

        // 6 Table độc lập cùng setFillParent(true) — mỗi cụm neo một góc, không có lưới chung.
        stage.addActor(anchored(buildPlayerCluster(), Align.topLeft));
        stage.addActor(anchored(target.group(), Align.top));
        stage.addActor(anchored(banners.buildTopRightColumn(quest.group()), Align.topRight));
        stage.addActor(anchored(banners.duelGroup(), Align.left));
        this.hintBar = buildHintBar();
        stage.addActor(anchored(hintBar, Align.bottom));
        stage.addActor(anchored(actions.build(skin), Align.bottomRight));
        fpsLabel.setVisible(false); // ADR-283 — bật bằng /fps
        nearbyLabel.setVisible(false); // ADR-325 — bật bằng /xungquanh
        stage.addActor(anchored(fpsCorner(), Align.bottomLeft));
        keyHelpLabel.setWrap(true);
        keyHelp.setBackground(skin.panel);
        keyHelp.add(keyHelpLabel).width(KEY_HELP_WIDTH).pad(10f);
        keyHelp.setVisible(false); // ADR-357 — giữ F1 mới hiện
        stage.addActor(anchored(keyHelp, Align.center));
    }

    /** ADR-357 — giữ F1: hiện bảng phím tắt; chữ chỉ dựng lúc vừa nhấn, không mỗi khung. */
    private void updateKeyHelp() {
        boolean held = Gdx.input != null && Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.F1);
        if (held != keyHelp.isVisible()) {
            if (held) {
                keyHelpLabel.setText(keyHelpText.get());
            }
            keyHelp.setVisible(held);
        }
    }

    /** ADR-283 — cụm nhãn FPS góc dưới trái. */
    private Table fpsCorner() {
        Table corner = new Table();
        corner.add(infoLines.group()).left().row(); // ADR-332, ADR-334
        corner.add(nearbyLabel).left().row(); // ADR-325
        corner.add(fpsLabel).left();
        return corner;
    }

    /**
     * Lô 58+ — thêm một dòng số nhỏ ở góc dưới trái (ẩn sẵn): tiền tố là khoá i18n, giá trị đọc mỗi khung (âm = trống),
     * {@code clock} = viết dạng {@code H:MM:SS}. Trả công tắc hiện/ẩn.
     */
    public java.util.function.Consumer<Boolean> addCounterLine(
            String prefixKey, java.util.function.LongSupplier value, boolean clock) {
        return infoLines.addCounter(prefixKey, value, clock);
    }

    /** ADR-390 — nghe mỗi nhịp đo độ trễ (cảnh báo ping cao). */
    public void useLatencyListener(java.util.function.LongConsumer listener) {
        this.latencyListener = listener;
    }

    /** ADR-325 — nguồn số người chơi khác đang thấy (đọc mỗi khung, chỉ đổi chữ khi số đổi). */
    public void useNearbyCount(java.util.function.IntSupplier count) {
        this.nearbyCount = count;
    }

    /** ADR-325 — hiện / ẩn nhãn đếm người xung quanh. */
    public void setNearbyVisible(boolean visible) {
        nearbyLabel.setVisible(visible);
        lastNearby = -1;
    }

    /** ADR-332 — nguồn số giây đã chơi trong phiên (đọc mỗi khung, chỉ đổi chữ khi sang giây mới). */
    public void useSessionClock(java.util.function.LongSupplier seconds) {
        infoLines.useSessionSeconds(seconds);
    }

    /** ADR-332 — hiện / ẩn đồng hồ phiên chơi. */
    public void setSessionClockVisible(boolean visible) {
        infoLines.setClockVisible(visible);
    }

    /** ADR-356 — nguồn số giây tới reset ngày. */
    public void useResetCountdown(java.util.function.LongSupplier seconds) {
        infoLines.useResetCountdown(seconds);
    }

    /** ADR-356 — hiện / ẩn đếm ngược reset. */
    public void setResetCountdownVisible(boolean visible) {
        infoLines.setResetVisible(visible);
    }

    /** ADR-372 — nguồn số phút trong ngày theo giờ máy. */
    public void useLocalClock(java.util.function.IntSupplier minutesOfDay) {
        infoLines.useLocalClock(minutesOfDay);
    }

    /** ADR-372 — hiện / ẩn giờ máy. */
    public void setLocalClockVisible(boolean visible) {
        infoLines.setLocalClockVisible(visible);
    }

    /** ADR-374 — nguồn số quái đã hạ trong phiên. */
    public void useKillCount(java.util.function.IntSupplier kills) {
        infoLines.useKills(kills);
    }

    /** ADR-374 — hiện / ẩn đếm quái. */
    public void setKillCountVisible(boolean visible) {
        infoLines.setKillsVisible(visible);
    }

    /** ADR-357 — nguồn chữ bảng phím tắt (chỉ dựng khi vừa nhấn F1). */
    public void useKeyHelp(java.util.function.Supplier<String> text) {
        this.keyHelpText = text;
    }

    /** ADR-334 — dòng mục tiêu cấp độ đã dịch; rỗng = ẩn. */
    public void setLevelGoal(String text) {
        infoLines.setGoal(text);
    }

    /** ADR-316 — hiện / ẩn nhãn độ trễ mạng. */
    public void setLatencyVisible(boolean visible) {
        banners.setLatencyVisible(visible);
    }

    /** ADR-318 — HUD tối giản: ẩn thanh gợi ý phím và thẻ nhiệm vụ (máu, mục tiêu, nút chiêu vẫn hiện). */
    public void setMinimal(boolean minimal) {
        hintBar.setVisible(!minimal);
        quest.setSuppressed(minimal);
    }

    /** ADR-283 — hiện / ẩn số khung hình/giây. */
    public void setFpsVisible(boolean visible) {
        fpsLabel.setVisible(visible);
        lastFps = -1;
    }

    /** Bọc một cụm vào {@link Table} phủ toàn màn hình, neo về góc {@code align}. */
    private static Table anchored(Table content, int align) {
        Table container = new Table();
        container.setFillParent(true);
        container.align(align).add(content).pad(14f);
        return container;
    }

    /** Góc TRÊN-TRÁI: huy hiệu cấp (tròn) + thanh Máu + thanh Khí + vạch kinh nghiệm. */
    private Table buildPlayerCluster() {
        // Số ĐÈ LÊN thanh (Stack) thay vì nằm dòng riêng — giống bản gốc, tiết kiệm chiều cao.
        Table bars = new Table();
        bars.add(vitals.hpOverlay())
                .size(HudLayout.BAR_WIDTH, HudLayout.BAR_HEIGHT)
                .row();
        bars.add(vitals.chiOverlay())
                .size(HudLayout.BAR_WIDTH, HudLayout.BAR_HEIGHT)
                .padTop(4f)
                .row();
        // Vạch kinh nghiệm (chủ dự án 2026-08-09: "không có vạch kinh nghiệm để biết bao giờ lên
        // level à"). Mỏng hơn Máu/Khí — quan trọng nhưng không phải thứ phải liếc giữa lúc đánh.
        bars.add(progress.expOverlay())
                .size(HudLayout.BAR_WIDTH, HudLayout.EXP_BAR_HEIGHT)
                .padTop(4f);

        Table cluster = new Table();
        cluster.setBackground(skin.panel);
        cluster.add(progress.levelBadge(skin)).size(BADGE_SIZE).padRight(8f);
        cluster.add(bars);
        cluster.pad(8f);
        return cluster;
    }

    /**
     * GIỮA-DƯỚI: ô vuông kiểu thanh skill, nội dung là phím điều khiển. Bản gốc có 5 ô skill; ở đây
     * mới có 1 skill chủ động nên dùng chỗ này để nói cho người chơi biết phím nào làm gì (trước đó
     * không có chỗ nào nói cả).
     *
     * <p>Nhãn tiếng Việt qua i18n, KHÔNG dùng ký tự mũi tên ←/→: font Be Vietnam Pro không có glyph
     * đó nên chữ bị cắt cụt (đã thấy thật trên ảnh chụp preview).
     */
    private Table buildHintBar() {
        Table hintBar = new Table();
        hintBar.add(hintSlot(i18n.get("hud.hint.left"))).size(SLOT_SIZE).padRight(8f);
        hintBar.add(hintSlot(i18n.get("hud.hint.right"))).size(SLOT_SIZE).padRight(8f);
        hintBar.add(hintSlot(i18n.get("hud.hint.jump"))).size(SLOT_SIZE).padRight(8f);
        // 2 ô thêm 2026-08-09 cùng lượt với nhảy đôi/chọn mục tiêu bằng chuột: cả hai thao tác đều
        // KHÔNG tự khám phá ra được (chủ dự án chơi thử và không biết là có), nên phải nói ra ở đây
        // — đúng chỗ đã dùng để nói phím nào làm gì.
        hintBar.add(hintSlot(i18n.get("hud.hint.target"))).size(SLOT_SIZE).padRight(8f);
        hintBar.add(hintSlot(i18n.get("hud.hint.attack"))).size(SLOT_SIZE).padRight(8f);
        hintBar.add(hintSlot(i18n.get("hud.hint.skill"))).size(SLOT_SIZE);
        return hintBar;
    }

    /** Ô vuông gợi ý phím — hình dáng giống ô skill của bản gốc, nội dung là phím điều khiển. */
    private Table hintSlot(String text) {
        Label label = new Label(text, skin.statLabelStyle);
        label.setAlignment(Align.center);
        label.setFontScale(HINT_LABEL_SCALE);
        Table slot = new Table();
        slot.setBackground(skin.slot);
        slot.add(label).expand().center();
        return slot;
    }

    public void setOnSkillClicked(Runnable callback) {
        actions.setOnSkillClicked(callback);
    }

    /** ADR-133 — nút pháp bảo. */
    public void setOnArtifactClicked(Runnable callback) {
        actions.setOnArtifactClicked(callback);
    }

    /** ADR-133 — giây hồi chiêu pháp bảo còn lại (0 = sẵn sàng). */
    public void setArtifactCooldown(float remainingSeconds) {
        actions.setArtifactCooldown(remainingSeconds);
    }

    /** Nút Đánh (đánh thường) — luôn bấm được, server tự bỏ qua nếu chưa hết cooldown/ngoài tầm. */
    public void setOnAttackClicked(Runnable callback) {
        actions.setOnAttackClicked(callback);
    }

    /**
     * Round-trip time của nhịp heartbeat vừa về (system.proto {@code PONG}) — con số DUY NHẤT trên
     * HUD nói về đường truyền chứ không về nhân vật.
     *
     * <p>Bỏ qua khi trùng giá trị đang hiện: {@code Label#setText} dựng lại glyph layout, và độ trễ
     * đứng yên là trường hợp thường gặp nhất.
     */
    public void setLatency(long roundTripMs) {
        banners.setLatency(roundTripMs);
        latencyListener.accept(roundTripMs); // ADR-390
    }

    /** Quay về "chưa biết" — rời màn hình/mất kết nối, xem {@code ConnectionCoordinator#reset}. */
    public void clearLatency() {
        banners.clearLatency();
    }

    /** Cấp nhân vật hiện trên huy hiệu tròn góc trên-trái. */
    public void setLevel(int level) {
        progress.setLevel(level);
    }

    public void setPlayerChi(int current, int max) {
        vitals.setChi(current, max);
    }

    /** Đặt nhãn nút skill theo i18n key, trạng thái sẵn sàng — xem {@link #setSkillState}. */
    public void setSkillLabel(String i18nKey) {
        setSkillState(i18nKey, 0f);
    }

    /**
     * Nhãn nút skill kèm ĐẾM NGƯỢC hồi chiêu — người chơi thấy được đang dùng skill nào VÀ khi nào
     * dùng lại được. Trước đó nút chỉ mang tên tĩnh: bấm xong không có phản hồi nào trên màn hình,
     * không phân biệt được "đã dùng, đang hồi" với "bấm hụt" (chủ dự án phản hồi thật 2026-08-09,
     * cùng lượt với việc hiệu ứng skill chưa từng được vẽ — xem {@code SkillVfxRenderer}).
     */
    public void setSkillState(String i18nKey, float cooldownRemainingSeconds) {
        actions.setSkillState(i18nKey, cooldownRemainingSeconds);
    }

    /**
     * Vạch kinh nghiệm (2026-08-09) — số hiển thị là tiến độ TRONG cấp hiện tại, không phải tổng
     * EXP tích luỹ: "còn bao xa tới cấp sau" mới là câu hỏi người chơi đang hỏi.
     *
     * @param expForNextLevel {@code 0} = đã cấp tối đa, vẽ vạch đầy kèm nhãn riêng.
     */
    public void setExperience(long expIntoLevel, long expForNextLevel) {
        progress.setExperience(expIntoLevel, expForNextLevel);
    }

    public void setPlayerHp(int current, int max) {
        vitals.setHp(current, max);
    }

    public void setTarget(String monsterCode, int current, int max) {
        target.setMonster(monsterCode, current, max);
    }

    /**
     * Mục tiêu là một NHÂN VẬT (đối thủ đấu trường, 2026-10-04).
     *
     * <p>Khác quái ở hai chỗ: tên là tên người chơi do server gửi (không có khoá i18n nào cho nó), và
     * máu có thể CHƯA BIẾT — giao thức chỉ mang máu tối đa của người khác trong
     * {@code HealthChangedEvent}. Chưa biết máu tối đa thì ẨN vạch máu và chỉ hiện con số đã biết:
     * vẽ một tỉ lệ đoán mò là client tự bịa số (Điều 15).
     *
     * @param displayName tên đã sanitize
     * @param current máu hiện tại, âm = chưa biết
     * @param max máu tối đa, không dương = chưa biết
     */
    public void setCharacterTarget(String displayName, int current, int max) {
        target.setCharacter(displayName, current, max);
    }

    /** ADR-299 — số mét tới mục tiêu ({@code < 0} = ẩn). Chỉ đổi chữ khi số đổi. */
    public void setTargetDistance(int meters) {
        target.setDistance(meters);
    }

    public void clearTarget() {
        target.clear();
    }

    public void setSkillReady(boolean ready) {
        if (ready && !skillWasReady) {
            skillReadyListener.run(); // ADR-435 — vừa hết hồi chiêu
        }
        skillWasReady = ready;
        actions.setSkillReady(ready);
    }

    /** ADR-435 — nghe lúc kỹ năng vừa chuyển sang sẵn sàng. */
    public void useSkillReadyListener(Runnable listener) {
        this.skillReadyListener = listener;
    }

    /** ADR-438 — ẩn/hiện cả cụm dòng số nhỏ góc dưới trái (giữ nguyên công tắc riêng từng dòng). */
    public void setInfoLinesVisible(boolean visible) {
        infoLines.group().setVisible(visible);
    }

    /**
     * Hiện nhiệm vụ đang theo dõi.
     *
     * @param questCode mã nhiệm vụ — tra tên hiển thị qua khoá {@code quest.name.<code>}; thiếu bản
     *     dịch thì hiện chính mã, cùng lý do {@code DialoguePanel} làm vậy (nội dung nhiệm vụ nằm
     *     trong script GraalJS phía server, bản dịch nằm ở client, hai nhịp cập nhật khác nhau).
     * @param completed true = đã xong, bật nút lĩnh thưởng.
     */
    public void setQuest(String questCode, boolean completed) {
        quest.show(questCode, completed);
    }

    public void clearQuest() {
        quest.hide();
    }

    /**
     * Bật biển "đang đấu tay đôi" (2026-08-27).
     *
     * @param text chuỗi ĐÃ dịch và ĐÃ sanitize — HUD không tra i18n cho dữ liệu đến từ mạng, cùng
     *     quy ước {@code GuildPanel#setStatus} (tên đối thủ do người khác đặt, khoá i18n không dịch
     *     được một cái tên).
     */
    public void setDuelStatus(String text) {
        banners.setDuelStatus(text);
    }

    /** Trận đấu kết thúc — biển biến mất hẳn, không để lại tên đối thủ cũ treo trên màn hình. */
    public void clearDuelStatus() {
        banners.clearDuelStatus();
    }

    /**
     * Dải chữ "đang ở trong hầm ngục" (2026-08-27) — hiện thường trực ở góc trên-phải, ngay trên
     * cụm nhiệm vụ.
     *
     * <p>Phải là dấu hiệu THƯỜNG TRỰC chứ không phải một dòng thoáng qua trong nhật ký chat: hầm
     * ngục lát cắt này là một map TRỐNG (ADR-026 — chỉ chứng minh cơ chế tách world), nên nhìn bằng
     * mắt không có cách nào phân biệt nó với world chính. Không nói ra thì người chơi đứng trong
     * instance mà tưởng nút vừa bấm không ăn.
     *
     * @param banner chuỗi ĐÃ dịch, hoặc RỖNG để giấu cả cụm — panel không tự tra i18n cho một trạng
     *     thái nó không theo dõi (UI.md §4).
     * @param exitHint dòng nhỏ bên dưới nói cách ra, cũng đã dịch sẵn.
     */
    public void setDungeonBanner(String banner, String exitHint) {
        banners.setDungeonBanner(banner, exitHint);
    }

    /**
     * Biển vùng (ADR-141) — "Vùng PvP" khi đứng trong dải PvP tự do, rỗng để giấu. Thường trực như dải hầm ngục: bước
     * vào dải là bị người khác đánh được, một dòng chat thoáng qua là không đủ.
     *
     * @param text chuỗi ĐÃ dịch, hoặc rỗng
     * @param color màu chữ — KHÔNG phải thông tin duy nhất (chữ đã nói đủ, UI.md)
     */
    public void setZoneBanner(String text, Color color) {
        banners.setZoneBanner(text, color);
    }

    /** ADR-157 — bảng sát thương boss thế giới trực tiếp (chữ đã dịch, nhiều dòng); rỗng = ẩn. */
    public void setBossBoard(String text) {
        banners.setBossBoard(text);
    }

    /** ADR-164 — dòng "x2 EXP" (chữ đã dịch); rỗng = ẩn. */
    public void setExpBuff(String text) {
        banners.setExpBuff(text);
    }

    public void setOnClaimQuestClicked(Runnable listener) {
        quest.setOnClaimClicked(listener);
    }

    @Override
    public Stage stage() {
        return stage;
    }

    /**
     * Bộ style dùng chung — cho {@code DialoguePanel} MƯỢN. HUD giữ quyền sở hữu: người mượn KHÔNG
     * được dispose. Dựng {@link KageSkin} lần thứ hai là sinh lại toàn bộ font FreeType và mọi
     * texture nền chỉ để có đúng cùng một bộ màu.
     */
    public KageSkin skin() {
        return skin;
    }

    /**
     * Font dùng chung của HUD — cho {@code FloatingCombatText} MƯỢN để vẽ số damage, tránh nạp một
     * bản font thứ hai. HUD vẫn giữ quyền sở hữu: người mượn KHÔNG được dispose.
     */
    public BitmapFont font() {
        return skin.font();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render(float deltaTime) {
        if (fpsLabel.isVisible() && Gdx.graphics.getFramesPerSecond() != lastFps) {
            lastFps = Gdx.graphics.getFramesPerSecond();
            fpsText.setLength(0);
            fpsText.append("FPS ").append(lastFps); // tái dùng bộ đệm — không cấp phát mỗi khung
            fpsLabel.setText(fpsText);
        }
        if (nearbyLabel.isVisible() && nearbyCount.getAsInt() != lastNearby) {
            lastNearby = nearbyCount.getAsInt();
            nearbyText.setLength(0);
            nearbyText.append(i18n.get("hud.nearby")).append(' ').append(lastNearby); // tái dùng bộ đệm
            nearbyLabel.setText(nearbyText);
        }
        infoLines.update(); // ADR-332
        updateKeyHelp(); // ADR-357
        stage.act(deltaTime);
        stage.draw();
    }

    @Override
    public void dispose() {
        // Stage KHÔNG tự dispose batch truyền vào constructor (ownsBatch=false) — thiếu dòng này là rò
        // một SpriteBatch (mesh + shader) mỗi lần dựng lại màn world (InboundPacketFuzzTest).
        stage.getBatch().dispose();
        stage.dispose();
        skin.dispose();
    }
}
