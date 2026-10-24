package dev.kage.character.api;

import java.util.Optional;

import dev.kage.character.application.RebirthUseCase;
import dev.kage.character.domain.RebirthPort;
import dev.kage.character.domain.Transaction;

/** API Độ Kiếp Chuyển Thế (ADR-089). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class RebirthFacade {

    /** Composition không có chuyển thế (test) — không nhân vật nào. */
    public static final RebirthFacade DISABLED = new RebirthFacade(new RebirthUseCase(
            new RebirthPort() {
                @Override
                public Optional<Progress> lock(Transaction tx, long characterId) {
                    return Optional.empty();
                }

                @Override
                public void rebirth(Transaction tx, long characterId) {
                    // không có nhân vật nào để chuyển thế
                }

                @Override
                public int rebirthCountOf(long characterId) {
                    return 0;
                }
            },
            action -> action.accept(null)));

    private final RebirthUseCase useCase;

    /** Dựng ở composition root. */
    public RebirthFacade(RebirthUseCase useCase) {
        this.useCase = useCase;
    }

    /** Chuyển thế — {@code characterId} từ session. */
    public RebirthUseCase.Result rebirth(long characterId) {
        return useCase.rebirth(characterId);
    }

    /** Số lần đã chuyển thế. */
    public int rebirthCountOf(long characterId) {
        return useCase.rebirthCountOf(characterId);
    }
}
