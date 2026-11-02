package dev.kage.server.database.checkin;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import dev.kage.checkin.domain.LuckyWheelPort;
import dev.kage.checkin.domain.LuckyWheelRules;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link LuckyWheelPort} (ADR-151, V122). Quay là MỘT giao dịch mở đầu bằng khoá túi của nhân vật
 * (thứ tự khoá chung, DATABASE.md §5.1): hai lượt quay song song không thể cùng dùng một lượt miễn phí hay vượt trần
 * lượt mua.
 */
public final class PostgresLuckyWheelAdapter implements LuckyWheelPort {

    private static final String REASON = "gold.reason.lucky_wheel";

    private static final String PRIZES = """
            SELECT id, weight, label_key, reward_gold, COALESCE(reward_item_template_id, 0), reward_quantity
              FROM wheel_prize
             ORDER BY id
            """;

    private static final String USED = """
            SELECT COUNT(*) FILTER (WHERE NOT paid), COUNT(*) FILTER (WHERE paid)
              FROM character_wheel_spin
             WHERE character_id = ? AND day = ?
            """;

    private static final String RECORD = """
            INSERT INTO character_wheel_spin (character_id, day, paid, prize_id, spun_at) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một ô kèm id + trọng số. */
    private record Row(long id, int weight, Prize prize) {}

    /** Một lệnh quay. */
    private record Request(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {}

    private final DataSource dataSource;

    public PostgresLuckyWheelAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Wheel wheel(long characterId, LocalDate day) {
        return JdbcTransactionRunner.call(
                dataSource,
                connection -> new Wheel(
                        rows(connection).stream().map(Row::prize).toList(), spinsLeft(connection, characterId, day)));
    }

    @Override
    public Spin spin(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> spinIn(connection, new Request(characterId, day, now, pick)));
        } catch (Abort abort) {
            return new Spin(
                    abort.outcome,
                    Optional.empty(),
                    false,
                    wheel(characterId, day).spins());
        } catch (GoldCapExceededException capped) {
            return new Spin(
                    Outcome.GOLD_CAP,
                    Optional.empty(),
                    false,
                    wheel(characterId, day).spins());
        }
    }

    private static Spin spinIn(Connection connection, Request request) {
        long characterId = request.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        Spins before = spinsLeft(connection, characterId, request.day());
        boolean paid = takeSpin(connection, characterId, before);
        List<Row> rows = rows(connection);
        if (rows.isEmpty()) {
            throw new Abort(Outcome.NO_SPINS_LEFT);
        }
        int index =
                request.pick().applyAsInt(rows.stream().mapToInt(Row::weight).toArray());
        Row chosen = rows.get(index);
        Prize prize = chosen.prize();
        grant(connection, characterId, prize);
        Timestamp at = Timestamp.from(request.now());
        JdbcOperations.update(connection, RECORD, characterId, Date.valueOf(request.day()), paid, chosen.id(), at);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "LUCKY_WHEEL_SPIN",
                "{\"paid\":" + paid + ",\"prize\":\"" + prize.labelKey() + "\",\"gold\":" + prize.gold()
                        + ",\"itemTemplateId\":" + prize.itemTemplateId() + ",\"quantity\":" + prize.quantity() + "}",
                at);
        Spins after = paid
                ? new Spins(before.freeLeft(), before.paidLeft() - 1)
                : new Spins(before.freeLeft() - 1, before.paidLeft());
        return new Spin(Outcome.OK, Optional.of(prize), paid, after);
    }

    /** Lượt miễn phí nếu còn, không thì lượt mua (trừ giá); {@code true} = lượt mua. Hết lượt / thiếu tiền ⇒ Abort. */
    private static boolean takeSpin(Connection connection, long characterId, Spins before) {
        if (before.freeLeft() > 0) {
            return false;
        }
        if (before.paidLeft() == 0) {
            throw new Abort(Outcome.NO_SPINS_LEFT);
        }
        if (!GoldLedger.deduct(connection, characterId, LuckyWheelRules.SPIN_COST, REASON)) {
            throw new Abort(Outcome.INSUFFICIENT_GOLD);
        }
        return true;
    }

    /** Trao ô trúng — túi đầy ⇒ Abort; vượt trần vàng ⇒ {@code GoldCapExceededException} (rollback trọn). */
    private static void grant(Connection connection, long characterId, Prize prize) {
        if (prize.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, prize.itemTemplateId(), prize.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, prize.itemTemplateId(), prize.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (prize.gold() > 0) {
            GoldLedger.credit(connection, characterId, prize.gold(), REASON);
        }
    }

    /** Các ô theo id; {@code Prize.index} = vị trí trong danh sách này (client hiển thị theo đúng thứ tự). */
    private static List<Row> rows(Connection connection) {
        List<Row> read = JdbcOperations.query(
                connection,
                PRIZES,
                rs -> new Row(
                        rs.getLong(1),
                        rs.getInt(2),
                        new Prize(0, rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getInt(6))));
        List<Row> rows = new ArrayList<>(read.size());
        for (int i = 0; i < read.size(); i++) {
            Row row = read.get(i);
            Prize p = row.prize();
            rows.add(new Row(
                    row.id(), row.weight(), new Prize(i, p.labelKey(), p.gold(), p.itemTemplateId(), p.quantity())));
        }
        return rows;
    }

    private static Spins spinsLeft(Connection connection, long characterId, LocalDate day) {
        long[] used = JdbcOperations.queryOne(
                        connection,
                        USED,
                        rs -> new long[] {rs.getLong(1), rs.getLong(2)},
                        characterId,
                        Date.valueOf(day))
                .orElse(new long[] {0L, 0L});
        return new Spins((int) Math.max(0L, LuckyWheelRules.FREE_SPINS_PER_DAY - used[0]), (int)
                Math.max(0L, LuckyWheelRules.PAID_SPINS_PER_DAY - used[1]));
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
