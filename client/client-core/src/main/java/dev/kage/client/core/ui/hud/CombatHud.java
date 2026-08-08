package dev.kage.client.core.ui.hud;

import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

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

    private static final float BAR_WIDTH = 200f;
    private static final float BAR_HEIGHT = 24f;

    private final Stage stage;
    private final KageSkin skin;
    private final I18NBundle i18n;

    private final HealthBar playerHealthBar;
    private final Label playerHpLabel;
    private final Table targetGroup;
    private final Label targetNameLabel;
    private final HealthBar targetHealthBar;
    private final Label targetHpLabel;
    private final TextButton skillButton;

    private Runnable onSkillClicked;

    private int lastPlayerHp = -1;
    private int lastPlayerMaxHp = -1;
    private String lastTargetCode;
    private int lastTargetHp = -1;
    private int lastTargetMaxHp = -1;

    public CombatHud() {
        I18NBundle.setExceptionOnMissingKey(false); // tên quái lạ hiện "??key??" thay vì sập HUD.
        this.stage = new Stage(new ScreenViewport(), new SpriteBatch());
        this.skin = new KageSkin();
        this.i18n = I18NBundle.createBundle(Gdx.files.internal("i18n/strings"), Locale.of("vi"), "UTF-8");

        this.playerHealthBar = new HealthBar(skin);
        this.playerHpLabel = new Label("", skin.labelStyle);
        Table playerGroup = new Table();
        playerGroup.add(playerHealthBar).size(BAR_WIDTH, BAR_HEIGHT).row();
        playerGroup.add(playerHpLabel).padTop(2f);

        this.targetNameLabel = new Label("", skin.labelStyle);
        this.targetHealthBar = new HealthBar(skin);
        this.targetHpLabel = new Label("", skin.labelStyle);
        this.targetGroup = new Table();
        targetGroup.add(targetNameLabel).row();
        targetGroup.add(targetHealthBar).size(BAR_WIDTH, BAR_HEIGHT).row();
        targetGroup.add(targetHpLabel).padTop(2f);
        targetGroup.setVisible(false);

        this.skillButton = new TextButton(i18n.get("hud.skill.fireball"), skin.buttonStyle);
        skillButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onSkillClicked != null) {
                    onSkillClicked.run();
                }
            }
        });

        // 3 Table độc lập, mỗi cái setFillParent(true) — HUD combat tối thiểu này không có bố cục
        // chung dạng lưới (thanh máu mình góc trên-trái, mục tiêu giữa-trên, nút skill giữa-dưới).
        Table playerContainer = new Table();
        playerContainer.setFillParent(true);
        playerContainer.top().left().add(playerGroup).pad(16f);

        Table targetContainer = new Table();
        targetContainer.setFillParent(true);
        targetContainer.top().add(targetGroup).padTop(16f);

        Table skillContainer = new Table();
        skillContainer.setFillParent(true);
        skillContainer.bottom().add(skillButton).size(120f, 48f).padBottom(24f);

        stage.addActor(playerContainer);
        stage.addActor(targetContainer);
        stage.addActor(skillContainer);
    }

    public void setOnSkillClicked(Runnable callback) {
        this.onSkillClicked = callback;
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
