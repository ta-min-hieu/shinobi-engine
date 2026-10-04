package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.widget.HealthBar;

/**
 * HUD combat tối thiểu (UI.md §3 — gộp {@code PlayerStatusHud}+{@code SkillBarHud} thành 1 lớp cho
 * bản demo đầu tiên, tách ra khi HUD có thêm bề mặt). Sở hữu {@link Stage} riêng — HUD luôn vẽ theo
 * toạ độ màn hình, không theo world unit như camera 3D/2D của {@code WorldScreen}.
 *
 * <p>CHỈ nhận dữ liệu qua setter, không tự đọc ECS/state gameplay (UI.md §4, §10) — caller
 * ({@code WorldScreen}, dựa trên {@code CombatHudController}) gọi các setter này mỗi tick.
 */
public final class CombatHud implements HudWindow {

    private static final float BAR_WIDTH = 190f;
    private static final float BAR_HEIGHT = 20f;
    /**
     * Vạch EXP mỏng hơn Máu/Khí nhưng KHÔNG mỏng hơn được nữa: 12f (thử đầu tiên) làm nhãn số cao
     * hơn ô chứa và tràn hẳn ra ngoài khung panel — thấy thật trên ảnh chụp preview 2026-08-09.
     * Đi kèm {@link #EXP_LABEL_SCALE} thu nhỏ chữ cho vừa.
     */
    private static final float EXP_BAR_HEIGHT = 16f;

    private static final float EXP_LABEL_SCALE = 0.7f;
    private static final float BADGE_SIZE = 52f;
    /**
     * Ô gợi ý phím. 46 → 52 (2026-10-01, song ngữ): "Chọn/Chuột" đã sát viền ở 46, "Target/Mouse"
     * thì tràn — đo bằng {@code HudButtonLabelFitTest}.
     */
    static final float SLOT_SIZE = 52f;

    /** Cỡ chữ (tỉ lệ) trong ô gợi ý phím — ô vuông {@link #SLOT_SIZE}, chữ phải vừa ở cả hai ngôn ngữ. */
    static final float HINT_LABEL_SCALE = 0.65f;

    private static final float ACTION_BUTTON_SIZE = 96f;

    /** Nút chiêu nhỏ hơn nút Đánh — thứ bậc thị giác: đánh thường là hành động chính (2026-08-09). */
    private static final float SKILL_BUTTON_SIZE = 76f;

    private static final float NAME_PLATE_PAD_Y = 4f;

    /**
     * Khung biển tên là texture 64px kéo giãn ({@code KageSkin.framed}), nên viền 2px phồng lên ~4px ở bề
     * rộng ~130px. Lề ngang 4px làm nhãn dài như "Flamesphere" đè lên viền (kiểm sống 2026-10-02).
     */
    private static final float NAME_PLATE_PAD_X = 10f;

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;

    private final Label levelLabel;
    private final HealthBar playerHealthBar;
    private final Label playerHpLabel;
    private final HealthBar playerChiBar;
    private final Label playerChiLabel;
    private final HealthBar expBar;
    private final Label expLabel;
    private final Table targetGroup;
    private final Label targetNameLabel;
    private final HealthBar targetHealthBar;
    private final Label targetHpLabel;
    private final TextButton skillButton;
    private final Label skillNameLabel;
    private final TextButton attackButton;

    /** Cụm nhiệm vụ (góc TRÊN-PHẢI) — ẩn hẳn khi không theo dõi nhiệm vụ nào. */
    private final Table questGroup;

    /** Biển "đang đấu tay đôi" (rìa TRÁI, giữa chiều cao) — ẩn hẳn khi không ở trong trận nào. */
    private final Table duelGroup;

    private final Label duelLabel;

    private final Label questNameLabel;
    private final Label questStatusLabel;
    private final TextButton claimQuestButton;

    /** Cụm "đang ở trong hầm ngục" — xem {@link #setDungeonBanner}. Ẩn cho tới khi có gì để nói. */
    private final Table dungeonGroup;

    private final Label dungeonLabel;

