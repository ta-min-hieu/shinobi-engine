package dev.kage.checkin.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.checkin.domain.WeeklyTaskRules;

/** Nhiệm vụ tuần cá nhân (ADR-154). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class WeeklyTaskFacade {

    /** Composition không có nhiệm vụ tuần (test). */
    public static final WeeklyTaskFacade DISABLED = new WeeklyTaskFacade(new WeeklyTaskPort() {
        @Override
        public List<Task> tasks(long characterId, LocalDate weekStart) {
            return List.of();
        }

        @Override
        public void record(long characterId, LocalDate weekStart, String activityCode) {
            // không lưu gì
        }

        @Override
        public Outcome claim(long characterId, LocalDate weekStart, String taskCode, Instant now) {
            return Outcome.UNKNOWN_TASK;
        }
    });

    private final WeeklyTaskPort port;

    /** Dựng ở composition root. */
    public WeeklyTaskFacade(WeeklyTaskPort port) {
        this.port = port;
    }

    /** Các việc tuần này. */
    public List<WeeklyTaskPort.Task> tasks(long characterId, Instant now) {
        return port.tasks(characterId, WeeklyTaskRules.weekStart(now));
    }

    /** Một lần làm xong việc {@code activityCode}. */
    public void record(long characterId, String activityCode, Instant now) {
        port.record(characterId, WeeklyTaskRules.weekStart(now), activityCode);
    }

    /** Nhận thưởng một việc tuần này. */
    public WeeklyTaskPort.Outcome claim(long characterId, String taskCode, Instant now) {
        return port.claim(characterId, WeeklyTaskRules.weekStart(now), taskCode, now);
    }
}
