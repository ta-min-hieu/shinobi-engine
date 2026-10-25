package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildAuctionPort;
import dev.kage.guild.domain.GuildAuctionRules;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GuildAuctionPort} (ADR-112, V92).
 *
 * <p>Trả giá chỉ khoá dòng {@code guild_auction_lot} (không khoá {@code guild}) — giải tán bang khoá {@code guild} rồi
 * SET NULL sang lô, cùng chiều, nên không deadlock. Chốt dùng {@code FOR UPDATE SKIP LOCKED} + điều kiện
 * {@code settled_at IS NULL}: hai luồng chốt song song không bao giờ chốt đôi một lô.
 */
public final class PostgresGuildAuctionAdapter implements GuildAuctionPort {

    private static final String GOLD_REASON = "gold.reason.guild_auction";

    private static final String DROPS =
            "SELECT item_template_id, quantity, weight FROM guild_auction_drop ORDER BY item_template_id";

    private static final String INSERT_LOT = """
            INSERT INTO guild_auction_lot (guild_id, item_template_id, quantity, killer_id, opened_at, ends_at)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """;

    private static final String INSERT_PARTICIPANT = """
            INSERT INTO guild_auction_participant (lot_id, character_id) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String LOT_VIEW = """
            SELECT l.id, l.item_template_id, l.quantity, l.current_bid, COALESCE(c.name, ''), l.leader_id = ?,
                   l.ends_at, (SELECT COUNT(*) FROM guild_auction_participant p WHERE p.lot_id = l.id)
              FROM guild_auction_lot l LEFT JOIN character c ON c.id = l.leader_id
            """;

    private static final String OPEN_LOTS_OF = LOT_VIEW + """
             WHERE l.settled_at IS NULL AND l.ends_at > ?
               AND l.guild_id = (SELECT guild_id FROM guild_member WHERE character_id = ?)
             ORDER BY l.ends_at, l.id
            """;

    private static final String LOT_BY_ID = LOT_VIEW + " WHERE l.id = ?";

    private static final String LOCK_LOT_FOR_BID = """
            SELECT l.current_bid, l.leader_id, l.ends_at, l.settled_at IS NULL
              FROM guild_auction_lot l
             WHERE l.id = ? AND l.guild_id = (SELECT guild_id FROM guild_member WHERE character_id = ?)
               FOR UPDATE OF l
            """;

    private static final String UPDATE_BID = "UPDATE guild_auction_lot SET current_bid = ?, leader_id = ? WHERE id = ?";

    private static final String INSERT_PAYOUT = """
            INSERT INTO guild_auction_payout (lot_id, character_id, kind, gold, item_template_id, quantity)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String DUE = """
            SELECT id, item_template_id, quantity, killer_id, current_bid, leader_id
              FROM guild_auction_lot
             WHERE settled_at IS NULL AND ends_at <= ?
             ORDER BY id
               FOR UPDATE SKIP LOCKED
            """;

    private static final String PARTICIPANTS = "SELECT character_id FROM guild_auction_participant WHERE lot_id = ?";
    private static final String MARK_SETTLED = "UPDATE guild_auction_lot SET settled_at = ? WHERE id = ?";

    private static final String PENDING = """
            SELECT id, character_id, kind, gold, COALESCE(item_template_id, 0), quantity
              FROM guild_auction_payout ORDER BY id
            """;

    private static final String MARK_PAID = "DELETE FROM guild_auction_payout WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một dòng bảng rơi. */
    private record Drop(long itemTemplateId, int quantity, int weight) {}

    /** Dòng lô vừa khoá để trả giá. */
    private record BidState(long currentBid, long leaderId, Instant endsAt, boolean open) {}

    /** Dòng lô hết giờ vừa khoá để chốt. */
    private record DueLot(long id, long itemTemplateId, int quantity, long killerId, long bid, long leaderId) {}

    private final DataSource dataSource;
    private final RandomGenerator random;

    public PostgresGuildAuctionAdapter(DataSource dataSource, RandomGenerator random) {
        this.dataSource = dataSource;
        this.random = random;
    }

