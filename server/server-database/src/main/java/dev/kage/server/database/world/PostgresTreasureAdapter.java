package dev.kage.server.database.world;

import java.io.Serial;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;
import dev.kage.world.domain.TreasurePort;
import dev.kage.world.domain.TreasureRules;

/**
 * Hiện thực {@link TreasurePort} (ADR-147, V119). Đọc bản đồ và đào là mỗi lệnh MỘT giao dịch mở đầu bằng khoá túi của
 * nhân vật — đúng thứ tự khoá chung (DATABASE.md §5.1) và tuần tự hoá "một cuộc tìm đang mở" giữa các lệnh song song.
 */
public final class PostgresTreasureAdapter implements TreasurePort {

    private static final String REASON = "gold.reason.treasure";

    private static final String ACTIVE = """
            SELECT s.id, s.name_key, s.x, s.y, s.guardian_monster_code
              FROM character_treasure_hunt h
              JOIN treasure_spot s ON s.id = h.spot_id
             WHERE h.character_id = ?
            """;

    private static final String ACTIVE_FOR_UPDATE = ACTIVE + "   FOR UPDATE OF h\n";

    private static final String SPOTS =
            "SELECT id, name_key, x, y, guardian_monster_code FROM treasure_spot ORDER BY id";

    private static final String MAP_TEMPLATE = "SELECT id FROM item_template WHERE code = ?";

    private static final String START = """
            INSERT INTO character_treasure_hunt (character_id, spot_id, started_at) VALUES (?, ?, ?)
            """;

    private static final String REWARDS = """
            SELECT weight, result_key, reward_gold, COALESCE(reward_item_template_id, 0), reward_quantity,
                   spawns_guardian
              FROM treasure_reward
             ORDER BY id
            """;

    private static final String FINISH = "DELETE FROM character_treasure_hunt WHERE character_id = ? AND spot_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một dòng phần thưởng để chọn theo trọng số. */
    private record Reward(
            int weight, String resultKey, long gold, long itemTemplateId, int quantity, boolean spawnsGuardian) {}

    /** Một lệnh đào. */
    private record Dig(long characterId, long expectedSpotId, Instant now, ToIntFunction<int[]> pickReward) {}

    private final DataSource dataSource;

    public PostgresTreasureAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Spot> activeHunt(long characterId) {
        return JdbcOperations.queryOne(dataSource, ACTIVE, PostgresTreasureAdapter::spot, characterId);
    }

    @Override
    public ReadResult read(long characterId, Instant now, ToIntFunction<int[]> pickSpot) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            Optional<Spot> current =
                    JdbcOperations.queryOne(connection, ACTIVE, PostgresTreasureAdapter::spot, characterId);
            if (current.isPresent()) {
                return new ReadResult(Outcome.ALREADY_HUNTING, current);
            }
            List<Spot> spots = JdbcOperations.query(connection, SPOTS, PostgresTreasureAdapter::spot);
            Optional<Long> mapTemplate =
                    JdbcOperations.queryOne(connection, MAP_TEMPLATE, rs -> rs.getLong(1), TreasureRules.MAP_ITEM_CODE);
            if (spots.isEmpty()
                    || mapTemplate.isEmpty()
                    || !TemplateQuantityConsumer.consume(connection, characterId, mapTemplate.get(), 1)) {
                return new ReadResult(Outcome.NO_MAP, Optional.<Spot>empty());
            }
            int[] even = new int[spots.size()];
            Arrays.fill(even, 1);
            Spot chosen = spots.get(pickSpot.applyAsInt(even));
            JdbcOperations.update(connection, START, characterId, chosen.id(), Timestamp.from(now));
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    "TREASURE_MAP_READ",
                    "{\"spotId\":" + chosen.id() + "}",
                    Timestamp.from(now));
            return new ReadResult(Outcome.OK, Optional.of(chosen));
        });
    }

    @Override
    public DigResult dig(long characterId, long expectedSpotId, Instant now, ToIntFunction<int[]> pickReward) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> digIn(connection, new Dig(characterId, expectedSpotId, now, pickReward)));
        } catch (Abort abort) {
            // Giao dịch đã rollback — đọc lại cuộc tìm còn lại để client vẫn thấy đích.
            return DigResult.refused(abort.outcome, activeHunt(characterId));
        }
    }

    private static DigResult digIn(Connection connection, Dig dig) {
        long characterId = dig.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        Spot spot = JdbcOperations.queryOne(connection, ACTIVE_FOR_UPDATE, PostgresTreasureAdapter::spot, characterId)
                .filter(s -> s.id() == dig.expectedSpotId())
                .orElseThrow(() -> new Abort(Outcome.NO_HUNT));
        List<Reward> rewards = JdbcOperations.query(
                connection,
                REWARDS,
                rs -> new Reward(
                        rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getBoolean(6)));
        if (rewards.isEmpty()) {
            throw new Abort(Outcome.NO_HUNT);
        }
        Reward reward = rewards.get(dig.pickReward()
                .applyAsInt(rewards.stream().mapToInt(Reward::weight).toArray()));
        if (reward.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, reward.itemTemplateId(), reward.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, reward.itemTemplateId(), reward.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (reward.gold() > 0) {
            GoldLedger.credit(connection, characterId, reward.gold(), REASON);
        }
        JdbcOperations.update(connection, FINISH, characterId, spot.id());
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "TREASURE_DIG",
                "{\"spotId\":" + spot.id() + ",\"reward\":\"" + reward.resultKey() + "\",\"gold\":" + reward.gold()
                        + ",\"itemTemplateId\":" + reward.itemTemplateId() + ",\"quantity\":" + reward.quantity()
                        + ",\"guardian\":" + reward.spawnsGuardian() + "}",
                Timestamp.from(dig.now()));
        return new DigResult(
                Outcome.OK,
                Optional.of(spot),
                reward.resultKey(),
                reward.gold(),
                reward.itemTemplateId(),
                reward.quantity(),
                reward.spawnsGuardian());
    }

    private static Spot spot(ResultSet rs) throws SQLException {
        return new Spot(rs.getLong(1), rs.getString(2), rs.getFloat(3), rs.getFloat(4), rs.getString(5));
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
