package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.checkin.api.WeeklyTaskFacade;
import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.protocol.ClaimWeeklyTaskCommand;
import dev.kage.protocol.ClaimWeeklyTaskOutcome;
import dev.kage.protocol.ClaimWeeklyTaskResponse;
import dev.kage.protocol.GetWeeklyTasksResponse;
import dev.kage.protocol.WeeklyTaskView;

/** Nhiệm vụ tuần cá nhân (ADR-154) — xem tiến độ, nhận thưởng; server kiểm chỉ tiêu và chống nhận hai lần. */
final class WeeklyTaskCommands {

    private final WeeklyTaskFacade facade;
    private final Clock clock;

    WeeklyTaskCommands(WeeklyTaskFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetWeeklyTasksResponse list(long characterId) {
        GetWeeklyTasksResponse.Builder response = GetWeeklyTasksResponse.newBuilder();
        for (WeeklyTaskPort.Task t : facade.tasks(characterId, clock.instant())) {
            response.addTasks(WeeklyTaskView.newBuilder()
                    .setCode(t.code())
                    .setNameKey(t.nameKey())
                    .setTarget(t.target())
                    .setProgress(Math.min(t.progress(), t.target()))
                    .setClaimed(t.claimed())
                    .setGold(t.gold())
                    .setItemTemplateId(t.itemTemplateId())
                    .setQuantity(t.quantity()));
        }
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    ClaimWeeklyTaskResponse claim(long characterId, ClaimWeeklyTaskCommand command) {
        WeeklyTaskPort.Outcome outcome = facade.claim(characterId, command.getTaskCode(), clock.instant());
        return ClaimWeeklyTaskResponse.newBuilder()
                .setOutcome(wire(outcome))
                .setTaskCode(command.getTaskCode())
                .setTasks(list(characterId))
                .build();
    }

    private static ClaimWeeklyTaskOutcome wire(WeeklyTaskPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_OK;
            case UNKNOWN_TASK -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_UNKNOWN_TASK;
            case NOT_DONE -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_NOT_DONE;
            case ALREADY_CLAIMED -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_ALREADY_CLAIMED;
            case BAG_FULL -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_BAG_FULL;
            case GOLD_CAP -> ClaimWeeklyTaskOutcome.CLAIM_WEEKLY_TASK_OUTCOME_GOLD_CAP;
        };
    }
}
