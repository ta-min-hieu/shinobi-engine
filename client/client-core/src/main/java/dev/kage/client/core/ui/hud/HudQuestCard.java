package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Cụm nhiệm vụ đang theo dõi + nút lĩnh thưởng (cột góc trên-phải) — một cụm của {@link CombatHud}. Ẩn
 * hẳn khi không theo dõi nhiệm vụ nào.
 *
 * <p>KHÔNG có "đã giết 2/3": server cố ý không gửi tiến độ từng bước (quest.proto — chỉ phát khi quest
 * VỪA chuyển COMPLETED, để không quá tải World tick), nên con số đó đơn giản là không tồn tại ở phía
 * client. Hiện "Đang làm" là tất cả những gì nói được mà không bịa.
 */
final class HudQuestCard {

    private final I18NBundle i18n;
    private final Table group;
    private final Label nameLabel;
    private final Label statusLabel;
    private final TextButton claimButton;
    private boolean showing; // ADR-318
    private boolean suppressed; // ADR-318

    private Runnable onClaimClicked;

    HudQuestCard(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.nameLabel = new Label("", skin.labelStyle);
        this.statusLabel = new Label("", skin.statLabelStyle);
        this.claimButton = new TextButton(i18n.get("quest.action.claim"), skin.buttonStyle);
        this.group = new Table();
        claimButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onClaimClicked != null) {
                    onClaimClicked.run();
                }
            }
        });
        group.setBackground(skin.panel);
        group.add(nameLabel).row();
        group.add(statusLabel).padTop(2f).row();
        group.add(claimButton).padTop(6f).growX();
        group.pad(8f);
        group.setVisible(false);
    }

    Table group() {
        return group;
    }

    void setOnClaimClicked(Runnable listener) {
        this.onClaimClicked = listener;
    }

    /** Xem {@link CombatHud#setQuest}. */
    void show(String questCode, boolean completed) {
        nameLabel.setText(KageI18n.textOrKey(i18n, "quest.name." + questCode));
        statusLabel.setText(i18n.get(completed ? "quest.status.completed" : "quest.status.in_progress"));
        claimButton.setVisible(completed);
        showing = true;
        group.setVisible(!suppressed);
    }

    /** ADR-318 — HUD tối giản: ẩn thẻ dù đang có nhiệm vụ; tắt thì hiện lại nếu đang có. */
    void setSuppressed(boolean hidden) {
        suppressed = hidden;
        group.setVisible(showing && !hidden);
    }

    void hide() {
        showing = false;
        group.setVisible(false);
    }
}