    /** Độ trễ đường truyền, đo từ {@code PongResponse} — xem {@link #setLatency(long)}. */
    private final Label latencyLabel;

    private final Label dungeonHintLabel;

    /** Ô chứa {@link #dungeonGroup} trong cột góc trên-phải — xem {@link #buildTopRightColumn()}. */
    private Cell<Table> dungeonCell;

    private Runnable onSkillClicked;
    private Runnable onAttackClicked;
    private Runnable onClaimQuestClicked;

    private int lastPlayerHp = -1;
    private int lastPlayerMaxHp = -1;
    private String lastTargetCode;
    /** Tên nhân vật đang là mục tiêu (đối thủ đấu trường) — loại trừ với {@link #lastTargetCode}. */
    private String lastCharacterTargetName;

    private int lastTargetHp = -1;
    private int lastTargetMaxHp = -1;
    private String lastSkillText;
    private final CooldownText cooldownText = new CooldownText();
    private String lastDuelText;
    private int lastLevel = -1;
    private int lastPlayerChi = -1;
    private int lastPlayerMaxChi = -1;
    private long lastExpIntoLevel = -1L;
    private long lastExpForNextLevel = -1L;

    /** -1 = chưa nhận nhịp heartbeat nào, xem {@link #clearLatency()}. */
    private long lastLatencyMs = -1L;

    /**
     * Bố cục theo ảnh Ninja School Online chủ dự án gửi (2026-08-09): cụm chỉ số góc trên-trái, mục
     * tiêu giữa-trên, gợi ý phím giữa-dưới, nút hành động tròn góc dưới-phải.
     */
    public CombatHud() {
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.skin = new KageSkin();
        this.i18n = KageI18n.load();

        this.levelLabel = new Label("1", skin.statLabelStyle);
        this.playerHealthBar = new HealthBar(skin);
        this.playerHpLabel = new Label("", skin.statLabelStyle);
        this.playerChiBar = new HealthBar(skin, HealthBar.Kind.CHI);
        this.playerChiLabel = new Label("", skin.statLabelStyle);
        this.expBar = new HealthBar(skin, HealthBar.Kind.EXPERIENCE);
        this.expLabel = new Label("", skin.statLabelStyle);
        this.expLabel.setFontScale(EXP_LABEL_SCALE);
        this.targetNameLabel = new Label("", skin.labelStyle);
        this.targetHealthBar = new HealthBar(skin);
        this.targetHpLabel = new Label("", skin.statLabelStyle);
        this.targetGroup = new Table();
        this.skillButton = new TextButton("", skin.roundButtonStyle);
        this.attackButton = new TextButton("", skin.roundButtonStyle);
        this.skillNameLabel = new Label("", skin.statLabelStyle);
        this.questNameLabel = new Label("", skin.labelStyle);
        this.questStatusLabel = new Label("", skin.statLabelStyle);
        this.claimQuestButton = new TextButton(i18n.get("quest.action.claim"), skin.buttonStyle);
        this.questGroup = new Table();
        this.duelLabel = new Label("", skin.labelStyle);
        this.duelGroup = new Table();
        this.dungeonLabel = new Label("", skin.labelStyle);
        this.dungeonHintLabel = new Label("", skin.statLabelStyle);
        this.dungeonGroup = new Table();
        this.latencyLabel = new Label(i18n.get("hud.latency_unknown"), skin.statLabelStyle);
        this.latencyLabel.setFontScale(EXP_LABEL_SCALE);

        // 6 Table độc lập cùng setFillParent(true) — mỗi cụm neo một góc, không có lưới chung.
        stage.addActor(anchored(buildPlayerCluster(), Align.topLeft));
        stage.addActor(anchored(buildTargetCluster(), Align.top));
        stage.addActor(anchored(buildTopRightColumn(), Align.topRight));
        stage.addActor(anchored(buildDuelBanner(), Align.left));
        stage.addActor(anchored(buildHintBar(), Align.bottom));
        stage.addActor(anchored(buildSkillCluster(), Align.bottomRight));
    }

