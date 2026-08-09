package dev.kage.client.core.ui.hud;

import java.util.Locale;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
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
public final class CombatHud implements Disposable {

    private static final float BAR_WIDTH = 190f;
    private static final float BAR_HEIGHT = 20f;
    private static final float EXP_BAR_HEIGHT = 12f;
    private static final float BADGE_SIZE = 52f;
    private static final float SLOT_SIZE = 46f;
    private static final float ACTION_BUTTON_SIZE = 96f;

    /** Nút chiêu nhỏ hơn nút Đánh — thứ bậc thị giác: đánh thường là hành động chính (2026-08-09). */
    private static final float SKILL_BUTTON_SIZE = 76f;

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

    private Runnable onSkillClicked;
    private Runnable onAttackClicked;

    private int lastPlayerHp = -1;
    private int lastPlayerMaxHp = -1;
    private String lastTargetCode;
    private int lastTargetHp = -1;
    private int lastTargetMaxHp = -1;
    private String lastSkillText;
    private int lastLevel = -1;
    private int lastPlayerChi = -1;
    private int lastPlayerMaxChi = -1;
    private long lastExpIntoLevel = -1L;
    private long lastExpForNextLevel = -1L;

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
        this.targetNameLabel = new Label("", skin.labelStyle);
        this.targetHealthBar = new HealthBar(skin);
        this.targetHpLabel = new Label("", skin.statLabelStyle);
        this.targetGroup = new Table();
        this.skillButton = new TextButton("", skin.roundButtonStyle);
        this.attackButton = new TextButton("", skin.roundButtonStyle);
        this.skillNameLabel = new Label("", skin.statLabelStyle);

        // 4 Table độc lập cùng setFillParent(true) — mỗi cụm neo một góc, không có lưới chung.
        stage.addActor(anchored(buildPlayerCluster(), Align.topLeft));
        stage.addActor(anchored(buildTargetCluster(), Align.top));
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

        Table cluster = new Table();
        cluster.add(namePlate(skillNameLabel)).padBottom(6f).padRight(10f);
        cluster.add(namePlate(new Label(i18n.get("hud.action.attack"), skin.statLabelStyle)))
                .padBottom(6f)
                .row();
        cluster.add(skillButton).size(SKILL_BUTTON_SIZE).padRight(10f);
        cluster.add(attackButton).size(ACTION_BUTTON_SIZE);
        return cluster;
    }

    /** Khung nền cho biển tên — chữ trần trên nền map sáng (cỏ/tuyết) gần như không đọc được. */
    private Table namePlate(Label label) {
        label.setAlignment(Align.center);
        Table plate = new Table();
        plate.setBackground(skin.panel);
        plate.add(label).pad(4f);
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
        label.setFontScale(0.65f);
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
        String text = cooldownRemainingSeconds > 0f ? String.format(Locale.ROOT, "%.1f", cooldownRemainingSeconds) : "";
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
            targetNameLabel.setText(i18n.get("hud.monster." + monsterCode));
        }
        if (current == lastTargetHp && max == lastTargetMaxHp) {
            return;
        }
        lastTargetHp = current;
        lastTargetMaxHp = max;
        targetHealthBar.setValues(current, max);
        targetHpLabel.setText(current + " / " + max);
    }

    public void clearTarget() {
        if (lastTargetCode == null) {
            return;
        }
        lastTargetCode = null;
        lastTargetHp = -1;
        lastTargetMaxHp = -1;
        targetGroup.setVisible(false);
    }

    public void setSkillReady(boolean ready) {
        skillButton.setDisabled(!ready); // Button tự chặn click khi disabled, không cần gate thêm.
    }

    public Stage stage() {
        return stage;
    }

    /**
     * Font dùng chung của HUD — cho {@code FloatingCombatText} MƯỢN để vẽ số damage, tránh nạp một
     * bản font thứ hai. HUD vẫn giữ quyền sở hữu: người mượn KHÔNG được dispose.
     */
    public BitmapFont font() {
        return skin.font();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

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
