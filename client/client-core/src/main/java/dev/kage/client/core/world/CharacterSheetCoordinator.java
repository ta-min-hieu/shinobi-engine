package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.CultivationRealm;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetCharacterStatsRequest;
import dev.kage.protocol.GetCharacterStatsResponse;
import dev.kage.protocol.ItemSetProgressView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Bảng nhân vật (ADR-056, phím C) — hỏi {@code GetCharacterStatsRequest} mỗi lần mở/Làm mới, vẽ các con
 * số SERVER đã tính. Client không tính gì (client/CLAUDE.md): tỉ lệ đến bằng phần nghìn và chỉ được
 * định dạng ở đây.
 */
final class CharacterSheetCoordinator implements WindowFlow {

    private static final float PERMILLE_PER_PERCENT = 10f;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    CharacterSheetCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRefresh(this::request);
        panel.setStatus(text("charsheet.hint.open"));
    }

    private void request() {
        panel.setStatus(text("charsheet.status.loading"));
        sender.sendReadRequest(
                Opcode.OPCODE_GET_CHARACTER_STATS_REQUEST_VALUE, GetCharacterStatsRequest.getDefaultInstance());
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_CHARACTER_STATS_RESPONSE_VALUE) {
            return false;
        }
        show(GetCharacterStatsResponse.parseFrom(frame.payload()));
        return true;
    }

    private void show(GetCharacterStatsResponse stats) {
        panel.setStatus(format("charsheet.combat_power", stats.getCombatPower()));
        panel.setRows(rows(stats));
    }

    /** Dựng dòng — gói chỉ tới khi người chơi mở bảng, không phải mỗi khung. */
    List<ReportPanel.Row> rows(GetCharacterStatsResponse stats) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        rows.add(row(format("charsheet.row.realm", realmLabel(stats.getLevel()), stats.getLevel())));
        rows.add(row(format(
                "charsheet.row.primary",
                stats.getStrength(),
                stats.getAgility(),
                stats.getIntelligence(),
                stats.getVitality())));
        rows.add(row(format("charsheet.row.vitals", stats.getMaxHp(), stats.getEquipmentBonusHp(), stats.getMaxChi())));
        rows.add(row(format("charsheet.row.offense", stats.getAttack(), stats.getSkillDamage())));
        rows.add(row(format("charsheet.row.defense", stats.getDefense(), stats.getResistance())));
        rows.add(row(
                format("charsheet.row.accuracy", percent(stats.getHitPermille()), percent(stats.getDodgePermille()))));
        rows.add(row(format("charsheet.row.crit", percent(stats.getCritPermille()), stats.getCritDamagePercent())));
        for (ItemSetProgressView set : stats.getItemSetsList()) {
            rows.add(row(format(
                    "charsheet.row.item_set",
                    text(set.getNameKey()),
                    set.getWornPieces(),
                    set.getTotalPieces(),
                    set.getBonusAtk(),
                    set.getBonusDef(),
                    set.getBonusRes(),
                    set.getBonusHp())));
        }
        return rows;
    }

    private static ReportPanel.Row row(String text) {
        return new ReportPanel.Row(text, false);
    }

    /** {@code 125‰} ⇒ {@code "12.5"} — một chữ số thập phân, đúng độ phân giải server gửi. */
    private static String percent(int permille) {
        return String.valueOf(permille / PERMILLE_PER_PERCENT);
    }

    private String realmLabel(int level) {
        CultivationRealm realm = CultivationRealm.of(level);
        String realmName = text(realm.nameKey());
        return realm.perfected()
                ? format("realm.perfect", realmName)
                : KageI18n.formatOrKey(i18n, "realm.tier_short", realmName, realm.tier());
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object... args) {
        return KageI18n.formatOrKey(i18n, key, args);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
        panel.setStatus(text("charsheet.hint.open"));
    }
}
