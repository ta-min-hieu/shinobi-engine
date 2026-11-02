package dev.kage.server.gateway;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.checkin.api.ActivityFacade;
import dev.kage.checkin.api.WeeklyTaskFacade;

/**
 * Cộng điểm năng động (ADR-106 D2) — các đường việc hằng ngày (điểm danh, mốc online, truy nã, Bí Cảnh, nhiệm vụ ngày,
 * hái linh thảo) gọi {@link #record} SAU khi việc chính đã thành công. Ghi là việc phụ trên Virtual Thread riêng:
 * người gọi có thể đang ở luồng hẹn giờ/luồng thưởng, và lỗi ghi không được làm hỏng việc chính.
 */
final class ActivityHooks {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityHooks.class);

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn BountyHooks.
    private volatile ActivityFacade facade = ActivityFacade.DISABLED;
    // ADR-154 — nhiệm vụ tuần đếm qua CÙNG đường: mỗi việc hằng ngày cũng là một lần cho việc tuần cùng mã.
    private volatile WeeklyTaskFacade weekly = WeeklyTaskFacade.DISABLED;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(ActivityFacade realFacade) {
        this.facade = realFacade;
    }

    /** Nối nhiệm vụ tuần (ADR-154) — một lần, lúc khởi động. */
    void bindWeekly(WeeklyTaskFacade realWeekly) {
        this.weekly = realWeekly;
    }

    /** Một lần làm xong việc {@code activity}. Không chặn người gọi. */
    void record(long characterId, ActivityFacade.Activity activity) {
        ActivityFacade target = facade;
        writers.execute(() -> write(target, characterId, activity));
        recordWeekly(characterId, activity.code());
    }

    /** Một lần cho nhiệm vụ tuần (ADR-154) — kể cả việc lịch ngày không đếm, vd hạ quái. Không chặn người gọi. */
    void recordWeekly(long characterId, String activityCode) {
        WeeklyTaskFacade target = weekly;
        writers.execute(() -> writeWeekly(target, characterId, activityCode));
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây, việc chính không suy suyển.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void writeWeekly(WeeklyTaskFacade target, long characterId, String activityCode) {
        try {
            target.record(characterId, activityCode, Instant.now());
        } catch (RuntimeException e) {
            LOG.warn("Ghi nhiệm vụ tuần lỗi: characterId={}, activity={}", characterId, activityCode, e);
        }
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây, việc chính không suy suyển.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void write(ActivityFacade target, long characterId, ActivityFacade.Activity activity) {
        try {
            target.record(characterId, activity);
        } catch (RuntimeException e) {
            LOG.warn("Ghi điểm năng động lỗi: characterId={}, activity={}", characterId, activity, e);
        }
    }

    /** Tắt server có trật tự — gọi TRƯỚC khi đóng pool DB. */
    void stop() {
        writers.shutdown();
    }
}
