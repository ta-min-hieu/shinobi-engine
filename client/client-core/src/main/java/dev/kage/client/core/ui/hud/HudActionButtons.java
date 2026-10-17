package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * GÓC DƯỚI-PHẢI: hai nút tròn — <b>Chiêu</b> bên trái, <b>Đánh</b> bên phải — mỗi nút một biển tên phía
 * trên. Một cụm của {@link CombatHud}.
 *
 * <p>Nút Đánh thêm 2026-08-09 khi chủ dự án đổi đánh thường sang bấm tay. Nó nằm NGOÀI CÙNG bên phải và
 * to hơn nút chiêu: đó là nút bấm nhiều nhất trong trận, đúng chỗ ngón cái chạm tới trên cảm ứng (UI.md —
 * nút ≥48dp) và đúng vị trí nút đánh của bản gốc Ninja School.
 */
final class HudActionButtons {

    private static final float ACTION_BUTTON_SIZE = 96f;

    /** Nút chiêu nhỏ hơn nút Đánh — thứ bậc thị giác: đánh thường là hành động chính (2026-08-09). */
    private static final float SKILL_BUTTON_SIZE = 76f;

    private final I18NBundle i18n;
    private final TextButton skillButton;
    private final Label skillNameLabel;
    private final TextButton attackButton;
    private final CooldownText cooldownText = new CooldownText();

    private Runnable onSkillClicked;
    private Runnable onAttackClicked;
    private String shownSkillText;

    HudActionButtons(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.skillButton = new TextButton("", skin.roundButtonStyle);
        this.attackButton = new TextButton("", skin.roundButtonStyle);
        this.skillNameLabel = new Label("", skin.statLabelStyle);
    }

    Table build(KageSkin skin) {
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

        // Mỗi nút một CỘT riêng (biển tên + nút), canh đáy: hai nút khác cỡ nằm chung một lưới 2 hàng sẽ
        // kéo hàng biển tên giãn theo nút cao nhất, để lại khoảng trống lơ lửng giữa tên và nút — thấy
        // thật trên ảnh chụp preview 2026-08-09.
        Table cluster = new Table();
        cluster.add(buttonColumn(HudLayout.namePlate(skin, skillNameLabel), skillButton, SKILL_BUTTON_SIZE))
                .bottom()
                .padRight(10f);
        Label attackName = new Label(i18n.get("hud.action.attack"), skin.statLabelStyle);
        cluster.add(buttonColumn(HudLayout.namePlate(skin, attackName), attackButton, ACTION_BUTTON_SIZE))
                .bottom();
        return cluster;
    }

    private static Table buttonColumn(Table plate, TextButton button, float buttonSize) {
        Table column = new Table();
        column.add(plate).padBottom(6f).row();
        column.add(button).size(buttonSize);
        return column;
    }

    void setOnSkillClicked(Runnable callback) {
        this.onSkillClicked = callback;
    }

    void setOnAttackClicked(Runnable callback) {
        this.onAttackClicked = callback;
    }

    /** Xem {@link CombatHud#setSkillState}. */
    void setSkillState(String i18nKey, float cooldownRemainingSeconds) {
        skillNameLabel.setText(i18n.get(i18nKey));
        // Nút TRÒN nhỏ không đủ chỗ cho tên + số: tên nằm nhãn phía trên, trong nút chỉ còn đếm ngược
        // (rỗng khi sẵn sàng — nút sáng lên là đủ tín hiệu "dùng được").
        String text = cooldownText.textFor(cooldownRemainingSeconds);
        if (text.equals(shownSkillText)) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        shownSkillText = text;
        skillButton.setText(text);
    }

    void setSkillReady(boolean ready) {
        skillButton.setDisabled(!ready); // Button tự chặn click khi disabled, không cần gate thêm.
    }
}
