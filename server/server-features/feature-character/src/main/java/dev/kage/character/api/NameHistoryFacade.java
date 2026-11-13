package dev.kage.character.api;

import java.util.List;
import java.util.Optional;

import dev.kage.character.domain.NameHistoryPort;

/** API tra tên cũ (ADR-343): người chơi xem các tên trước đây của một nhân vật. Chạm DB — Virtual Thread. */
public final class NameHistoryFacade {

    /** Không có lịch sử nào (composition test). */
    public static final NameHistoryFacade DISABLED = new NameHistoryFacade(new NameHistoryPort() {
        @Override
        public List<Entry> history(long characterId) {
            return List.of();
        }

        @Override
        public Optional<Long> characterWithOldName(String oldName) {
            return Optional.empty();
        }
    });

    private final NameHistoryPort port;

    /** Dựng ở composition root. */
    public NameHistoryFacade(NameHistoryPort port) {
        this.port = port;
    }

    /** Tên cũ, mới trước. */
    public List<NameHistoryPort.Entry> history(long characterId) {
        return port.history(characterId);
    }

    /** Ai từng mang tên này. */
    public Optional<Long> characterWithOldName(String oldName) {
        return port.characterWithOldName(oldName);
    }
}
