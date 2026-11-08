package dev.kage.character.api;

import java.time.Instant;
import java.util.Map;

import dev.kage.character.domain.PersonalRecordPort;

/** API kỷ lục cá nhân (ADR-240). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class PersonalRecordFacade {

    /** Không lưu gì (composition test). */
    public static final PersonalRecordFacade DISABLED = new PersonalRecordFacade(new PersonalRecordPort() {
        @Override
        public Map<Kind, Long> recordsOf(long characterId) {
            return Map.of();
        }

        @Override
        public boolean raise(long characterId, Kind kind, long value, Instant at) {
            return false;
        }
    });

    private final PersonalRecordPort port;

    /** Dựng ở composition root. */
    public PersonalRecordFacade(PersonalRecordPort port) {
        this.port = port;
    }

    /** Kỷ lục {@code kind} của {@code characterId}; 0 nếu chưa có. */
    public long recordOf(long characterId, PersonalRecordPort.Kind kind) {
        return port.recordsOf(characterId).getOrDefault(kind, 0L);
    }

    /** Mọi kỷ lục đã có. */
    public Map<PersonalRecordPort.Kind, Long> recordsOf(long characterId) {
        return port.recordsOf(characterId);
    }

    /** Nâng kỷ lục nếu lớn hơn; {@code true} nếu đã nâng. */
    public boolean raise(long characterId, PersonalRecordPort.Kind kind, long value, Instant at) {
        return port.raise(characterId, kind, value, at);
    }
}
