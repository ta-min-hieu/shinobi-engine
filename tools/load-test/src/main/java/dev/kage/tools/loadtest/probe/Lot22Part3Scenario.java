package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.BidGuildAuctionCommand;
import dev.kage.protocol.BidGuildAuctionResponse;
import dev.kage.protocol.EnterTowerCommand;
import dev.kage.protocol.EnterTowerResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.EscortStateEvent;
import dev.kage.protocol.GetTowerStatusRequest;
import dev.kage.protocol.GetTowerStatusResponse;
import dev.kage.protocol.ListGuildAuctionsRequest;
import dev.kage.protocol.ListGuildAuctionsResponse;
import dev.kage.protocol.ListRefinementsRequest;
import dev.kage.protocol.ListRefinementsResponse;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RefineItemCommand;
import dev.kage.protocol.RefineItemResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TowerFloorClearedEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Các bước lô 22 phần 3 (đi qua {@code --lot21=}): {@code refines}, {@code refine:<itemId>:<dòng giữ, -1 = không>},
 * {@code auctions}, {@code bid:<lotId>:<số tiền>}, {@code tower}, {@code enterTower}, {@code towerFight:<giây>} (đánh
 * quái tầng tới khi server báo qua tầng), {@code escortWatch:<giây>} (in trạng thái đoàn hộ tống cuối cùng thấy).
 */
final class Lot22Part3Scenario {

    private static final int MAX_FRAMES_TO_SKIP = 2000;
    private static final long ATTACK_SPACING_MILLIS = 1_100L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MOVE_SPACING_MILLIS = 100L;
    /** Dưới tầm đánh thường 2 m của server, chừa sai số vị trí. */
    private static final float IN_RANGE_METERS = 1.5f;
    /** Điểm xuất hiện trong World tháp (cùng hầm ngục). */
    private static final float TOWER_ENTRY_X = 0f;

    private final GameConnection connection;

    Lot22Part3Scenario(GameConnection connection) {
        this.connection = connection;
    }