    /** Bọc một cụm vào {@link Table} phủ toàn màn hình, neo về góc {@code align}. */
    private static Table anchored(Table content, int align) {
        Table container = new Table();
        container.setFillParent(true);
        container.align(align).add(content).pad(14f);
        return container;
    }

    /** Góc TRÊN-TRÁI: huy hiệu cấp (tròn) + thanh Máu + thanh Khí. */
    private Table buildPlayerCluster() {
        levelLabel.setAlignment(Align.center);
        Table levelBadge = new Table();
        levelBadge.setBackground(skin.roundButton);
        levelBadge.add(levelLabel);

        // Số ĐÈ LÊN thanh (Stack) thay vì nằm dòng riêng — giống bản gốc, tiết kiệm chiều cao.
        Table bars = new Table();
        bars.add(overlay(playerHealthBar, playerHpLabel))
                .size(BAR_WIDTH, BAR_HEIGHT)
                .row();
        bars.add(overlay(playerChiBar, playerChiLabel))
                .size(BAR_WIDTH, BAR_HEIGHT)
                .padTop(4f)
                .row();
        // Vạch kinh nghiệm (chủ dự án 2026-08-09: "không có vạch kinh nghiệm để biết bao giờ lên
        // level à"). Mỏng hơn Máu/Khí — quan trọng nhưng không phải thứ phải liếc giữa lúc đánh.
        bars.add(overlay(expBar, expLabel)).size(BAR_WIDTH, EXP_BAR_HEIGHT).padTop(4f);

        Table cluster = new Table();
        cluster.setBackground(skin.panel);
        cluster.add(levelBadge).size(BADGE_SIZE).padRight(8f);
        cluster.add(bars);
        cluster.pad(8f);
        return cluster;
    }

    /** GIỮA-TRÊN: tên + thanh máu mục tiêu. Ẩn cho tới khi có mục tiêu. */
    private Table buildTargetCluster() {
        targetGroup.setBackground(skin.panel);
        targetGroup.add(targetNameLabel).row();
        targetGroup
                .add(overlay(targetHealthBar, targetHpLabel))
                .size(BAR_WIDTH, BAR_HEIGHT)
                .padTop(4f);
        targetGroup.pad(8f);
        targetGroup.setVisible(false);
        return targetGroup;
    }

    /**
     * Góc TRÊN-PHẢI, một CỘT: dải hầm ngục ở trên, cụm nhiệm vụ ở dưới.
     *
     * <p>Cột chung chứ không phải hai {@code Table} cùng {@code setFillParent(true)} neo cùng góc —
     * hai cái như thế sẽ vẽ chồng lên nhau, không xếp dọc.
     */
    private Table buildTopRightColumn() {
        buildDungeonCluster();
        Table column = new Table();
        // Độ trễ nằm trên cùng cột phải: một dòng chữ nhỏ, LUÔN có chỗ (khác dải hầm ngục bên dưới)
        // — nó có chữ ngay từ khung hình đầu ("— ms"), nên không có ô rỗng nào để đội bố cục về sau.
        column.add(latencyLabel).right().row();
        // GỠ actor ra khỏi ô thay vì setVisible(false): một Table của scene2d vẫn dành chỗ theo kích
        // thước ưa thích của con nó dù con đang ẩn, nên hai cái Label rỗng bên trong sẽ đẩy cụm
        // nhiệm vụ tụt xuống ~2 dòng suốt cả phiên chơi ngoài hầm ngục. Ô rỗng thì cao 0.
        this.dungeonCell = column.add((Table) null).right();
        column.row();
        column.add(buildQuestCluster()).right().padTop(8f).row();
        return column;
    }

    /**
     * Round-trip time của nhịp heartbeat vừa về (system.proto {@code PONG}) — con số DUY NHẤT trên
     * HUD nói về đường truyền chứ không về nhân vật.
     *
     * <p>Bỏ qua khi trùng giá trị đang hiện: {@code Label#setText} dựng lại glyph layout, và độ trễ
     * đứng yên là trường hợp thường gặp nhất.
     */
    public void setLatency(long roundTripMs) {
        if (roundTripMs == lastLatencyMs) {
            return;
        }
        lastLatencyMs = roundTripMs;
        latencyLabel.setText(i18n.format("hud.latency", roundTripMs));
    }

