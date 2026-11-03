package dev.kage.server.database.inventory;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import dev.kage.inventory.domain.LuckyBagPort;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link LuckyBagPort} (ADR-163, V131). Mở túi: khoá túi đồ → đọc bảng quà theo thứ tự → trừ một túi (đường
 * "dùng": túi đã khoá vẫn mở được — ADR-159 D3) → rút → cấp quà → audit; túi đầy hay vàng chạm trần thì rollback, túi
 * còn nguyên.
 */
public final class PostgresLuckyBagAdapter implements LuckyBagPort {

    private static final String GOLD_REASON = "gold.reason.lucky_bag";

    private static final String ALL_REWARDS = """
            SELECT r.bag_item_template_id, r.reward_gold, COALESCE(r.reward_item_template_id, 0), r.reward_quantity,
                   r.weight,
                   COALESCE((SELECT SUM(ii.quantity) FROM inventory_item ii
                              WHERE ii.character_id = ? AND ii.item_template_id = r.bag_item_template_id), 0)
              FROM lucky_bag_reward r
             ORDER BY r.bag_item_template_id, r.sort_order, r.id
            """;

    private static final String REWARDS_OF = """
            SELECT reward_gold, COALESCE(reward_item_template_id, 0), reward_quantity, weight
              FROM lucky_bag_reward WHERE bag_item_template_id = ? ORDER BY sort_order, id
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một lần mở. */
    private record Request(long characterId, long bagItemTemplateId, ToIntFunction<int[]> pick) {}

    private final DataSource dataSource;

    public PostgresLuckyBagAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Bag> bags(long characterId) {
        Map<Long, List<Reward>> rewards = new LinkedHashMap<>();
        Map<Long, Integer> owned = new LinkedHashMap<>();
        JdbcOperations.query(
                        dataSource,
                        ALL_REWARDS,
                        rs -> {
                            long bag = rs.getLong(1);
                            owned.put(bag, (int) Math.min(Integer.MAX_VALUE, rs.getLong(6)));
                            return Map.entry(bag, new Reward(rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5)));
                        },
                        characterId)
                .forEach(row -> rewards.computeIfAbsent(row.getKey(), k -> new ArrayList<>())
                        .add(row.getValue()));
        List<Bag> bags = new ArrayList<>();
        rewards.forEach((bag, list) -> bags.add(new Bag(bag, owned.getOrDefault(bag, 0), list)));
        return bags;
    }

    @Override
    public Opened open(long characterId, long bagItemTemplateId, ToIntFunction<int[]> pick) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> openIn(connection, new Request(characterId, bagItemTemplateId, pick)));
        } catch (Abort abort) {
            return Opened.refused(abort.outcome);
        } catch (GoldCapExceededException capped) {
            return Opened.refused(Outcome.GOLD_FULL);
        }
    }

    private static Opened openIn(Connection connection, Request request) {
        long characterId = request.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        List<Reward> rewards = JdbcOperations.query(
                connection,
                REWARDS_OF,
                rs -> new Reward(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4)),
                request.bagItemTemplateId());
        if (rewards.isEmpty()) {
            throw new Abort(Outcome.NOT_A_BAG);
        }
        if (!TemplateQuantityConsumer.consume(connection, characterId, request.bagItemTemplateId(), 1)) {
            throw new Abort(Outcome.NO_BAG);
        }
        int[] weights = rewards.stream().mapToInt(Reward::weight).toArray();
        Reward reward = rewards.get(request.pick().applyAsInt(weights));
        grant(connection, characterId, reward);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "LUCKY_BAG_OPENED",
                "{\"bag\":" + request.bagItemTemplateId() + ",\"gold\":" + reward.gold() + ",\"item\":"
                        + reward.itemTemplateId() + ",\"quantity\":" + reward.quantity() + "}",
                Timestamp.from(Instant.now()));
        return new Opened(Outcome.OK, reward);
    }

    private static void grant(Connection connection, long characterId, Reward reward) {
        if (reward.itemTemplateId() == 0L) {
            GoldLedger.credit(connection, characterId, reward.gold(), GOLD_REASON);
            return;
        }
        if (!InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, reward.itemTemplateId(), reward.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, reward.itemTemplateId(), reward.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
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
