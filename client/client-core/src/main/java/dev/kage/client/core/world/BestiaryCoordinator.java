package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BestiaryEntryView;
import dev.kage.protocol.GetBestiaryRequest;
import dev.kage.protocol.GetBestiaryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Sổ sưu tầm yêu thú (ADR-108 D5) — {@link ReportPanel} chỉ đọc, phím Y: mỗi loài một dòng (đã hạ / cần, thưởng), trang
 * đã mở kèm lời sưu tầm. Server đếm và cộng chỉ số; client chỉ hiển thị.
 */
final class BestiaryCoordinator implements WindowFlow {

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    BestiaryCoordinator(ReportPanel panel, WorldCommandSender sender) {
        this.panel = panel;
        this.sender = sender;
        panel.setOnRefresh(this::request);
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_GET_BESTIARY_REQUEST_VALUE, GetBestiaryRequest.getDefaultInstance());
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
        if (frame.header().opcode() != Opcode.OPCODE_GET_BESTIARY_RESPONSE_VALUE) {
            return false;
        }
        show(GetBestiaryResponse.parseFrom(frame.payload()));
        return true;
    }

    private void show(GetBestiaryResponse response) {
        int unlocked = 0;
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (BestiaryEntryView entry : response.getEntriesList()) {
            String name = KageI18n.textOrKey(i18n, "hud.monster." + entry.getMonsterCode());
            String bonus = KageI18n.formatOrKey(
                    i18n, "bestiary.bonus." + entry.getBonusStat().toLowerCase(Locale.ROOT), entry.getBonusValue());
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            entry.getUnlocked() ? "bestiary.row.unlocked" : "bestiary.row.locked",
                            name,
                            Math.min(entry.getKills(), entry.getKillsRequired()),
                            entry.getKillsRequired(),
                            bonus),
                    entry.getUnlocked(),
                    ""));
            if (entry.getUnlocked()) {
                unlocked++;
                rows.add(new ReportPanel.Row("  " + KageI18n.textOrKey(i18n, entry.getLoreKey()), false, ""));
            }
        }
        panel.setStatus(KageI18n.formatOrKey(i18n, "bestiary.hint", unlocked, response.getEntriesCount()));
        panel.setRows(rows);
    }

    @Override
    public void reset() {
        panel.close();
        panel.setRows(List.of());
    }
}
