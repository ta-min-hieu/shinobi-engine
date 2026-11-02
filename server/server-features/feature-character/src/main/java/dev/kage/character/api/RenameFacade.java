package dev.kage.character.api;

import java.time.Instant;
import java.util.Optional;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.RenamePort;

/**
 * Dịch Danh Phù (ADR-148). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. Tên mới qua ĐÚNG luật
 * tên lúc tạo nhân vật (chuẩn hoá NFC, {@link Character#validateNewName}) trước khi chạm DB.
 */
public final class RenameFacade {

    /** Composition không có đổi tên (test) — luôn từ chối. */
    public static final RenameFacade DISABLED = new RenameFacade(
            (characterId, newName, now) -> new RenamePort.Result(RenamePort.Outcome.NOT_FOUND, "", Optional.empty()));

    private final RenamePort port;

    /** Dựng ở composition root. */
    public RenameFacade(RenamePort port) {
        this.port = port;
    }

    /** Đổi tên nhân vật thành {@code requestedName} (chuỗi thô từ client). */
    public RenamePort.Result rename(long characterId, String requestedName, Instant now) {
        String normalized = Character.normalizeNewName(requestedName);
        if (normalized == null) {
            return invalid();
        }
        try {
            Character.validateNewName(normalized);
        } catch (IllegalArgumentException rejected) {
            return invalid();
        }
        return port.rename(characterId, normalized, now);
    }

    private static RenamePort.Result invalid() {
        return new RenamePort.Result(RenamePort.Outcome.INVALID_NAME, "", Optional.empty());
    }
}
