package dev.kage.quest.api;

import java.util.List;

import dev.kage.quest.domain.QuestRankingPort;

/** API xếp hạng nhiệm vụ (ADR-441) — số dòng {@code character_quest_progress} đã xong. Chạm DB — Virtual Thread. */
public final class QuestRankingFacade {

    /** Số dòng mỗi bảng. */
    public static final int TOP = 10;

    /** Bảng rỗng (composition test). */
    public static final QuestRankingFacade DISABLED = new QuestRankingFacade(limit -> List.of());

    private final QuestRankingPort port;

    /** Dựng ở composition root. */
    public QuestRankingFacade(QuestRankingPort port) {
        this.port = port;
    }

    /** Top {@value #TOP}. */
    public List<QuestRankingPort.Ranked> top() {
        return port.top(TOP);
    }
}