    /** Quay về "chưa biết" — rời màn hình/mất kết nối, xem {@code ConnectionCoordinator#reset}. */
    public void clearLatency() {
        lastLatencyMs = -1L;
        latencyLabel.setText(i18n.get("hud.latency_unknown"));
    }

    /** Dải "đang ở trong hầm ngục" — xem {@link #setDungeonBanner} về việc vì sao nó phải thường trực. */
    private void buildDungeonCluster() {
        dungeonGroup.setBackground(skin.panel);
        dungeonGroup.add(dungeonLabel).row();
        dungeonGroup.add(dungeonHintLabel).padTop(2f).row();
        dungeonGroup.pad(8f);
    }

    /**
     * Cụm nhiệm vụ đang theo dõi + nút lĩnh thưởng (nằm dưới dải hầm ngục trong cột góc trên-phải).
     *
     * <p>KHÔNG có "đã giết 2/3": server cố ý không gửi tiến độ từng bước (quest.proto — chỉ phát
     * khi quest VỪA chuyển COMPLETED, để không quá tải World tick), nên con số đó đơn giản là không
     * tồn tại ở phía client. Hiện "Đang làm" là tất cả những gì nói được mà không bịa.
     */
    private Table buildQuestCluster() {
        claimQuestButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onClaimQuestClicked != null) {
                    onClaimQuestClicked.run();
                }
            }
        });
        questGroup.setBackground(skin.panel);
        questGroup.add(questNameLabel).row();
        questGroup.add(questStatusLabel).padTop(2f).row();
        questGroup.add(claimQuestButton).padTop(6f).growX();
        questGroup.pad(8f);
        questGroup.setVisible(false);
        return questGroup;
    }

    /**
     * RÌA TRÁI, giữa chiều cao: biển "đang đấu tay đôi với X" (2026-08-27).
     *
     * <p>Rìa trái vì bốn góc + cạnh trên + cạnh dưới đã có chủ, và vì đây là thứ phải NHÌN THẤY
     * suốt trận chứ không phải liếc một lần: đang trong trận đấu tay đôi thì mọi thứ khác trên HUD
     * (mục tiêu, nhiệm vụ) vẫn hiện y hệt lúc thường, không có gì nói cho người chơi biết mình đang
     * ở trong một trận đấu tính điểm.
     *
     * <p>Tên đối thủ do NGƯỜI KHÁC đặt nên chuỗi truyền vào phải đã qua {@code ChatController
     * .sanitize} — {@code DuelCoordinator} lo việc đó, HUD chỉ vẽ (UI.md §4).
     */
    private Table buildDuelBanner() {
        duelGroup.setBackground(skin.panel);
        duelGroup.add(duelLabel).pad(8f);
        duelGroup.setVisible(false);
        return duelGroup;
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

    /**
     * GÓC DƯỚI-PHẢI: hai nút tròn — <b>Chiêu</b> bên trái, <b>Đánh</b> bên phải — mỗi nút một biển
     * tên phía trên.
     *
     * <p>Nút Đánh thêm 2026-08-09 khi chủ dự án đổi đánh thường sang bấm tay. Nó nằm NGOÀI CÙNG bên
     * phải và to hơn nút chiêu: đó là nút bấm nhiều nhất trong trận, đúng chỗ ngón cái chạm tới
     * trên cảm ứng (UI.md — nút ≥48dp) và đúng vị trí nút đánh của bản gốc Ninja School.
     */
    private Table buildSkillCluster() {
        skillButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onSkillClicked != null) {
                    onSkillClicked.run();
                }
            }
        });
        attackButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onAttackClicked != null) {
                    onAttackClicked.run();
                }
            }
        });
        skillNameLabel.setAlignment(Align.center);

        // Mỗi nút một CỘT riêng (biển tên + nút), canh đáy: hai nút khác cỡ nằm chung một lưới 2
        // hàng sẽ kéo hàng biển tên giãn theo nút cao nhất, để lại khoảng trống lơ lửng giữa tên và
        // nút — thấy thật trên ảnh chụp preview 2026-08-09.
        Table cluster = new Table();
        cluster.add(buttonColumn(namePlate(skillNameLabel), skillButton, SKILL_BUTTON_SIZE))
                .bottom()
                .padRight(10f);
        cluster.add(buttonColumn(
                        namePlate(new Label(i18n.get("hud.action.attack"), skin.statLabelStyle)),
                        attackButton,
                        ACTION_BUTTON_SIZE))
                .bottom();
        return cluster;
    }

    private static Table buttonColumn(Table plate, TextButton button, float buttonSize) {
        Table column = new Table();
        column.add(plate).padBottom(6f).row();
        column.add(button).size(buttonSize);
        return column;
    }

    /** Khung nền cho biển tên — chữ trần trên nền map sáng (cỏ/tuyết) gần như không đọc được. */
    private Table namePlate(Label label) {
        label.setAlignment(Align.center);
        Table plate = new Table();
        plate.setBackground(skin.panel);
        plate.add(label).pad(NAME_PLATE_PAD_Y, NAME_PLATE_PAD_X, NAME_PLATE_PAD_Y, NAME_PLATE_PAD_X);
        return plate;
    }

    /** Chồng nhãn số lên giữa thanh — kiểu Ninja School, số nằm TRONG thanh chứ không dưới thanh. */
    private static Stack overlay(HealthBar bar, Label label) {
        label.setAlignment(Align.center);
        Stack stack = new Stack();
        stack.add(bar);
        stack.add(label);
        return stack;
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
        this.onSkillClicked = callback;
    }

    /** Nút Đánh (đánh thường) — luôn bấm được, server tự bỏ qua nếu chưa hết cooldown/ngoài tầm. */
    public void setOnAttackClicked(Runnable callback) {
        this.onAttackClicked = callback;
    }

    /** Cấp nhân vật hiện trên huy hiệu tròn góc trên-trái. */
    public void setLevel(int level) {
        if (level == lastLevel) {
            return; // UI.md §9
        }
        lastLevel = level;
        levelLabel.setText(String.valueOf(level));
    }

    public void setPlayerChi(int current, int max) {
        if (current == lastPlayerChi && max == lastPlayerMaxChi) {
            return; // UI.md §9
        }
        lastPlayerChi = current;
        lastPlayerMaxChi = max;
        playerChiBar.setValues(current, max);
        playerChiLabel.setText(current + " / " + max);
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
        skillNameLabel.setText(i18n.get(i18nKey));
        // Nút TRÒN nhỏ không đủ chỗ cho tên + số: tên nằm nhãn phía trên, trong nút chỉ còn đếm
        // ngược (rỗng khi sẵn sàng — nút sáng lên là đủ tín hiệu "dùng được").
        String text = cooldownText.textFor(cooldownRemainingSeconds);
        if (text.equals(lastSkillText)) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        lastSkillText = text;
        skillButton.setText(text);
    }

    /**
     * Vạch kinh nghiệm (2026-08-09) — số hiển thị là tiến độ TRONG cấp hiện tại, không phải tổng
     * EXP tích luỹ: "còn bao xa tới cấp sau" mới là câu hỏi người chơi đang hỏi.
     *
     * @param expForNextLevel {@code 0} = đã cấp tối đa, vẽ vạch đầy kèm nhãn riêng.
     */
    public void setExperience(long expIntoLevel, long expForNextLevel) {
        if (expIntoLevel == lastExpIntoLevel && expForNextLevel == lastExpForNextLevel) {
            return; // UI.md §9
        }
        lastExpIntoLevel = expIntoLevel;
        lastExpForNextLevel = expForNextLevel;
        if (expForNextLevel <= 0L) {
            expBar.setValues(1L, 1L);
            expLabel.setText(i18n.get("hud.exp.max"));
            return;
        }
        expBar.setValues(expIntoLevel, expForNextLevel);
        expLabel.setText(expIntoLevel + " / " + expForNextLevel);
    }

    public void setPlayerHp(int current, int max) {
        if (current == lastPlayerHp && max == lastPlayerMaxHp) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        lastPlayerHp = current;
        lastPlayerMaxHp = max;
        playerHealthBar.setValues(current, max);
        playerHpLabel.setText(current + " / " + max);
    }

    public void setTarget(String monsterCode, int current, int max) {
        targetGroup.setVisible(true);
        if (!monsterCode.equals(lastTargetCode)) {
            lastTargetCode = monsterCode;
            lastCharacterTargetName = null;
            targetNameLabel.setText(i18n.get("hud.monster." + monsterCode));
            // Mục tiêu trước có thể là nhân vật chưa biết máu tối đa (vạch máu đang ẩn) — vẽ lại hết.
            lastTargetHp = -1;
            lastTargetMaxHp = -1;
            targetHealthBar.setVisible(true);
        }
        if (current == lastTargetHp && max == lastTargetMaxHp) {
            return;
        }
        lastTargetHp = current;
        lastTargetMaxHp = max;
        targetHealthBar.setValues(current, max);
        targetHpLabel.setText(current + " / " + max);
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
        targetGroup.setVisible(true);
        if (!displayName.equals(lastCharacterTargetName)) {
            lastCharacterTargetName = displayName;
            lastTargetCode = null;
            targetNameLabel.setText(displayName);
            lastTargetHp = Integer.MIN_VALUE;
            lastTargetMaxHp = Integer.MIN_VALUE;
        }
        if (current == lastTargetHp && max == lastTargetMaxHp) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        lastTargetHp = current;
        lastTargetMaxHp = max;
        boolean maxKnown = max > 0 && current >= 0;
        targetHealthBar.setVisible(maxKnown);
        if (maxKnown) {
            targetHealthBar.setValues(current, max);
            targetHpLabel.setText(current + " / " + max);
        } else if (current >= 0) {
            targetHpLabel.setText(KageI18n.formatOrKey(i18n, "hud.target.hp_partial", String.valueOf(current)));
        } else {
            targetHpLabel.setText(KageI18n.textOrKey(i18n, "hud.target.hp_unknown"));
        }
    }

    public void clearTarget() {
        if (lastTargetCode == null && lastCharacterTargetName == null) {
            return;
        }
        lastTargetCode = null;
        lastCharacterTargetName = null;
        lastTargetHp = -1;
        lastTargetMaxHp = -1;
        targetGroup.setVisible(false);
    }

    public void setSkillReady(boolean ready) {
        skillButton.setDisabled(!ready); // Button tự chặn click khi disabled, không cần gate thêm.
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
        questNameLabel.setText(translateOrKey("quest.name." + questCode));
        questStatusLabel.setText(i18n.get(completed ? "quest.status.completed" : "quest.status.in_progress"));
        claimQuestButton.setVisible(completed);
        questGroup.setVisible(true);
    }

    public void clearQuest() {
        questGroup.setVisible(false);
    }

    /**
     * Bật biển "đang đấu tay đôi" (2026-08-27).
     *
     * @param text chuỗi ĐÃ dịch và ĐÃ sanitize — HUD không tra i18n cho dữ liệu đến từ mạng, cùng
     *     quy ước {@code GuildPanel#setStatus} (tên đối thủ do người khác đặt, khoá i18n không dịch
     *     được một cái tên).
     */
    public void setDuelStatus(String text) {
        if (text.equals(lastDuelText)) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        lastDuelText = text;
        duelLabel.setText(text);
        duelGroup.setVisible(true);
    }

    /** Trận đấu kết thúc — biển biến mất hẳn, không để lại tên đối thủ cũ treo trên màn hình. */
    public void clearDuelStatus() {
        lastDuelText = null;
        duelGroup.setVisible(false);
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
        dungeonLabel.setText(banner);
        dungeonHintLabel.setText(exitHint);
        dungeonCell.setActor(banner.isEmpty() ? null : dungeonGroup);
    }

    public void setOnClaimQuestClicked(Runnable listener) {
        this.onClaimQuestClicked = listener;
    }

    private String translateOrKey(String key) {
        return KageI18n.textOrKey(i18n, key);
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
        stage.act(deltaTime);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
