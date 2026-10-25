package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnterTowerCommand;
import dev.kage.protocol.EnterTowerResponse;
import dev.kage.protocol.GetTowerStatusRequest;
import dev.kage.protocol.GetTowerStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TowerClimberView;
import dev.kage.protocol.TowerFloorClearedEvent;

/**
 * Tab "Trấn Yêu Tháp" của cửa sổ Hằng ngày (ADR-113 D4) — tầng cao nhất, tầng kế (loài + số con), nút Vào tháp, bảng
 * 10 người leo cao nhất. Server dựng tầng, đếm quái và ghi tầng; client chỉ hiển thị. Cùng khuôn {@link ReputationTab}.
 */
final class TowerTab {

    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    TowerTab(WorldCommandSender sender) {
        this.sender = sender;
    }

    void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_TOWER_STATUS_REQUEST_VALUE, GetTowerStatusRequest.getDefaultInstance());
    }

    /** Gói thuộc tab này; {@code true} nếu đã xử lý. Sự kiện qua tầng đi thẳng ra dòng hệ thống. */
    boolean dispatch(int opcode, byte[] payload, Consumer<ActivityTab.Rendered> show, Consumer<String> notice)
            throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_GET_TOWER_STATUS_RESPONSE_VALUE) {
            show.accept(render(GetTowerStatusResponse.parseFrom(payload)));
            return true;
        }
        if (opcode == Opcode.OPCODE_ENTER_TOWER_RESPONSE_VALUE) {
            EnterTowerResponse response = EnterTowerResponse.parseFrom(payload);
            String name = response.getOutcome().name();
            notice.accept(KageI18n.formatOrKey(
                    i18n,
                    "tower.enter." + name.replace("ENTER_TOWER_OUTCOME_", "").toLowerCase(Locale.ROOT),
                    response.getFloor()));
            return true;
        }
        if (opcode == Opcode.OPCODE_TOWER_FLOOR_CLEARED_EVENT_VALUE) {
            TowerFloorClearedEvent event = TowerFloorClearedEvent.parseFrom(payload);
            notice.accept(KageI18n.formatOrKey(
                    i18n, event.getRewarded() ? "tower.cleared.rewarded" : "tower.cleared", event.getFloor()));
            return true;
        }
        return false;
    }

    private ActivityTab.Rendered render(GetTowerStatusResponse status) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (status.getNextFloor() > 0) {
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "tower.next",
                            status.getNextFloor(),
                            KageI18n.textOrKey(i18n, "hud.monster." + status.getNextMonsterCode()),
                            status.getNextMonsterCount()),
                    true,
                    "tower.action.enter"));
            actions.add(() -> sender.sendReadRequest(
                    Opcode.OPCODE_ENTER_TOWER_COMMAND_VALUE, EnterTowerCommand.getDefaultInstance()));
        } else {
            rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "tower.completed"), true));
            actions.add(() -> {});
        }
        rows.add(new ReportPanel.Row(KageI18n.textOrKey(i18n, "tower.top.title"), false));
        actions.add(() -> {});
        int rank = 1;
        for (TowerClimberView climber : status.getTopList()) {
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n, "tower.top.row", rank, climber.getCharacterName(), climber.getBestFloor()),
                    false));
            actions.add(() -> {});
            rank++;
        }
        String hint = KageI18n.formatOrKey(i18n, "tower.hint", status.getBestFloor(), status.getTopFloor());
        return new ActivityTab.Rendered(rows, actions, hint);
    }
}
