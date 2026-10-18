package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.achievement.application.AchievementUseCase;
import dev.kage.protocol.AchievementView;
import dev.kage.protocol.ListAchievementsResponse;
import dev.kage.protocol.SetActiveTitleOutcome;
import dev.kage.protocol.SetActiveTitleResponse;

/** Dịch kết quả feature-achievement sang gói (ADR-062 D5) — tách khỏi {@code GatewayChannelHandler}. */
final class AchievementViews {

    private AchievementViews() {}

    static ListAchievementsResponse sheet(AchievementUseCase.Sheet sheet) {
        ListAchievementsResponse.Builder response = ListAchievementsResponse.newBuilder()
                .setActiveTitleCode(sheet.activeTitleCode().orElse(""));
        for (AchievementUseCase.AchievementView row : sheet.rows()) {
            response.addAchievements(AchievementView.newBuilder()
                    .setCode(row.achievement().code())
                    .setNameKey(row.achievement().nameKey())
                    .setTitleKey(row.achievement().titleKey())
                    .setProgress(row.progress())
                    .setTarget(row.achievement().target())
                    .setUnlocked(row.unlocked())
                    .build());
        }
        return response.build();
    }

    static SetActiveTitleResponse titleResponse(boolean worn, Optional<String> activeCode) {
        return SetActiveTitleResponse.newBuilder()
                .setOutcome(
                        worn
                                ? SetActiveTitleOutcome.SET_ACTIVE_TITLE_OUTCOME_OK
                                : SetActiveTitleOutcome.SET_ACTIVE_TITLE_OUTCOME_NOT_UNLOCKED)
                .setActiveTitleCode(activeCode.orElse(""))
                .build();
    }
}
