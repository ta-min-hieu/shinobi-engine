package dev.kage.checkin.domain;

import java.util.List;
import java.util.Map;

/** Luật điểm năng động (ADR-106) — thuần. */
public final class ActivityRules {

    private ActivityRules() {}

    /** Điểm của một việc: số lần (kẹp ở trần) × điểm mỗi lần. */
    public static int pointsOf(ActivityPort.Definition definition, int count) {
        return Math.min(Math.max(0, count), definition.maxCount()) * definition.pointsEach();
    }

    /** Tổng điểm trong ngày. */
    public static int totalPoints(List<ActivityPort.Definition> definitions, Map<String, Integer> progress) {
        int total = 0;
        for (ActivityPort.Definition definition : definitions) {
            total += pointsOf(definition, progress.getOrDefault(definition.code(), 0));
        }
        return total;
    }
}
