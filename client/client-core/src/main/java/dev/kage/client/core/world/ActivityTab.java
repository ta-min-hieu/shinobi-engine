package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ActivityChestView;
import dev.kage.protocol.ActivityView;
import dev.kage.protocol.ClaimActivityChestCommand;
import dev.kage.protocol.ClaimActivityChestOutcome;
import dev.kage.protocol.ClaimActivityChestResponse;
import dev.kage.protocol.GetActivityRequest;
import dev.kage.protocol.GetActivityResponse;
import dev.kage.protocol.Opcode;

/**
 * Tab "Năng động" của cửa sổ Hằng ngày (ADR-106 D5) — tách khỏi {@link DailyCoordinator} để lớp đó không vượt trần độ
 * phức tạp PMD. Mỗi việc một dòng (đã làm / trần, điểm), mỗi rương một dòng có nút nhận khi đủ điểm. Server tính điểm
 * và trả quà; client chỉ hiển thị.
 */
final class ActivityTab {

    /** Dòng hiển thị + việc của nút cuối dòng (cùng chỉ số). */
    record Rendered(List<ReportPanel.Row> rows, List<Runnable> actions, String status) {}

    private final WorldCommandSender sender;
    private final I18NBundle i18n = KageI18n.load();

    ActivityTab(WorldCommandSender sender) {
        this.sender = sender;
    }

    void request() {
        sender.sendReadRequest(Opcode.OPCODE_GET_ACTIVITY_REQUEST_VALUE, GetActivityRequest.getDefaultInstance());
    }

    /**
     * Gói thuộc tab này: trả lời trạng thái hoặc kết quả nhận rương (một dòng chat + trạng thái mới).
     *
     * @param show vẽ trạng thái lên cửa sổ
     * @param notice một dòng hệ thống trong chat
     * @return {@code true} nếu gói thuộc tab này
     */
    boolean dispatch(int opcode, byte[] payload, Consumer<Rendered> show, Consumer<String> notice)
            throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_GET_ACTIVITY_RESPONSE_VALUE) {
            show.accept(render(GetActivityResponse.parseFrom(payload)));
            return true;
        }
        if (opcode == Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_RESPONSE_VALUE) {
            ClaimActivityChestResponse response = ClaimActivityChestResponse.parseFrom(payload);
            notice.accept(text(
                    response.getOutcome() == ClaimActivityChestOutcome.CLAIM_ACTIVITY_CHEST_OUTCOME_OK
                            ? "activity.claim.ok"
                            : "activity.claim.not_available"));
            show.accept(render(response.getStatus()));
            return true;
        }
        return false;
    }

    Rendered render(GetActivityResponse status) {
        List<ReportPanel.Row> rows = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (ActivityView activity : status.getActivitiesList()) {
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(
                            i18n,
                            "activity.row",
                            text(activity.getNameKey()),
                            Math.min(activity.getCount(), activity.getMaxCount()),
                            activity.getMaxCount(),
                            activity.getPoints()),
                    false,
                    ""));
            actions.add(() -> {});
        }
        for (ActivityChestView chest : status.getChestsList()) {
            int milestone = chest.getMilestone();
            boolean claimable = chest.getReached() && !chest.getClaimed();
            String state = text(
                    chest.getClaimed()
                            ? "daily.state.claimed"
                            : chest.getReached() ? "daily.state.ready" : "daily.state.locked");
            rows.add(new ReportPanel.Row(
                    KageI18n.formatOrKey(i18n, "activity.chest", milestone, chest.getGold(), state),
                    claimable,
                    claimable ? "daily.action.claim" : ""));
            actions.add(() -> claim(milestone));
        }
        return new Rendered(rows, actions, KageI18n.formatOrKey(i18n, "activity.hint", status.getPoints()));
    }

    private void claim(int milestone) {
        sender.sendReadRequest(
                Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_COMMAND_VALUE,
                ClaimActivityChestCommand.newBuilder().setMilestone(milestone).build());
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