    /** Một bước; {@code "bước không hiểu"} nếu không thuộc phần này. */
    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "refines" ->
                ask(
                        Opcode.OPCODE_LIST_REFINEMENTS_REQUEST_VALUE,
                        ListRefinementsRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_REFINEMENTS_RESPONSE_VALUE,
                        p -> describe(ListRefinementsResponse.parseFrom(p)));
            case "refine" -> refine(arg);
            case "auctions" ->
                ask(
                        Opcode.OPCODE_LIST_GUILD_AUCTIONS_REQUEST_VALUE,
                        ListGuildAuctionsRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_GUILD_AUCTIONS_RESPONSE_VALUE,
                        p -> describe(ListGuildAuctionsResponse.parseFrom(p)));
            case "bid" -> bid(arg);
            case "tower" ->
                ask(
                        Opcode.OPCODE_GET_TOWER_STATUS_REQUEST_VALUE,
                        GetTowerStatusRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_TOWER_STATUS_RESPONSE_VALUE,
                        p -> describe(GetTowerStatusResponse.parseFrom(p)));
            case "enterTower" ->
                ask(
                        Opcode.OPCODE_ENTER_TOWER_COMMAND_VALUE,
                        EnterTowerCommand.getDefaultInstance(),
                        Opcode.OPCODE_ENTER_TOWER_RESPONSE_VALUE,
                        p -> {
                            EnterTowerResponse r = EnterTowerResponse.parseFrom(p);
                            return r.getOutcome() + " tầng=" + r.getFloor();
                        });
            case "towerFight" -> towerFight(Integer.parseInt(arg));
            case "escortWatch" -> escortWatch(Integer.parseInt(arg));
            default -> "bước không hiểu";
        };
    }

    private String refine(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        return ask(
                Opcode.OPCODE_REFINE_ITEM_COMMAND_VALUE,
                RefineItemCommand.newBuilder()
                        .setItemId(Long.parseLong(parts[0]))
                        .setLockedLine(parts.length == 2 ? Integer.parseInt(parts[1]) : -1)
                        .build(),
                Opcode.OPCODE_REFINE_ITEM_RESPONSE_VALUE,
                p -> {
                    RefineItemResponse r = RefineItemResponse.parseFrom(p);
                    return r.getOutcome() + " máuTốiĐa=" + r.getMaxHp() + " " + describe(r.getStatus());
                });
    }

    private String bid(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        return ask(
                Opcode.OPCODE_BID_GUILD_AUCTION_COMMAND_VALUE,
                BidGuildAuctionCommand.newBuilder()
                        .setLotId(Long.parseLong(parts[0]))
                        .setAmount(Long.parseLong(parts[1]))
                        .build(),
                Opcode.OPCODE_BID_GUILD_AUCTION_RESPONSE_VALUE,
                p -> {
                    BidGuildAuctionResponse r = BidGuildAuctionResponse.parseFrom(p);
                    return r.getOutcome() + " " + describe(r.getStatus());
                });
    }

    /** Nhắm từng con quái vừa thấy (MonsterSpawnedEvent) và đánh cách nhau hơn hồi chiêu, tới khi có 1507. */
    private String towerFight(int seconds) throws IOException {
        TowerFight fight = new TowerFight();
        long deadline = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND;
        while (System.currentTimeMillis() < deadline) {
            if (fight.act()) {
                continue; // đang đi: act() đã tự đọc khung tới MoveResponse — đừng chặn ở readFrame dài
            }
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            fight.onFrame(frame);
            if (!fight.result.isEmpty()) {
                return fight.result;
            }
        }
        return "chưa qua tầng sau " + seconds + "s, hạ=" + fight.kills + " cònThấy=" + fight.alive.size();
    }

    /** Trạng thái một trận tầng tháp: quái đang thấy, mục tiêu, nhịp đánh. */
    private final class TowerFight {
        private final Set<Long> alive = new LinkedHashSet<>();
        private final Map<Long, Float> monsterX = new HashMap<>();
        private float playerX = TOWER_ENTRY_X;
        private long nextAttack;
        private long target;
        private int kills;
        /** Rỗng = chưa xong; khác rỗng = dòng kết quả (đã qua tầng). */
        private String result = "";

        /** Chọn mục tiêu nếu chưa có; đi tới trong tầm đánh; tới nhịp thì đánh. {@code true} = đang đi. */
        boolean act() throws IOException {
            if (target == 0L && !alive.isEmpty()) {
                target = alive.iterator().next();
                connection.send(
                        Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                        SetTargetCommand.newBuilder()
                                .setTargetEntityId(target)
                                .build()
                                .toByteArray());
            }
            if (target == 0L || !result.isEmpty()) {
                return false;
            }
            if (walkTowardTarget()) {
                return true;
            }
            if (System.currentTimeMillis() >= nextAttack) {
                connection.send(
                        Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                        AttackCommand.getDefaultInstance().toByteArray());
                nextAttack = System.currentTimeMillis() + ATTACK_SPACING_MILLIS;
            }
            return false;
        }

        /** {@code true} nếu còn đang đi (chưa vào tầm đánh) — gửi lệnh đi mỗi nhịp để nhận {@code MoveResponse}. */
        private boolean walkTowardTarget() throws IOException {
            float gap = monsterX.getOrDefault(target, playerX) - playerX;
            boolean far = Math.abs(gap) > IN_RANGE_METERS;
            pause();
            connection.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder()
                            .setDirX(far ? Math.signum(gap) : 0f)
                            .build()
                            .toByteArray());
            awaitMoveResponse();
            return far;
        }

        /** Nhịp gửi lệnh đi — đủ thưa để không dội server, đủ dày để không đi lố mục tiêu. */
        private void pause() {
            try {
                Thread.sleep(MOVE_SPACING_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** Đọc tới đúng {@code MoveResponse} của lệnh vừa gửi — mọi khung khác vẫn đi qua {@link #onFrame}. */
        private void awaitMoveResponse() throws IOException {
            for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
                PacketCodec.Frame frame = connection.readFrame();
                onFrame(frame);
                if (frame.header().opcode() == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
                    return;
                }
            }
        }

        void onFrame(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
                MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
                alive.add(spawned.getMonsterId());
                monsterX.put(spawned.getMonsterId(), spawned.getX());
            } else if (opcode == Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE) {
                MonsterMovedEvent moved = MonsterMovedEvent.parseFrom(frame.payload());
                monsterX.put(moved.getMonsterId(), moved.getX());
            } else if (opcode == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
                playerX = MoveResponse.parseFrom(frame.payload()).getX();
            } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
                onHit(AttackResultEvent.parseFrom(frame.payload()));
            } else if (opcode == Opcode.OPCODE_TOWER_FLOOR_CLEARED_EVENT_VALUE) {
                TowerFloorClearedEvent cleared = TowerFloorClearedEvent.parseFrom(frame.payload());
                result = "QUA tầng=" + cleared.getFloor() + " caoNhất=" + cleared.getBestFloor() + " thưởng="
                        + cleared.getRewarded() + " hạ=" + kills;
            }
        }

        private void onHit(AttackResultEvent hit) {
            if (hit.getTargetDied() && alive.remove(hit.getTargetEntityId())) {
                kills++;
                if (hit.getTargetEntityId() == target) {
                    target = 0L;
                }
            }
        }
    }

    /** Đọc khung trong {@code seconds} giây, in trạng thái đoàn cuối cùng (và dừng sớm nếu đoàn tới nơi/thất bại). */
    private String escortWatch(int seconds) throws IOException {
        long deadline = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND;
        String last = "không thấy gói 625";
        int events = 0;
        while (System.currentTimeMillis() < deadline) {
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            if (frame.header().opcode() == Opcode.OPCODE_ESCORT_STATE_EVENT_VALUE) {
                EscortStateEvent e = EscortStateEvent.parseFrom(frame.payload());
                events++;
                last = e.getState() + " x=" + Math.round(e.getX()) + " máu=" + e.getHp() + "/" + e.getMaxHp();
                if (!"MOVING".equals(e.getState()) && !"WAITING".equals(e.getState())) {
                    break;
                }
            }
        }
        return "gói=" + events + " cuối: " + last;
    }

    private static String describe(ListRefinementsResponse r) {
        StringBuilder line = new StringBuilder("đá=" + r.getStones());
        r.getItemsList().forEach(item -> {
            line.append(" | ").append(item.getItemId()).append(' ').append(item.getSlot());
            item.getLinesList()
                    .forEach(l ->
                            line.append(' ').append(l.getStat()).append('+').append(l.getValue()));
        });
        return line.toString();
    }

    private static String describe(ListGuildAuctionsResponse r) {
        StringBuilder line = new StringBuilder("lô=" + r.getLotsCount());
        r.getLotsList().forEach(l -> line.append(" | ")
                .append(l.getLotId())
                .append(" vật=")
                .append(l.getItemTemplateId())
                .append('x')
                .append(l.getQuantity())
                .append(" giá=")
                .append(l.getCurrentBid())
                .append(" dẫn=")
                .append(l.getLeaderName())
                .append(" tốiThiểu=")
                .append(l.getMinimumBid())
                .append(" người=")
                .append(l.getParticipants())
                .append(" còn=")
                .append(l.getSecondsLeft())
                .append('s'));
        return line.toString();
    }

    private static String describe(GetTowerStatusResponse r) {
        StringBuilder line = new StringBuilder("caoNhất=" + r.getBestFloor() + "/" + r.getTopFloor() + " kế="
                + r.getNextFloor() + " " + r.getNextMonsterCode() + "x" + r.getNextMonsterCount());
        r.getTopList().forEach(c -> line.append(" | ")
                .append(c.getCharacterName())
                .append(' ')
                .append(c.getBestFloor()));
        return line.toString();
    }

    @FunctionalInterface
    private interface Reply {
        String describe(byte[] payload) throws InvalidProtocolBufferException;
    }

    private String ask(int opcode, MessageLite command, int responseOpcode, Reply reply) throws IOException {
        int sequenceId = connection.send(opcode, command.toByteArray());
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int got = frame.header().opcode();
            if (got == responseOpcode) {
                return reply.describe(frame.payload());
            }
            if (got == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                if (error.getRelatedSequenceId() == sequenceId) {
                    return "LỖI " + error.getErrorCode() + " " + error.getErrorKey();
                }
            }
        }
        throw new IOException("Không thấy opcode " + responseOpcode + " sau " + MAX_FRAMES_TO_SKIP + " frame");
    }
}