    @Override
    public Optional<Lot> createLot(NewLot lot) {
        List<Drop> drops =
                JdbcOperations.query(dataSource, DROPS, rs -> new Drop(rs.getLong(1), rs.getInt(2), rs.getInt(3)));
        int total = drops.stream().mapToInt(Drop::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        Drop picked = drops.getLast();
        for (Drop drop : drops) {
            roll -= drop.weight();
            if (roll < 0) {
                picked = drop;
                break;
            }
        }
        Drop chosen = picked;
        long lotId = JdbcTransactionRunner.call(dataSource, connection -> {
            long id = JdbcOperations.queryOne(
                            connection,
                            INSERT_LOT,
                            rs -> rs.getLong(1),
                            lot.guildId(),
                            chosen.itemTemplateId(),
                            chosen.quantity(),
                            lot.killerId(),
                            Timestamp.from(lot.at()),
                            Timestamp.from(lot.at().plus(GuildAuctionRules.DURATION)))
                    .orElseThrow();
            for (long participant : lot.participantIds()) {
                JdbcOperations.update(connection, INSERT_PARTICIPANT, id, participant);
            }
            return id;
        });
        return JdbcOperations.queryOne(dataSource, LOT_BY_ID, PostgresGuildAuctionAdapter::mapLot, 0L, lotId);
    }

    @Override
    public List<Lot> openLotsOf(long characterId, Instant now) {
        return JdbcOperations.query(
                dataSource,
                OPEN_LOTS_OF,
                PostgresGuildAuctionAdapter::mapLot,
                characterId,
                Timestamp.from(now),
                characterId);
    }

    @Override
    public BidOutcome bid(long characterId, long lotId, long amount, Instant now) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> bidIn(connection, new Bid(characterId, lotId, amount, now)));
    }

    /** Một lần trả giá — gom tham số (trần {@code ParameterNumber}). */
    private record Bid(long characterId, long lotId, long amount, Instant now) {}

    private static BidOutcome bidIn(Connection connection, Bid bid) {
        long characterId = bid.characterId();
        long lotId = bid.lotId();
        long amount = bid.amount();
        Instant now = bid.now();
        Optional<BidState> found = JdbcOperations.queryOne(
                connection,
                LOCK_LOT_FOR_BID,
                rs -> new BidState(
                        rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant(), rs.getBoolean(4)),
                lotId,
                characterId);
        if (found.isEmpty()) {
            return BidOutcome.NOT_FOUND;
        }
        BidState state = found.get();
        if (!state.open() || !now.isBefore(state.endsAt())) {
            return BidOutcome.CLOSED;
        }
        if (state.leaderId() == characterId) {
            return BidOutcome.ALREADY_LEADING;
        }
        if (amount < GuildAuctionRules.minimumBid(state.currentBid())) {
            return BidOutcome.TOO_LOW;
        }
        if (!GoldLedger.deduct(connection, characterId, amount, GOLD_REASON)) {
            return BidOutcome.INSUFFICIENT_GOLD;
        }
        if (state.leaderId() != 0L) {
            JdbcOperations.update(
                    connection,
                    INSERT_PAYOUT,
                    lotId,
                    state.leaderId(),
                    PayoutKind.REFUND.name(),
                    state.currentBid(),
                    null,
                    0);
        }
        JdbcOperations.update(connection, UPDATE_BID, amount, characterId, lotId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GUILD_AUCTION_BID",
                "{\"lotId\":" + lotId + ",\"amount\":" + amount + ",\"outbid\":" + state.leaderId() + "}",
                Timestamp.from(now));
        return BidOutcome.OK;
    }

    @Override
    public int settleDue(Instant now) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            List<DueLot> due = JdbcOperations.query(
                    connection,
                    DUE,
                    rs -> new DueLot(
                            rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)),
                    Timestamp.from(now));
            for (DueLot lot : due) {
                settleIn(connection, lot, now);
            }
            return due.size();
        });
    }

    private static void settleIn(Connection connection, DueLot lot, Instant now) {
        boolean sold = lot.leaderId() != 0L && lot.bid() > 0L;
        long itemRecipient = sold ? lot.leaderId() : lot.killerId();
        JdbcOperations.update(
                connection,
                INSERT_PAYOUT,
                lot.id(),
                itemRecipient,
                PayoutKind.ITEM.name(),
                0L,
                lot.itemTemplateId(),
                lot.quantity());
        if (sold) {
            List<Long> participants = JdbcOperations.query(connection, PARTICIPANTS, rs -> rs.getLong(1), lot.id());
            long share = GuildAuctionRules.shareOf(lot.bid(), participants.size());
            if (share > 0L) {
                for (long participant : participants) {
                    JdbcOperations.update(
                            connection, INSERT_PAYOUT, lot.id(), participant, PayoutKind.SHARE.name(), share, null, 0);
                }
            }
        }
        JdbcOperations.update(connection, MARK_SETTLED, Timestamp.from(now), lot.id());
        JdbcOperations.update(
                connection,
                AUDIT,
                itemRecipient,
                "GUILD_AUCTION_SETTLED",
                "{\"lotId\":" + lot.id() + ",\"bid\":" + lot.bid() + ",\"sold\":" + sold + "}",
                Timestamp.from(now));
    }

    @Override
    public List<Payout> pendingPayouts() {
        return JdbcOperations.query(
                dataSource,
                PENDING,
                rs -> new Payout(
                        rs.getLong(1),
                        rs.getLong(2),
                        PayoutKind.valueOf(rs.getString(3)),
                        rs.getLong(4),
                        rs.getLong(5),
                        rs.getInt(6)));
    }

    @Override
    public void markPaid(long payoutId) {
        JdbcOperations.update(dataSource, MARK_PAID, payoutId);
    }

    private static Lot mapLot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Lot(
                rs.getLong(1),
                rs.getLong(2),
                rs.getInt(3),
                rs.getLong(4),
                rs.getString(5),
                rs.getBoolean(6),
                rs.getTimestamp(7).toInstant(),
                rs.getInt(8));
    }
}
