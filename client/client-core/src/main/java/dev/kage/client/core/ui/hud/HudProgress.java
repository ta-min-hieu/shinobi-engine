package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.widget.HealthBar;

/** Huy hiệu cấp + vạch kinh nghiệm (góc trên-trái) — một cụm của {@link CombatHud}. */
final class HudProgress {

    private final I18NBundle i18n;
    private final Label levelLabel;
    private final HealthBar expBar;
    private final Label expLabel;

    private int shownLevel = -1;
    private long shownExpIntoLevel = -1L;
    private long shownExpForNextLevel = -1L;

    HudProgress(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.levelLabel = new Label("1", skin.statLabelStyle);
        this.expBar = new HealthBar(skin, HealthBar.Kind.EXPERIENCE);
        this.expLabel = new Label("", skin.statLabelStyle);
        expLabel.setFontScale(HudLayout.SMALL_LABEL_SCALE);
    }

    /** Huy hiệu tròn mang số cấp. */
    Table levelBadge(KageSkin skin) {
        levelLabel.setAlignment(Align.center);
        Table badge = new Table();
        badge.setBackground(skin.roundButton);
        badge.add(levelLabel);
        return badge;
    }

    Stack expOverlay() {
        return HudLayout.overlay(expBar, expLabel);
    }

    void setLevel(int level) {
        if (level == shownLevel) {
            return; // UI.md §9
        }
        shownLevel = level;
        levelLabel.setText(String.valueOf(level));
    }

    /**
     * Vạch kinh nghiệm (2026-08-09) — số hiển thị là tiến độ TRONG cấp hiện tại, không phải tổng
     * EXP tích luỹ: "còn bao xa tới cấp sau" mới là câu hỏi người chơi đang hỏi.
     *
     * @param expForNextLevel {@code 0} = đã cấp tối đa, vẽ vạch đầy kèm nhãn riêng.
     */
    void setExperience(long expIntoLevel, long expForNextLevel) {
        if (expIntoLevel == shownExpIntoLevel && expForNextLevel == shownExpForNextLevel) {
            return; // UI.md §9
        }
        shownExpIntoLevel = expIntoLevel;
        shownExpForNextLevel = expForNextLevel;
        if (expForNextLevel <= 0L) {
            expBar.setValues(1L, 1L);
            expLabel.setText(i18n.get("hud.exp.max"));
            return;
        }
        expBar.setValues(expIntoLevel, expForNextLevel);
        expLabel.setText(expIntoLevel + " / " + expForNextLevel);
    }
}
