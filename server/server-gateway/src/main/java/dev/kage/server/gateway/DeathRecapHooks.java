package dev.kage.server.gateway;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.protocol.DeathRecapEntry;
import dev.kage.protocol.DeathRecapEvent;
import dev.kage.protocol.Opcode;

/**
 * Tóm tắt khi chết (ADR-195). Mỗi đòn trúng một nhân vật (đòn đánh thường hoặc kỹ năng sát thương) được ghi vào hàng
 * đợi RAM của người bị trúng — tối đa {@link #MAX_HITS} đòn. Lúc người đó bị hạ: gom các đòn trong
 * {@link #WINDOW_SECONDS} giây cuối theo kẻ ra đòn, rồi (trên Virtual Thread — tên người chơi chạm DB) gửi
 * {@code DeathRecapEvent} cho chính người bị hạ. Quái chỉ gửi id: client tự đặt tên theo gói spawn đã thấy.
 */
final class DeathRecapHooks {

    /** Nhìn lại chừng này giây trước lúc bị hạ. */
    static final int WINDOW_SECONDS = 10;

    /** Trần đòn giữ trong RAM mỗi nhân vật — đủ cho 10 giây bị vây đánh, không phình theo thời gian. */
    static final int MAX_HITS = 64;

    private static final Logger LOG = LoggerFactory.getLogger(DeathRecapHooks.class);
    private static final long WINDOW_MILLIS = WINDOW_SECONDS * 1000L;

    /** Một đòn trúng. {@code skillCode} rỗng = đòn đánh thường. */
    record Hit(long attackerId, int damage, String skillCode, long atMillis) {}

    /** Tổng của một kẻ ra đòn. */
    private static final class Total {
        int damage;
        int hits;
        String topSkill = "";
        int topSkillDamage;
    }

    private final Map<Long, Deque<Hit>> hits = new ConcurrentHashMap<>();
    private final ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor();
    private final LongSupplier clockMillis;
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn EnemyHooks.
    private volatile Optional<CharacterFacade> characters = Optional.empty();

