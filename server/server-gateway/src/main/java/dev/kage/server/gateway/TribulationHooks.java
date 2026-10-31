package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.TribulationFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.TribulationRules;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StartTribulationResponse;
import dev.kage.protocol.TribulationEvent;
import dev.kage.protocol.TribulationEventKind;
import dev.kage.protocol.TribulationOutcome;

/**
 * Thiên Kiếp (ADR-126) — đưa người độ kiếp vào một World riêng không quái ({@link DungeonInstanceRegistry#enterSolo}),
 * rồi nhịp {@value #TICK_SECONDS} giây trong {@link ExpansionJobs} giáng từng đợt lôi kích. Gục (đường gục của hầm ngục
 * tự đưa về world chính) hoặc rời giữa chừng ⇒ thua: trừ EXP. Sống qua đợt cuối thêm một nhịp ⇒ thắng: ghi kiếp
 * TRƯỚC khi đưa về (lượt gắn lại world chính đọc chỉ số mới), rồi đồng bộ vạch EXP (lên cấp ngay vì EXP đã đủ).
 * Trạng thái kiếp chỉ ở RAM: server khởi động lại giữa kiếp thì kiếp đó coi như chưa từng diễn ra, không phạt ai.
 */
final class TribulationHooks {

    private static final Logger LOG = LoggerFactory.getLogger(TribulationHooks.class);
    static final String INSTANCE_CODE = "heavenly_tribulation";
    private static final long TICK_SECONDS = 1L;
    private static final Duration FIRST_STRIKE_DELAY = Duration.ofSeconds(5);
    private static final Duration STRIKE_INTERVAL = Duration.ofSeconds(TribulationRules.STRIKE_INTERVAL_SECONDS);

    /** Thứ việc này cần. */
    record Deps(
            TribulationFacade facade,
            CharacterFacade characters,
            DungeonInstanceRegistry dungeons,
            ConnectedCharacterRegistry registry,
            Clock clock) {}

    /** Một kiếp đang diễn ra. */
    private record Trial(int passed, int cap, int waves, int struck, Instant nextStrikeAt) {}

    private final Map<Long, Trial> trials = new ConcurrentHashMap<>();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Nhịp giáng lôi cho {@link ExpansionJobs}. */
    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job("thiên kiếp", TICK_SECONDS, () -> deps.ifPresent(this::tick));
    }

    /** Lệnh {@code /dokiep} (Virtual Thread). */
    StartTribulationResponse start(long characterId) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return response(TribulationOutcome.TRIBULATION_OUTCOME_NOT_READY, 0);
        }
        Deps d = current.get();
        if (trials.containsKey(characterId)) {
            return response(TribulationOutcome.TRIBULATION_OUTCOME_ALREADY_IN_TRIAL, 0);
        }
        int passed = d.facade().passed(characterId);
        Optional<Character> character = d.characters().findById(characterId);
        if (character.isEmpty()
                || !TribulationRules.ready(passed, character.get().exp())) {
            return response(TribulationOutcome.TRIBULATION_OUTCOME_NOT_READY, 0);
        }
        DungeonInstanceRegistry.EnterOutcome entered = d.dungeons().enterSolo(characterId, INSTANCE_CODE);
        if (entered != DungeonInstanceRegistry.EnterOutcome.OK || !d.dungeons().isInDungeon(characterId)) {
            return response(TribulationOutcome.TRIBULATION_OUTCOME_BUSY, 0);
        }
        int waves = TribulationRules.waves(passed);
        trials.put(
                characterId,
                new Trial(
                        passed,
                        TribulationRules.levelCap(passed),
                        waves,
                        0,
                        d.clock().instant().plus(FIRST_STRIKE_DELAY)));
        return response(TribulationOutcome.TRIBULATION_OUTCOME_OK, waves);
    }

    // PMD AvoidCatchingGenericException: ranh giới tác vụ nền — một kiếp hỏng (DB chập) không được giết các kiếp khác.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void tick(Deps d) {
        Instant now = d.clock().instant();
        for (Map.Entry<Long, Trial> entry : trials.entrySet()) {
            try {
                step(d, entry.getKey(), entry.getValue(), now);
            } catch (RuntimeException e) {
                LOG.warn("Nhịp Thiên Kiếp lỗi: characterId={}", entry.getKey(), e);
            }
        }
    }

    private void step(Deps d, long characterId, Trial trial, Instant now) {
        if (!d.dungeons().isInDungeon(characterId)) {
            fail(d, characterId, trial);
            return;
        }
        if (now.isBefore(trial.nextStrikeAt())) {
            return;
        }
        if (trial.struck() >= trial.waves()) {
            succeed(d, characterId, trial);
            return;
        }
        int wave = trial.struck() + 1;
        d.dungeons()
                .worldFacadeFor(characterId)
                .ifPresent(world -> world.strikeCharacter(characterId, TribulationRules.STRIKE_PERCENT));
        trials.put(characterId, new Trial(trial.passed(), trial.cap(), trial.waves(), wave, now.plus(STRIKE_INTERVAL)));
        notify(d, characterId, event(TribulationEventKind.TRIBULATION_EVENT_KIND_STRIKE, wave, trial.waves()));
    }

    private void fail(Deps d, long characterId, Trial trial) {
        if (trials.remove(characterId) == null) {
            return;
        }
        d.facade().penalize(characterId, trial.cap());
        d.registry().sendExperience(characterId, d.characters().syncExperienceAfterQuestReward(characterId));
        notify(
                d,
                characterId,
                event(TribulationEventKind.TRIBULATION_EVENT_KIND_FAILED, trial.struck(), trial.waves()));
    }

    private void succeed(Deps d, long characterId, Trial trial) {
        if (trials.remove(characterId) == null) {
            return;
        }
        d.facade().advance(characterId, trial.passed());
        d.dungeons().exitDungeon(characterId);
        d.registry().sendExperience(characterId, d.characters().syncExperienceAfterQuestReward(characterId));
        notify(d, characterId, event(TribulationEventKind.TRIBULATION_EVENT_KIND_PASSED, trial.waves(), trial.waves()));
    }

    private static TribulationEvent event(TribulationEventKind kind, int wave, int waves) {
        return TribulationEvent.newBuilder()
                .setKind(kind)
                .setWave(wave)
                .setWaves(waves)
                .build();
    }

    private static void notify(Deps d, long characterId, TribulationEvent event) {
        d.registry()
                .sendToCharacterIfOnline(characterId, Opcode.OPCODE_TRIBULATION_EVENT_VALUE, 0, event.toByteArray());
    }

    private static StartTribulationResponse response(TribulationOutcome outcome, int waves) {
        return StartTribulationResponse.newBuilder()
                .setOutcome(outcome)
                .setWaves(waves)
                .setStrikePercent(TribulationRules.STRIKE_PERCENT)
                .setIntervalSeconds(TribulationRules.STRIKE_INTERVAL_SECONDS)
                .build();
    }
}
