package dev.kage.character.api;

import java.time.Instant;
import java.util.List;

import dev.kage.character.domain.MilestonePort;

/** Quà mốc cảnh giới (ADR-153). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class MilestoneFacade {

    /** Composition không có quà mốc (test) — bảng rỗng, không nhận được gì. */
    public static final MilestoneFacade DISABLED = new MilestoneFacade(new MilestonePort() {
        @Override
        public Board board(long characterId) {
            return new Board(1, List.of());
        }

        @Override
        public Outcome claim(long characterId, int level, Instant now) {
            return Outcome.UNKNOWN_MILESTONE;
        }
    });

    private final MilestonePort port;

    /** Dựng ở composition root. */
    public MilestoneFacade(MilestonePort port) {
        this.port = port;
    }

    /** Bảng mốc của nhân vật. */
    public MilestonePort.Board board(long characterId) {
        return port.board(characterId);
    }

    /** Nhận quà một mốc. */
    public MilestonePort.Outcome claim(long characterId, int level, Instant now) {
        return port.claim(characterId, level, now);
    }
}
