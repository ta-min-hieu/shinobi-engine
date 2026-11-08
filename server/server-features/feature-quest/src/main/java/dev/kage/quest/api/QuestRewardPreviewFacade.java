package dev.kage.quest.api;

import java.util.Optional;

import dev.kage.quest.application.PreviewQuestRewardUseCase;

/** API xem trước thưởng nhiệm vụ (ADR-242). Chạy script — chỉ gọi trên Virtual Thread. */
public final class QuestRewardPreviewFacade {

    /** Không có nhiệm vụ nào (composition test). */
    public static final QuestRewardPreviewFacade DISABLED = new QuestRewardPreviewFacade(Optional::empty);

    /** Nguồn xem trước — tách ra để {@link #DISABLED} khỏi cần ports thật. */
    @FunctionalInterface
    private interface Source {
        Optional<PreviewQuestRewardUseCase> useCase();
    }

    private final Source source;

    /** Dựng ở composition root. */
    public QuestRewardPreviewFacade(PreviewQuestRewardUseCase useCase) {
        this(() -> Optional.of(useCase));
    }

    private QuestRewardPreviewFacade(Source source) {
        this.source = source;
    }

    /** Thưởng dự kiến của {@code questCode} với tiến độ hiện tại; rỗng nếu không có nhiệm vụ đó. */
    public Optional<PreviewQuestRewardUseCase.Preview> preview(long characterId, String questCode) {
        return source.useCase().flatMap(useCase -> useCase.preview(characterId, questCode));
    }
}
