package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import dev.kage.character.domain.PositionSnapshot;
import dev.kage.protocol.TreasureAction;
import dev.kage.protocol.TreasureHuntCommand;
import dev.kage.protocol.TreasureHuntResponse;
import dev.kage.protocol.TreasureOutcome;
import dev.kage.server.world.WorldFacade;
import dev.kage.world.api.TreasureFacade;
import dev.kage.world.domain.TreasurePort;
import dev.kage.world.domain.TreasureRules;

/**
 * Tàng Bảo Đồ (ADR-147) — một lệnh, trường {@code action}: hỏi cuộc tìm, đọc bản đồ, đào. Server kiểm vị trí (mẫu
 * world chính của registry, hoặc chỗ đã lưu nếu vừa vào chưa nhúc nhích) trước khi mở giao dịch đào; phần thưởng có
 * quái canh kho thì sinh một con cách người đào {@value TreasureRules#GUARDIAN_OFFSET_METERS} m. Nối sau khi DB đã
 * dựng, cùng khuôn {@link EncounterHooks}.
 */
final class TreasureHooks {

    /** Thứ việc này cần. */
    record Deps(
            TreasureFacade facade,
            LongPredicate inInstance,
            LongFunction<Optional<WorldFacade>> worldOf,
            LongFunction<Optional<PositionSnapshot>> positionOf,
            Clock clock) {}

    private static final float UNKNOWN_DISTANCE = -1f;

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Xử lý một lệnh (Virtual Thread); {@code characterId} từ session. */
    TreasureHuntResponse handle(long characterId, TreasureHuntCommand command) {
        // Giá trị lạ (client mới hơn / gói hỏng) và UNSPECIFIED đều coi là hỏi trạng thái — không bao giờ tiêu gì.
        TreasureAction action =
                switch (command.getAction()) {
                    case TREASURE_ACTION_READ, TREASURE_ACTION_DIG -> command.getAction();
                    case TREASURE_ACTION_STATUS, TREASURE_ACTION_UNSPECIFIED, UNRECOGNIZED ->
                        TreasureAction.TREASURE_ACTION_STATUS;
                };
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return reply(action, TreasureOutcome.TREASURE_OUTCOME_NO_HUNT).build();
        }
        Deps d = current.get();
        return switch (action) {
            case TREASURE_ACTION_READ -> read(d, characterId);
            case TREASURE_ACTION_DIG -> dig(d, characterId);
            default -> status(d, characterId);
        };
    }

    private static TreasureHuntResponse status(Deps d, long characterId) {
        Optional<TreasurePort.Spot> spot = d.facade().activeHunt(characterId);
        TreasureOutcome outcome =
                spot.isPresent() ? TreasureOutcome.TREASURE_OUTCOME_OK : TreasureOutcome.TREASURE_OUTCOME_NO_HUNT;
        return withHunt(reply(TreasureAction.TREASURE_ACTION_STATUS, outcome), d, characterId, spot)
                .build();
    }

    private static TreasureHuntResponse read(Deps d, long characterId) {
        TreasurePort.ReadResult result = d.facade().read(characterId, d.clock().instant());
        return withHunt(
                        reply(TreasureAction.TREASURE_ACTION_READ, wire(result.outcome())),
                        d,
                        characterId,
                        result.spot())
                .build();
    }

    private static TreasureHuntResponse dig(Deps d, long characterId) {
        Optional<TreasurePort.Spot> hunt = d.facade().activeHunt(characterId);
        if (hunt.isEmpty()) {
            return reply(TreasureAction.TREASURE_ACTION_DIG, TreasureOutcome.TREASURE_OUTCOME_NO_HUNT)
                    .build();
        }
        Optional<PositionSnapshot> position = mainWorldPosition(d, characterId);
        if (position.isEmpty()) {
            return withHunt(
                            reply(
                                    TreasureAction.TREASURE_ACTION_DIG,
                                    TreasureOutcome.TREASURE_OUTCOME_NOT_IN_MAIN_WORLD),
                            d,
                            characterId,
                            hunt)
                    .build();
        }
        if (!TreasureRules.withinReach(position.get().x(), hunt.get())) {
            return withHunt(
                            reply(TreasureAction.TREASURE_ACTION_DIG, TreasureOutcome.TREASURE_OUTCOME_TOO_FAR),
                            d,
                            characterId,
                            hunt)
                    .build();
        }
        TreasurePort.DigResult result =
                d.facade().dig(characterId, hunt.get().id(), d.clock().instant());
        boolean guardian = result.outcome() == TreasurePort.Outcome.OK
                && result.spawnGuardian()
                && result.spot().isPresent()
                && spawnGuardian(d, characterId, position.get(), result.spot().get());
        TreasureHuntResponse.Builder response = reply(TreasureAction.TREASURE_ACTION_DIG, wire(result.outcome()))
                .setResultKey(result.resultKey())
                .setGold(result.gold())
                .setItemTemplateId(result.itemTemplateId())
                .setQuantity(result.quantity())
                .setGuardianSpawned(guardian);
        // OK ⇒ cuộc tìm đã đóng (spot trong kết quả là kho vừa đào); từ chối ⇒ cuộc tìm còn lại.
        Optional<TreasurePort.Spot> remaining =
                result.outcome() == TreasurePort.Outcome.OK ? Optional.empty() : result.spot();
        return withHunt(response, d, characterId, remaining).build();
    }

    private static boolean spawnGuardian(Deps d, long characterId, PositionSnapshot position, TreasurePort.Spot spot) {
        Optional<WorldFacade> world = d.worldOf().apply(characterId);
        if (world.isEmpty() || d.inInstance().test(characterId)) {
            return false;
        }
        world.get()
                .spawnMonsterOnce(
                        spot.guardianMonsterCode(), position.x() + TreasureRules.GUARDIAN_OFFSET_METERS, position.y());
        return true;
    }

    private static Optional<PositionSnapshot> mainWorldPosition(Deps d, long characterId) {
        if (d.inInstance().test(characterId)) {
            return Optional.empty();
        }
        return d.positionOf().apply(characterId);
    }

    private static TreasureHuntResponse.Builder reply(TreasureAction action, TreasureOutcome outcome) {
        return TreasureHuntResponse.newBuilder()
                .setAction(action)
                .setOutcome(outcome)
                .setDistance(UNKNOWN_DISTANCE);
    }

    private static TreasureHuntResponse.Builder withHunt(
            TreasureHuntResponse.Builder response, Deps d, long characterId, Optional<TreasurePort.Spot> spot) {
        if (spot.isEmpty()) {
            return response.setHunting(false);
        }
        TreasurePort.Spot s = spot.get();
        response.setHunting(true).setSpotNameKey(s.nameKey()).setSpotX(s.x());
        mainWorldPosition(d, characterId)
                .ifPresent(position -> response.setDistance(TreasureRules.distance(position.x(), s)));
        return response;
    }

    private static TreasureOutcome wire(TreasurePort.Outcome outcome) {
        return switch (outcome) {
            case OK -> TreasureOutcome.TREASURE_OUTCOME_OK;
            case NO_HUNT -> TreasureOutcome.TREASURE_OUTCOME_NO_HUNT;
            case NO_MAP -> TreasureOutcome.TREASURE_OUTCOME_NO_MAP;
            case ALREADY_HUNTING -> TreasureOutcome.TREASURE_OUTCOME_ALREADY_HUNTING;
            case BAG_FULL -> TreasureOutcome.TREASURE_OUTCOME_BAG_FULL;
        };
    }
}
