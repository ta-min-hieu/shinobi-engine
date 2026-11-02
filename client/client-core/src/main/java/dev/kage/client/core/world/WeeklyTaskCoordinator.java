package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.client.core.ui.theme.KageSkin;
import dev.kage.protocol.ClaimWeeklyTaskCommand;
import dev.kage.protocol.ClaimWeeklyTaskOutcome;
import dev.kage.protocol.ClaimWeeklyTaskResponse;
import dev.kage.protocol.GetWeeklyTasksRequest;
import dev.kage.protocol.GetWeeklyTasksResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.WeeklyTaskView;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhiệm vụ tuần cá nhân (ADR-154) — cửa sổ mở bằng lệnh chat {@code /nhiemvutuan} (hoặc {@code /weekly}): mỗi việc một
 * dòng (tiến độ/chỉ tiêu, thưởng); đủ chỉ tiêu mà chưa nhận thì có nút "Nhận". Server đếm và quyết, client chỉ đọc lại.
 */
final class WeeklyTaskCoordinator implements WindowFlow {

    private static final List<String> COMMANDS = List.of("/nhiemvutuan", "/weekly");
    private static final String OUTCOME_PREFIX = "CLAIM_WEEKLY_TASK_OUTCOME_";
    /** Bảng thật có 5 việc — gói dị dạng không được kéo theo hàng nghìn dòng. */
    private static final int MAX_ROWS = 20;

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<String> rowCodes = new ArrayList<>();

    WeeklyTaskCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("weekly.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    static WeeklyTaskCoordinator create(KageSkin skin, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        return new WeeklyTaskCoordinator(
                new ReportPanel(skin, "weekly.title", Input.Keys.UNKNOWN, List.of()), sender, feeds);
    }

    ReportPanel panel() {
        return panel;
    }

    private boolean onSlash(String line) {
        if (!COMMANDS.contains(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void request() {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_WEEKLY_TASKS_REQUEST_VALUE, GetWeeklyTasksRequest.getDefaultInstance());
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowCodes.size()) {
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CLAIM_WEEKLY_TASK_COMMAND_VALUE,
                ClaimWeeklyTaskCommand.newBuilder()
                        .setTaskCode(rowCodes.get(rowIndex))
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
        if (opcode == Opcode.OPCODE_GET_WEEKLY_TASKS_RESPONSE_VALUE) {
            show(GetWeeklyTasksResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode != Opcode.OPCODE_CLAIM_WEEKLY_TASK_RESPONSE_VALUE) {
            return false;
        }
        ClaimWeeklyTaskResponse response = ClaimWeeklyTaskResponse.parseFrom(frame.payload());
        show(response.getTasks());
        ClaimWeeklyTaskOutcome outcome = response.getOutcome();
        if (outcome == ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_OK) {
            String line = text("weekly.claimed");
            panel.setStatus(line);
            chat.showSystemNotice(line);
            sender.listInventory(); // thưởng vừa vào túi (linh thạch tới riêng qua GOLD_CHANGED_EVENT)
        } else {
            String suffix = outcome == ClaimWeeklyTaskOutcome.UNRECOGNIZED
                    ? "unspecified"
                    : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
            panel.setStatus(text("weekly.outcome." + suffix));
        }
        return true;
    }

    private void show(GetWeeklyTasksResponse tasks) {
        rowCodes.clear();
        List<ReportPanel.Row> rows = new ArrayList<>();
        for (WeeklyTaskView t : tasks.getTasksList()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            boolean done = t.getProgress() >= t.getTarget();
            String state = t.getClaimed() ? text("weekly.state.claimed") : done ? text("weekly.state.ready") : "";
            String line = KageI18n.formatOrKey(
                    i18n, "weekly.row", text(t.getNameKey()), t.getProgress(), t.getTarget(), reward(t), state);
            boolean claimable = done && !t.getClaimed();
            rows.add(new ReportPanel.Row(line, claimable, claimable ? "weekly.action.claim" : ""));
            rowCodes.add(t.getCode());
        }
        panel.setRows(rows);
    }

    private String reward(WeeklyTaskView t) {
        StringBuilder reward = new StringBuilder(KageI18n.formatOrKey(i18n, "weekly.reward.gold", t.getGold()));
        if (t.getItemTemplateId() != 0) {
            String name = itemCatalog
                    .nameKey(t.getItemTemplateId())
                    .map(this::text)
                    .orElseGet(() -> "#" + t.getItemTemplateId());
            reward.append(" + ").append(name).append(" x").append(t.getQuantity());
        }
        return reward.toString();
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        rowCodes.clear();
        panel.setRows(List.of());
    }
}