    DeathRecapHooks(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** ADR-223 — đếm lần bị hạ (thống kê cả đời); mặc định không đếm. */
    private volatile java.util.function.LongConsumer deathCounter = characterId -> {};

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(CharacterFacade realCharacters) {
        this.characters = Optional.of(realCharacters);
    }

    /** ADR-223 — nối bộ đếm lần bị hạ (chạm DB: chạy trên Virtual Thread riêng). */
    void bindDeathCounter(java.util.function.LongConsumer counter) {
        this.deathCounter = counter;
    }

    /** Tick thread — {@code observerId} vừa thấy một đòn đánh thường; ghi nếu chính họ là người bị trúng. */
    void onAttackObserved(long observerId, dev.kage.server.world.WorldOutboundSink.AttackOutcome outcome) {
        if (observerId == outcome.targetEntityId() && !outcome.miss()) {
            onHit(observerId, outcome.attackerEntityId(), outcome.damage(), "");
        }
    }

    /** Tick thread — như {@link #onAttackObserved} cho kỹ năng; chỉ kỹ năng loại sát thương. */
    void onSkillObserved(long observerId, dev.kage.server.world.WorldOutboundSink.SkillOutcome outcome) {
        if (observerId == outcome.targetEntityId()
                && !outcome.miss()
                && outcome.effectKind() == dev.kage.server.world.SkillEffectKind.DAMAGE) {
            onHit(observerId, outcome.attackerEntityId(), outcome.damage(), outcome.skillCode());
        }
    }

    /** Tick thread — một đòn {@code damage > 0} của {@code attackerId} vừa trúng nhân vật {@code victimId}. */
    void onHit(long victimId, long attackerId, int damage, String skillCode) {
        if (damage <= 0 || attackerId == victimId) {
            return;
        }
        Deque<Hit> queue = hits.computeIfAbsent(victimId, id -> new ArrayDeque<>());
        synchronized (queue) {
            if (queue.size() >= MAX_HITS) {
                queue.removeFirst();
            }
            queue.addLast(new Hit(attackerId, damage, skillCode, clockMillis.getAsLong()));
        }
    }

    /** Tick thread — nhân vật vừa bị hạ: gom và gửi tóm tắt (nếu có đòn nào trong cửa sổ). */
    void onDefeated(long victimId, long killerEntityId, ConnectedCharacterRegistry registry) {
        java.util.function.LongConsumer counter = deathCounter;
        senders.execute(() -> countDeath(counter, victimId)); // ADR-223
        Deque<Hit> queue = hits.get(victimId);
        if (queue == null) {
            return;
        }
        List<Hit> recent = new ArrayList<>();
        long since = clockMillis.getAsLong() - WINDOW_MILLIS;
        synchronized (queue) {
            for (Hit hit : queue) {
                if (hit.atMillis() >= since) {
                    recent.add(hit);
                }
            }
            queue.clear();
        }
        if (!recent.isEmpty()) {
            senders.execute(() -> send(victimId, killerEntityId, recent, registry));
        }
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void countDeath(java.util.function.LongConsumer counter, long victimId) {
        try {
            counter.accept(victimId);
        } catch (RuntimeException e) {
            LOG.warn("Đếm lần bị hạ lỗi: {}", victimId, e);
        }
    }

    /** Nhân vật rời world — bỏ hàng đợi. */
    void forget(long characterId) {
        hits.remove(characterId);
    }

    // PMD AvoidCatchingGenericException: việc phụ chạy trên Virtual Thread riêng — lỗi dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void send(long victimId, long killerEntityId, List<Hit> recent, ConnectedCharacterRegistry registry) {
        try {
            registry.sendToCharacterIfOnline(
                    victimId,
                    Opcode.OPCODE_DEATH_RECAP_EVENT_VALUE,
                    0,
                    recap(killerEntityId, recent).toByteArray());
        } catch (RuntimeException e) {
            LOG.warn("Gửi tóm tắt khi chết lỗi: victim={}", victimId, e);
        }
    }

    /** Gom theo kẻ ra đòn, nhiều sát thương nhất trước. Gói lại tách riêng để test. */
    DeathRecapEvent recap(long killerEntityId, List<Hit> recent) {
        Map<Long, Total> totals = new LinkedHashMap<>();
        Map<Long, Map<String, Integer>> bySkill = new LinkedHashMap<>();
        for (Hit hit : recent) {
            Total total = totals.computeIfAbsent(hit.attackerId(), id -> new Total());
            total.damage += hit.damage();
            total.hits++;
            int skillDamage = bySkill.computeIfAbsent(hit.attackerId(), id -> new LinkedHashMap<>())
                    .merge(hit.skillCode(), hit.damage(), Integer::sum);
            if (skillDamage > total.topSkillDamage) {
                total.topSkillDamage = skillDamage;
                total.topSkill = hit.skillCode();
            }
        }
        DeathRecapEvent.Builder event =
                DeathRecapEvent.newBuilder().setKillerEntityId(killerEntityId).setWindowSeconds(WINDOW_SECONDS);
        totals.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().damage, a.getValue().damage))
                .forEach(entry -> event.addEntries(DeathRecapEntry.newBuilder()
                        .setAttackerEntityId(entry.getKey())
                        .setAttackerName(nameOf(entry.getKey()))
                        .setDamage(entry.getValue().damage)
                        .setHits(entry.getValue().hits)
                        .setTopSkillCode(entry.getValue().topSkill)));
        return event.build();
    }

    /** Tên người chơi (chạm DB); quái (id âm) để rỗng — client tự đặt tên. */
    private String nameOf(long entityId) {
        if (entityId <= 0L) {
            return "";
        }
        return characters
                .flatMap(c -> c.findById(entityId))
                .map(Character::name)
                .orElse("");
    }
}
