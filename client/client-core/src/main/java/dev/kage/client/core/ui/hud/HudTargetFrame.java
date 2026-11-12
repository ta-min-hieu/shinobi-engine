package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.client.core.ui.widget.HealthBar;

/** Tên + thanh máu mục tiêu (giữa-trên) — một cụm của {@link CombatHud}. Ẩn cho tới khi có mục tiêu. */
final class HudTargetFrame {

    private final I18NBundle i18n;
    private final Table group;
    private final Label nameLabel;
    private final HealthBar hpBar;
    private final Label hpLabel;

    private String shownMonsterCode;
    /** Tên nhân vật đang là mục tiêu (đối thủ đấu trường) — loại trừ với {@link #shownMonsterCode}. */
    private String shownCharacterName;

    private int shownHp = -1;
    private int shownMaxHp = -1;
    private final Label distanceLabel; // ADR-299
    private int shownDistance = Integer.MIN_VALUE;

    HudTargetFrame(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.nameLabel = new Label("", skin.labelStyle);
        this.hpBar = new HealthBar(skin);
        this.hpLabel = new Label("", skin.statLabelStyle);
        this.distanceLabel = new Label("", skin.statLabelStyle);
        this.group = new Table();
        group.setBackground(skin.panel);
        group.add(nameLabel).row();
        group.add(HudLayout.overlay(hpBar, hpLabel))
                .size(HudLayout.BAR_WIDTH, HudLayout.BAR_HEIGHT)
                .padTop(4f)
                .row();
        group.add(distanceLabel).padTop(2f);
        group.pad(8f);
        group.setVisible(false);
    }

    Table group() {
        return group;
    }

    void setMonster(String monsterCode, int current, int max) {
        group.setVisible(true);
        if (!monsterCode.equals(shownMonsterCode)) {
            shownMonsterCode = monsterCode;
            shownCharacterName = null;
            nameLabel.setText(i18n.get("hud.monster." + monsterCode));
            // Mục tiêu trước có thể là nhân vật chưa biết máu tối đa (vạch máu đang ẩn) — vẽ lại hết.
            shownHp = -1;
            shownMaxHp = -1;
            hpBar.setVisible(true);
        }
        if (current == shownHp && max == shownMaxHp) {
            return;
        }
        shownHp = current;
        shownMaxHp = max;
        hpBar.setValues(current, max);
        hpLabel.setText(current + " / " + max);
    }

    /** Xem {@link CombatHud#setCharacterTarget}. */
    void setCharacter(String displayName, int current, int max) {
        group.setVisible(true);
        if (!displayName.equals(shownCharacterName)) {
            shownCharacterName = displayName;
            shownMonsterCode = null;
            nameLabel.setText(displayName);
            shownHp = Integer.MIN_VALUE;
            shownMaxHp = Integer.MIN_VALUE;
        }
        if (current == shownHp && max == shownMaxHp) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        shownHp = current;
        shownMaxHp = max;
        boolean maxKnown = max > 0 && current >= 0;
        hpBar.setVisible(maxKnown);
        if (maxKnown) {
            hpBar.setValues(current, max);
            hpLabel.setText(current + " / " + max);
        } else if (current >= 0) {
            hpLabel.setText(KageI18n.formatOrKey(i18n, "hud.target.hp_partial", String.valueOf(current)));
        } else {
            hpLabel.setText(KageI18n.textOrKey(i18n, "hud.target.hp_unknown"));
        }
    }

    /** ADR-299 — khoảng cách (mét); âm = ẩn. setText chỉ khi số đổi (UI.md §9). */
    void setDistance(int meters) {
        if (meters == shownDistance) {
            return;
        }
        shownDistance = meters;
        distanceLabel.setText(meters < 0 ? "" : KageI18n.formatOrKey(i18n, "hud.target.distance", meters));
    }

    void clear() {
        if (shownMonsterCode == null && shownCharacterName == null) {
            return;
        }
        shownMonsterCode = null;
        shownCharacterName = null;
        shownHp = -1;
        shownMaxHp = -1;
        group.setVisible(false);
    }
}
