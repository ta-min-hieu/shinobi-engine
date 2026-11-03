package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Các dòng trạng thái của {@link CombatHud}: biển đấu tay đôi (rìa trái), dải hầm ngục, biển vùng (ADR-141) và độ trễ
 * (cột góc trên-phải).
 */
final class HudBanners {

    private final I18NBundle i18n;

    /** Biển "đang đấu tay đôi" (rìa TRÁI, giữa chiều cao) — ẩn hẳn khi không ở trong trận nào. */
    private final Table duelGroup;

    private final Label duelLabel;

    /** Cụm "đang ở trong hầm ngục" — xem {@link CombatHud#setDungeonBanner}. Ẩn tới khi có gì để nói. */
    private final Table dungeonGroup;

    private final Label dungeonLabel;
    private final Label dungeonHintLabel;

    /** Biển vùng PvP/an toàn (ADR-141) — đỏ son khi trong dải PvP; cùng cách gỡ khỏi ô như dải hầm ngục. */
    private final Table zoneGroup;

    private final Label zoneLabel;

    /** Ô chứa {@link #zoneGroup} — xem {@link #buildTopRightColumn}. */
    private Cell<Table> zoneCell;

    /** ADR-157 — bảng sát thương boss thế giới trực tiếp; cùng cách gỡ khỏi ô như biển vùng. */
    private final Table bossGroup;

    private final Label bossLabel;
    private Cell<Table> bossCell;

    /** ADR-164 — dòng "x2 EXP"; cùng cách gỡ khỏi ô như bảng boss. */
    private final Table expBuffGroup;

    private final Label expBuffLabel;
    private Cell<Table> expBuffCell;

    /** Độ trễ đường truyền, đo từ {@code PongResponse} — xem {@link #setLatency(long)}. */
    private final Label latencyLabel;

    /** Ô chứa {@link #dungeonGroup} trong cột góc trên-phải — xem {@link #buildTopRightColumn}. */
    private Cell<Table> dungeonCell;

    private String shownDuelText;

    /** -1 = chưa nhận nhịp heartbeat nào, xem {@link #clearLatency()}. */
    private long shownLatencyMs = -1L;

    HudBanners(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.duelLabel = new Label("", skin.labelStyle);
        this.duelGroup = new Table();
        duelGroup.setBackground(skin.panel);
        duelGroup.add(duelLabel).pad(8f);
        duelGroup.setVisible(false);
        this.dungeonLabel = new Label("", skin.labelStyle);
        this.dungeonHintLabel = new Label("", skin.statLabelStyle);
        this.dungeonGroup = new Table();
        dungeonGroup.setBackground(skin.panel);
        dungeonGroup.add(dungeonLabel).row();
        dungeonGroup.add(dungeonHintLabel).padTop(2f).row();
        dungeonGroup.pad(8f);
        this.zoneLabel = new Label("", skin.labelStyle);
        this.zoneGroup = new Table();
        zoneGroup.setBackground(skin.panel);
        zoneGroup.add(zoneLabel).pad(6f);
        this.bossLabel = new Label("", skin.statLabelStyle);
        this.bossGroup = new Table();
        bossGroup.setBackground(skin.panel);
        bossGroup.add(bossLabel).pad(6f);
        this.expBuffLabel = new Label("", skin.statLabelStyle);
        this.expBuffGroup = new Table();
        expBuffGroup.setBackground(skin.panel);
        expBuffGroup.add(expBuffLabel).pad(6f);
        this.latencyLabel = new Label(i18n.get("hud.latency_unknown"), skin.statLabelStyle);
        latencyLabel.setFontScale(HudLayout.SMALL_LABEL_SCALE);
    }

    /** Xem {@link CombatHud#setDuelStatus} — biển đặt ở rìa trái. */
    Table duelGroup() {
        return duelGroup;
    }

    /**
     * Góc TRÊN-PHẢI, một CỘT: độ trễ, dải hầm ngục, rồi {@code questCard} ở dưới.
     *
     * <p>Cột chung chứ không phải hai {@code Table} cùng {@code setFillParent(true)} neo cùng góc —
     * hai cái như thế sẽ vẽ chồng lên nhau, không xếp dọc.
     */
    Table buildTopRightColumn(Table questCard) {
        Table column = new Table();
        // Độ trễ nằm trên cùng cột phải: một dòng chữ nhỏ, LUÔN có chỗ (khác dải hầm ngục bên dưới)
        // — nó có chữ ngay từ khung hình đầu ("— ms"), nên không có ô rỗng nào để đội bố cục về sau.
        column.add(latencyLabel).right().row();
        // GỠ actor ra khỏi ô thay vì setVisible(false): một Table của scene2d vẫn dành chỗ theo kích
        // thước ưa thích của con nó dù con đang ẩn, nên hai cái Label rỗng bên trong sẽ đẩy cụm
        // nhiệm vụ tụt xuống ~2 dòng suốt cả phiên chơi ngoài hầm ngục. Ô rỗng thì cao 0.
        this.dungeonCell = column.add((Table) null).right();
        column.row();
        this.zoneCell = column.add((Table) null).right();
        column.row();
        this.bossCell = column.add((Table) null).right().padTop(4f);
        column.row();
        this.expBuffCell = column.add((Table) null).right().padTop(4f);
        column.row();
        column.add(questCard).right().padTop(8f).row();
        return column;
    }

    /** Xem {@link CombatHud#setLatency}. */
    void setLatency(long roundTripMs) {
        if (roundTripMs == shownLatencyMs) {
            return;
        }
        shownLatencyMs = roundTripMs;
        latencyLabel.setText(i18n.format("hud.latency", roundTripMs));
    }

    void clearLatency() {
        shownLatencyMs = -1L;
        latencyLabel.setText(i18n.get("hud.latency_unknown"));
    }

    void setDuelStatus(String text) {
        if (text.equals(shownDuelText)) {
            return; // UI.md §9 — chỉ setText khi giá trị thực sự đổi.
        }
        shownDuelText = text;
        duelLabel.setText(text);
        duelGroup.setVisible(true);
    }

    void clearDuelStatus() {
        shownDuelText = null;
        duelGroup.setVisible(false);
    }

    void setDungeonBanner(String banner, String exitHint) {
        dungeonLabel.setText(banner);
        dungeonHintLabel.setText(exitHint);
        dungeonCell.setActor(banner.isEmpty() ? null : dungeonGroup);
    }

    void setZoneBanner(String text, Color color) {
        zoneLabel.setText(text);
        zoneLabel.setColor(color);
        zoneCell.setActor(text.isEmpty() ? null : zoneGroup);
    }

    /** ADR-157 — nhiều dòng chữ ĐÃ dịch; rỗng = gỡ bảng. */
    void setBossBoard(String text) {
        bossLabel.setText(text);
        bossCell.setActor(text.isEmpty() ? null : bossGroup);
    }

    /** ADR-164 — một dòng đã dịch; rỗng = gỡ. */
    void setExpBuff(String text) {
        expBuffLabel.setText(text);
        expBuffCell.setActor(text.isEmpty() ? null : expBuffGroup);
    }
}
