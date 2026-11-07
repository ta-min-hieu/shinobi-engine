package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AchievementUnlockedEvent;
import dev.kage.protocol.AchievementView;
import dev.kage.protocol.ListAchievementsRequest;
import dev.kage.protocol.ListAchievementsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetActiveTitleCommand;
import dev.kage.protocol.SetActiveTitleOutcome;
import dev.kage.protocol.SetActiveTitleResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa sổ Thành tựu (phím U, ADR-062 D6): mỗi dòng tên + tiến độ, dòng đã mở có nút "Đeo"; dòng đầu là
 * danh hiệu đang đeo kèm nút "Bỏ". Mở khoá ⇒ một dòng hệ thống trong chat.
 *
 * <p>Danh hiệu là khoá i18n do server gửi; client chỉ tra chữ. Đeo được hay không là phán quyết server.
 */
final class AchievementCoordinator implements WindowFlow {

    /** Hành động của từng dòng panel, cùng chỉ số với {@link ReportPanel#setRows}; rỗng = dòng không nút. */
    private final List<String> rowCodes = new ArrayList<>();

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ChatCoordinator notices;
    private final I18NBundle i18n = KageI18n.load();

    /** Mã đặc biệt của dòng "Bỏ danh hiệu" — mã rỗng gửi lên nghĩa là bỏ (achievement.proto). */
    private static final String TAKE_OFF = "";

    /** Dòng không có nút. */
    private static final String NO_ACTION = "\u0000";

    AchievementCoordinator(ReportPanel panel, WorldCommandSender sender, ChatCoordinator notices) {
        this.panel = panel;
        this.sender = sender;
        this.notices = notices;
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("achievement.hint.open"));
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_LIST_ACHIEVEMENTS_REQUEST_VALUE, ListAchievementsRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size() || NO_ACTION.equals(rowCodes.get(rowIndex))) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SET_ACTIVE_TITLE_COMMAND_VALUE,
                SetActiveTitleCommand.newBuilder()
                        .setAchievementCode(rowCodes.get(rowIndex))
                        .build());
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
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_ACHIEVEMENTS_RESPONSE_VALUE) {
            show(ListAchievementsResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_ACTIVE_TITLE_RESPONSE_VALUE) {
            SetActiveTitleResponse response = SetActiveTitleResponse.parseFrom(frame.payload());
            panel.setStatus(text(
                    response.getOutcome() == SetActiveTitleOutcome.SET_ACTIVE_TITLE_OUTCOME_OK
                            ? "achievement.status.title_changed"
                            : "achievement.error.not_unlocked"));
            request();
            return true;
        }
        if (opcode == Opcode.OPCODE_ACHIEVEMENT_UNLOCKED_EVENT_VALUE) {
            AchievementUnlockedEvent event = AchievementUnlockedEvent.parseFrom(frame.payload());
            notices.showSystemNotice(
                    format("achievement.notice.unlocked", text(event.getNameKey()), text(event.getTitleKey())));
            if (panel.isOpen()) {
                request();
            }
            return true;
        }
        return false;
    }

    /** Dựng dòng — chỉ khi server vừa trả lời. */
    private void show(ListAchievementsResponse response) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        rowCodes.clear();
        String activeCode = response.getActiveTitleCode();
        String activeTitle = text("achievement.title.none");
        for (AchievementView view : response.getAchievementsList()) {
            if (view.getCode().equals(activeCode)) {
                activeTitle = text(view.getTitleKey());
            }
        }
        rows.add(new ReportPanel.Row(
                format("achievement.row.active", activeTitle),
                true,
                activeCode.isEmpty() ? "" : "achievement.action.take_off"));
        rowCodes.add(activeCode.isEmpty() ? NO_ACTION : TAKE_OFF);
        for (AchievementView view : response.getAchievementsList()) {
            boolean wearable = view.getUnlocked() && !view.getCode().equals(activeCode);
            rows.add(new ReportPanel.Row(rowText(view), view.getUnlocked(), wearable ? "achievement.action.wear" : ""));
            rowCodes.add(wearable ? view.getCode() : NO_ACTION);
        }
        panel.setRows(rows);
    }

    /**
     * {@code [Đã mở] Tên (12/100) — Danh hiệu: X (Công +2)}; dấu bằng CHỮ để không dựa vào màu (client/CLAUDE.md). Chỉ
     * số cộng khi đeo (ADR-210) nối sau, chỉ những chỉ số khác 0.
     */
    private String rowText(AchievementView view) {
        long shown = Math.min(view.getProgress(), view.getTarget());
        return format(
                        view.getUnlocked() ? "achievement.row.unlocked" : "achievement.row.locked",
                        text(view.getNameKey()),
                        shown,
                        view.getTarget(),
                        text(view.getTitleKey()))
                + bonusSuffix(i18n, view);
    }

    /** ADR-210 — {@code " (Công +2, Máu +30)"}, hoặc chuỗi rỗng nếu danh hiệu không cộng gì. Tĩnh để test. */
    static String bonusSuffix(I18NBundle i18n, AchievementView view) {
        List<String> parts = new ArrayList<>();
        addStat(parts, i18n, "inventory.stat.atk", view.getBonusAtk());
        addStat(parts, i18n, "inventory.stat.def", view.getBonusDef());
        addStat(parts, i18n, "inventory.stat.res", view.getBonusRes());
        addStat(parts, i18n, "inventory.stat.hp", view.getBonusHp());
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    private static void addStat(List<String> parts, I18NBundle i18n, String key, int value) {
        if (value != 0) {
            parts.add(KageI18n.formatOrKey(i18n, key, value));
        }
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
        rowCodes.clear();
        panel.setRows(List.of());
        panel.setStatus(text("achievement.hint.open"));
    }
}
