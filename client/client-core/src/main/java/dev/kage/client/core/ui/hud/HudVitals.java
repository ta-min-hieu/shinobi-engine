package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;

import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.widget.HealthBar;

/** Thanh Máu + thanh Khí của người chơi (góc trên-trái) — một cụm của {@link CombatHud}. */
final class HudVitals {

    private final HealthBar hpBar;
    private final Label hpLabel;
    private final HealthBar chiBar;
    private final Label chiLabel;

    private int shownHp = -1;
    private int shownMaxHp = -1;
    private int shownChi = -1;
    private int shownMaxChi = -1;

    HudVitals(KageSkin skin) {
        this.hpBar = new HealthBar(skin);
        this.hpLabel = new Label("", skin.statLabelStyle);
        this.chiBar = new HealthBar(skin, HealthBar.Kind.CHI);
        this.chiLabel = new Label("", skin.statLabelStyle);
    }

    Stack hpOverlay() {
        return HudLayout.overlay(hpBar, hpLabel);
    }

    Stack chiOverlay() {
        return HudLayout.overlay(chiBar, chiLabel);
    }

    void setHp(int current, int max) {
        if (current == shownHp && max == shownMaxHp) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        shownHp = current;
        shownMaxHp = max;
        hpBar.setValues(current, max);
        hpLabel.setText(current + " / " + max);
    }

    void setChi(int current, int max) {
        if (current == shownChi && max == shownMaxChi) {
            return; // UI.md §9
        }
        shownChi = current;
        shownMaxChi = max;
        chiBar.setValues(current, max);
        chiLabel.setText(current + " / " + max);
    }
}
