package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.ChooseEncounterOptionCommand;
import dev.kage.protocol.ChooseEncounterOptionResponse;
import dev.kage.protocol.EncounterChoiceOutcome;
import dev.kage.protocol.EncounterOptionView;
import dev.kage.protocol.EncounterStartedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.WorldFacade;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.domain.EncounterPort;

/**
 * Kỳ ngộ (ADR-121) — bước phụ của đường thưởng hạ quái (chỉ người ra đòn cuối, chỉ ở world chính) và lệnh chọn. Lựa
 * chọn gọi quái thì sinh một con cách người chơi {@value #SPAWN_OFFSET} m ở world chính. Nối sau khi DB đã dựng, cùng
 * khuôn {@link BountyHooks}.
 */
final class EncounterHooks {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterHooks.class);

    /** Quái kỳ ngộ hiện cách người chơi chừng này mét theo trục ngang — đủ xa để không đánh ngay, đủ gần để thấy. */
    static final float SPAWN_OFFSET = 4f;

    /** Thứ việc này cần. */
    record Deps(
            EncounterFacade facade,
            ConnectedCharacterRegistry registry,
            LongPredicate inInstance,
            LongFunction<Optional<WorldFacade>> worldOf,
            LongFunction<Optional<PositionSnapshot>> positionOf,
            Clock clock) {}

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    // PMD AvoidCatchingGenericException: việc phụ — lỗi dừng ở đây, phần thưởng chính không suy suyển.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onMonsterKilled(long killerCharacterId) {
        Optional<Deps> current = deps;
        if (current.isEmpty() || current.get().inInstance().test(killerCharacterId)) {
            return;
        }
        Deps d = current.get();
        try {
            d.facade()
                    .onMonsterKilled(killerCharacterId, d.clock().instant())
                    .ifPresent(offer -> announce(d, killerCharacterId, offer));
        } catch (RuntimeException e) {
            LOG.warn("Mở kỳ ngộ lỗi: characterId={}", killerCharacterId, e);
        }
    }

    private static void announce(Deps d, long characterId, EncounterPort.Offer offer) {
        EncounterStartedEvent.Builder event = EncounterStartedEvent.newBuilder()
                .setInstanceId(offer.instanceId())
                .setPromptKey(offer.promptKey())
                .setExpiresAtEpochMs(offer.expiresAt().toEpochMilli());
        for (EncounterPort.Option option : offer.options()) {
            event.addOptions(
                    EncounterOptionView.newBuilder().setIndex(option.index()).setLabelKey(option.labelKey()));
        }
        d.registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_ENCOUNTER_STARTED_EVENT_VALUE,
                        0,
                        event.build().toByteArray());
    }

    /** Chọn một lựa chọn (Virtual Thread); thưởng đã cấp trong giao dịch, ở đây chỉ sinh quái nếu có. */
    ChooseEncounterOptionResponse choose(long characterId, ChooseEncounterOptionCommand command) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return ChooseEncounterOptionResponse.newBuilder()
                    .setOutcome(EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_NOT_FOUND)
                    .build();
        }
        Deps d = current.get();
        EncounterPort.Resolution resolution = d.facade()
                .choose(
                        characterId,
                        command.getInstanceId(),
                        command.getOptionIndex(),
                        d.clock().instant());
        boolean spawned = resolution.outcome() == EncounterPort.Outcome.OK
                && resolution.spawnMonsterCode().isPresent()
                && spawn(d, characterId, resolution.spawnMonsterCode().get());
        return ChooseEncounterOptionResponse.newBuilder()
                .setOutcome(wire(resolution.outcome()))
                .setResultKey(resolution.resultKey())
                .setGold(resolution.gold())
                .setItemTemplateId(resolution.itemTemplateId())
                .setQuantity(resolution.quantity())
                .setMonsterSpawned(spawned)
                .build();
    }

    private static boolean spawn(Deps d, long characterId, String monsterCode) {
        if (d.inInstance().test(characterId)) {
            return false;
        }
        Optional<PositionSnapshot> position = d.positionOf().apply(characterId);
        Optional<WorldFacade> world = d.worldOf().apply(characterId);
        if (position.isEmpty() || world.isEmpty()) {
            return false;
        }
        world.get()
                .spawnMonsterOnce(
                        monsterCode,
                        position.get().x() + SPAWN_OFFSET,
                        position.get().y());
        return true;
    }

    private static EncounterChoiceOutcome wire(EncounterPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_OK;
            case NOT_FOUND -> EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_NOT_FOUND;
            case INVALID_OPTION -> EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_INVALID_OPTION;
            case BAG_FULL -> EncounterChoiceOutcome.ENCOUNTER_CHOICE_OUTCOME_BAG_FULL;
        };
    }
}
