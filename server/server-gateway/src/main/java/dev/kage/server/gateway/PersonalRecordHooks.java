package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.PersonalRecordFacade;
import dev.kage.character.domain.PersonalRecordPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.SkillEffectKind;
import dev.kage.server.world.WorldOutboundSink;

/**
 * Kỷ lục cá nhân (ADR-240): đòn mạnh nhất và chuỗi hạ quái dài nhất. Các hàm {@code on*} chạy trên TICK THREAD — chỉ
 * so sánh trong RAM; khi phá kỷ lục mới đẩy việc ghi DB + báo chat sang Virtual Thread. Kỷ lục đã lưu được nạp lúc
 * vào world; trước khi nạp xong thì không tính (không báo nhầm "kỷ lục mới" chỉ vì chưa biết kỷ lục cũ).
 */
final class PersonalRecordHooks {

    private static final Logger LOG = LoggerFactory.getLogger(PersonalRecordHooks.class);

    /** Trạng thái RAM của một nhân vật; mọi truy cập khoá trên chính nó. */
    private static final class State {
        private long bestHit;
        private long bestStreak;
        private long streak;
        private boolean streakAnnounced;

        State(long bestHit, long bestStreak) {
            this.bestHit = bestHit;
            this.bestStreak = bestStreak;
        }
    }

    private final Map<Long, State> states = new ConcurrentHashMap<>();
    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn DeathRecapHooks.
    private volatile Optional<PersonalRecordFacade> facade = Optional.empty();
    private volatile Optional<ConnectedCharacterRegistry> registry = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(PersonalRecordFacade realFacade, ConnectedCharacterRegistry realRegistry) {
        this.facade = Optional.of(realFacade);
        this.registry = Optional.of(realRegistry);
    }

    /** Luồng blocking lúc vào world — nạp kỷ lục đã lưu (giữ chuỗi hiện tại nếu đang có). */
    void load(long characterId) {
        facade.ifPresent(f -> {
            Map<PersonalRecordPort.Kind, Long> saved = f.recordsOf(characterId);
            State fresh = new State(
                    saved.getOrDefault(PersonalRecordPort.Kind.HIGHEST_HIT, 0L),
                    saved.getOrDefault(PersonalRecordPort.Kind.LONGEST_KILL_STREAK, 0L));
            State old = states.putIfAbsent(characterId, fresh);
            if (old != null) {
                synchronized (old) {
                    old.bestHit = Math.max(old.bestHit, fresh.bestHit);
                    old.bestStreak = Math.max(old.bestStreak, fresh.bestStreak);
                }
            }
        });
    }

    /** Chuỗi hạ quái hiện tại (0 nếu chưa có). */
    long currentStreak(long characterId) {
        State state = states.get(characterId);
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            return state.streak;
        }
    }

    /** Tick thread — {@code observerId} vừa thấy một đòn đánh thường; tính nếu chính họ ra đòn. */
    void onAttackObserved(long observerId, WorldOutboundSink.AttackOutcome outcome) {
        if (observerId == outcome.attackerEntityId() && !outcome.miss() && outcome.targetEntityId() != observerId) {
            onHit(observerId, outcome.damage());
        }
    }

    /** Tick thread — như {@link #onAttackObserved} cho kỹ năng sát thương. */
    void onSkillObserved(long observerId, WorldOutboundSink.SkillOutcome outcome) {
        if (observerId == outcome.attackerEntityId()
                && !outcome.miss()
                && outcome.targetEntityId() != observerId
                && outcome.effectKind() == SkillEffectKind.DAMAGE) {
            onHit(observerId, outcome.damage());
        }
    }

    private void onHit(long characterId, long damage) {
        State state = states.get(characterId);
        if (state == null || damage <= 0) {
            return;
        }
        boolean announce;
        synchronized (state) {
            if (damage <= state.bestHit) {
                return;
            }
            announce = state.bestHit > 0;
            state.bestHit = damage;
        }
        writers.execute(() -> persist(characterId, PersonalRecordPort.Kind.HIGHEST_HIT, damage, announce));
    }

    /** Tick thread — {@code killerId} vừa hạ một con quái. */
    void onKill(long killerId) {
        State state = states.get(killerId);
        if (state == null) {
            return;
        }
        long streak;
        boolean announce;
        synchronized (state) {
            state.streak++;
            if (state.streak <= state.bestStreak) {
                return;
            }
            streak = state.streak;
            announce = !state.streakAnnounced && state.bestStreak > 0;
            state.streakAnnounced = true;
            state.bestStreak = streak;
        }
        writers.execute(() -> persist(killerId, PersonalRecordPort.Kind.LONGEST_KILL_STREAK, streak, announce));
    }

    /** Tick thread — nhân vật bị hạ: chuỗi về 0. */
    void onDefeated(long characterId) {
        State state = states.get(characterId);
        if (state != null) {
            synchronized (state) {
                state.streak = 0;
                state.streakAnnounced = false;
            }
        }
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void persist(long characterId, PersonalRecordPort.Kind kind, long value, boolean announce) {
        try {
            facade.ifPresent(f -> f.raise(characterId, kind, value, Instant.now()));
            if (announce) {
                registry.ifPresent(r -> r.sendToCharacterIfOnline(
                        characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, notice(kind, value)));
            }
        } catch (RuntimeException e) {
            LOG.warn("Ghi kỷ lục lỗi: characterId={}, kind={}", characterId, kind, e);
        }
    }

    /** Dòng chat hệ thống song ngữ báo phá kỷ lục. Tĩnh để test. */
    static byte[] notice(PersonalRecordPort.Kind kind, long value) {
        String body = kind == PersonalRecordPort.Kind.HIGHEST_HIT
                ? "Kỷ lục mới — đòn mạnh nhất: " + value + " / New record — highest hit: " + value
                : "Kỷ lục mới — chuỗi hạ quái: " + value + " / New record — kill streak: " + value;
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build()
                .toByteArray();
    }
}
