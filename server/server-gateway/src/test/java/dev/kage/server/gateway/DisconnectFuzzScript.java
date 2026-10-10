package dev.kage.server.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.ExitDuelCommand;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.ListPendingPartyInvitesRequest;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TradeOfferItemInput;

/**
 * Chuỗi bước ngẫu nhiên có seed của {@code DisconnectAnywhereFuzzDatabaseTest}: mỗi bước là MỘT
 * lệnh thật trên dây của một nhân vật — nhóm, hầm ngục, thách đấu, giao dịch, di chuyển — xen với
 * các kiểu ngắt kết nối (đóng giữa bắt tay, đóng ngay sau lệnh chọn nhân vật, đóng giữa việc đang dở,
 * đăng nhập đè từ kết nối khác). Không bước nào chờ câu trả lời trừ lần đăng nhập trọn vẹn: lệnh kế
 * tiếp — kể cả lệnh đóng — phải chạy đua với lệnh trước còn đang bay.
 *
 * <p>Seed cố định CHUỖI bước; thứ tự xen kẽ giữa các luồng của server thì không tái lập được.
 */
final class DisconnectFuzzScript {

    /** Trần cho một lần đăng nhập trọn vẹn. Dưới trần chờ phiên cũ (5 giây) của gateway: chạm trần đó là kẹt thật. */
    static final Duration LOGIN_BUDGET = Duration.ofSeconds(4);

    private static final String DUNGEON_CODE = "sample_dungeon";
    private static final int MAX_PAUSE_MILLIS = 4;
    /** Đủ cho một lệnh chạm DB đi hết vòng trên máy bình thường; không đủ thì bước ghép chỉ bị từ chối. */
    private static final int PAIR_STEP_MILLIS = 15;

    private static final int MAX_GOLD_OFFER = 200;
    private static final int MAX_ITEM_OFFER = 3;

    /** Một nhân vật của bài fuzz và kết nối hiện tại của nó (rỗng = chưa từng mở). */
    static final class Actor {
        final String username;
        final String characterName;
        final long characterId;
        final long itemId;
        final DisconnectFuzzClient.Hints hints = new DisconnectFuzzClient.Hints();
        private DisconnectFuzzClient client;

        Actor(String username, String characterName, long characterId, long itemId) {
            this.username = username;
            this.characterName = characterName;
            this.characterId = characterId;
            this.itemId = itemId;
        }

        boolean isOnline() {
            return client != null && client.isOpen();
        }

        DisconnectFuzzClient client() {
            return client;
        }
    }

    @FunctionalInterface
    private interface StepBody {
        void run(Actor actor) throws InterruptedException;
    }

    private record Step(String name, int weight, StepBody body) {}

    private final DisconnectFuzzServer server;
    private final List<Actor> actors;
    private final Random random;
    private final List<Step> onlineSteps;
    private final List<Step> loginSteps;
    private final List<String> trace = new ArrayList<>();

    DisconnectFuzzScript(DisconnectFuzzServer server, List<Actor> actors, long seed) {
        this.server = server;
        this.actors = actors;
        this.random = new Random(seed);
        this.onlineSteps = onlineSteps();
        this.loginSteps = loginSteps();
    }

    /** Các bước đã chạy, theo thứ tự — in kèm khi một bất biến đỏ. */
    List<String> trace() {
        return trace;
    }

    void runSteps(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            Actor actor = actors.get(random.nextInt(actors.size()));
            Step step = pick(actor.isOnline() ? onlineSteps : loginSteps);
            trace.add(actor.characterName + " " + step.name());
            step.body().run(actor);
            if (random.nextBoolean()) {
                Thread.sleep(random.nextInt(MAX_PAUSE_MILLIS));
            }
        }
    }

    /** Đóng mọi kết nối còn mở, không chờ — phần chờ và phép đo là việc của test. */
    void disconnectEveryone() {
        for (Actor actor : actors) {
            if (actor.isOnline()) {
                actor.client().closeWithoutWaiting();
            }
        }
    }

    /**
     * Đăng nhập trọn vẹn: mở kết nối, đăng nhập, chọn nhân vật, CHỜ vào world.
     *
     * <p>"Vào world" là gói số vàng lúc vào ({@code GoldChangedEvent}), không phải
     * {@code SelectCharacterResponse}: gateway trả response TRƯỚC khi gắn nhân vật vào kết nối, nên
     * lệnh gửi ngay sau response còn có thể nhận {@code error.not_authenticated}. Gói số vàng chỉ đi
     * sau khi đã gắn xong.
     *
     * @return {@code false} nếu server không trả lời trong {@link #LOGIN_BUDGET}
     */
    boolean logInFully(Actor actor) throws InterruptedException {
        DisconnectFuzzClient client = openAndConnect(actor);
        if (client.await(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, LOGIN_BUDGET).isEmpty()) {
            return false;
        }
        sendSelect(client, actor);
        return client.await(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, LOGIN_BUDGET)
                        .isPresent()
                && client.await(Opcode.OPCODE_GOLD_CHANGED_EVENT_VALUE, LOGIN_BUDGET)
                        .isPresent();
    }

    private Step pick(List<Step> steps) {
        int total = 0;
        for (Step step : steps) {
            total += step.weight();
        }
        int roll = random.nextInt(total);
        for (Step step : steps) {
            roll -= step.weight();
            if (roll < 0) {
                return step;
            }
        }
        throw new IllegalStateException("bảng bước rỗng");
    }

    private List<Step> loginSteps() {
        return List.of(
                new Step("login", 12, this::logInOrFail),
                new Step("close-during-handshake", 1, actor -> server.open(actor.hints)
                        .closeWithoutWaiting()),
                new Step("close-after-connect-request", 1, actor -> openAndConnect(actor)
                        .closeWithoutWaiting()),
                new Step("close-after-connect-response", 1, actor -> connected(actor)
                        .closeWithoutWaiting()),
                new Step("close-right-after-select", 3, actor -> {
                    DisconnectFuzzClient client = connected(actor);
                    sendSelect(client, actor);
                    client.closeWithoutWaiting();
                }),
                new Step("close-during-select", 3, actor -> {
                    DisconnectFuzzClient client = connected(actor);
                    sendSelect(client, actor);
                    Thread.sleep(random.nextInt(MAX_PAUSE_MILLIS));
                    client.closeWithoutWaiting();
                }),
                new Step("stay-offline", 3, actor -> {}));
    }

    private List<Step> onlineSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("disconnect", 4, actor -> actor.client().closeWithoutWaiting()));
        steps.add(new Step("takeover-from-new-connection", 2, this::logInOrFail));
        steps.add(new Step("select-again", 1, actor -> sendSelect(actor.client(), actor)));
        steps.add(new Step("move", 8, actor -> send(actor, Opcode.OPCODE_MOVE_COMMAND_VALUE, move())));
        steps.add(
                new Step("area-chat", 3, actor -> send(actor, Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, chat())));
        steps.add(new Step("attack-opponent", 3, this::attackOpponent));
        addPartySteps(steps);
        addDuelSteps(steps);
        addTradeSteps(steps);
        // Bước GHÉP: đưa hai người vào giữa một việc chung (nhóm, trận đấu, phiên giao dịch) để các lần
        // ngắt kết nối sau đó rơi vào đúng lúc việc ấy đang dở — bước đơn lẻ hiếm khi tự nối được thành chuỗi.
        steps.add(new Step("pair-party-and-dungeon", 5, this::partyUpAndEnterDungeon));
        steps.add(new Step("pair-duel", 6, this::startDuel));
        steps.add(new Step("pair-trade", 6, this::openTradeAndConfirm));
        return steps;
    }

    private void addPartySteps(List<Step> steps) {
        steps.add(new Step(
                "party-create",
                4,
                actor -> send(
                        actor,
                        Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                        CreatePartyCommand.getDefaultInstance().toByteArray())));
        steps.add(new Step(
                "party-invite",
                6,
                actor -> send(
                        actor,
                        Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE,
                        InvitePartyMemberCommand.newBuilder()
                                .setTargetName(other(actor).characterName)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "party-list-invites",
                5,
                actor -> send(
                        actor,
                        Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_REQUEST_VALUE,
                        ListPendingPartyInvitesRequest.getDefaultInstance().toByteArray())));
        steps.add(new Step(
                "party-accept",
                6,
                actor -> send(
                        actor,
                        Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE,
                        AcceptPartyInviteCommand.newBuilder()
                                .setInviteId(actor.hints.partyInviteId)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "party-leave",
                1,
                actor -> send(
                        actor,
                        Opcode.OPCODE_LEAVE_PARTY_COMMAND_VALUE,
                        LeavePartyCommand.getDefaultInstance().toByteArray())));
        steps.add(new Step(
                "dungeon-enter",
                8,
                actor -> send(
                        actor,
                        Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                        EnterDungeonCommand.newBuilder()
                                .setDungeonCode(DUNGEON_CODE)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "dungeon-exit",
                3,
                actor -> send(
                        actor,
                        Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE,
                        ExitDungeonCommand.getDefaultInstance().toByteArray())));
    }

    private void addDuelSteps(List<Step> steps) {
        steps.add(new Step(
                "duel-challenge",
                7,
                actor -> send(
                        actor,
                        Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE,
                        ChallengeDuelCommand.newBuilder()
                                .setTargetName(other(actor).characterName)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "duel-accept",
                7,
                actor -> send(
                        actor,
                        Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE,
                        AcceptDuelCommand.newBuilder()
                                .setChallengeId(actor.hints.challengeId)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "duel-forfeit",
                2,
                actor -> send(
                        actor,
                        Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE,
                        ExitDuelCommand.getDefaultInstance().toByteArray())));
    }

    private void addTradeSteps(List<Step> steps) {
        steps.add(new Step(
                "trade-request",
                6,
                actor -> send(
                        actor,
                        Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE,
                        RequestTradeCommand.newBuilder()
                                .setTargetName(other(actor).characterName)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "trade-status",
                3,
                actor -> send(
                        actor,
                        Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                        GetTradeStatusRequest.getDefaultInstance().toByteArray())));
        steps.add(new Step(
                "trade-accept",
                6,
                actor -> send(
                        actor,
                        Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE,
                        AcceptTradeRequestCommand.newBuilder()
                                .setSessionId(actor.hints.tradeSessionId)
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "trade-offer", 6, actor -> send(actor, Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, offer(actor))));
        steps.add(new Step(
                "trade-confirm",
                8,
                actor -> send(
                        actor,
                        Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE,
                        ConfirmTradeCommand.newBuilder()
                                .setSessionId(actor.hints.tradeSessionId)
                                .setIdempotencyKey(new UUID(random.nextLong(), random.nextLong()).toString())
                                .build()
                                .toByteArray())));
        steps.add(new Step(
                "trade-cancel",
                2,
                actor -> send(
                        actor,
                        Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE,
                        CancelTradeCommand.newBuilder()
                                .setSessionId(actor.hints.tradeSessionId)
                                .build()
                                .toByteArray())));
    }

    private void partyUpAndEnterDungeon(Actor actor) throws InterruptedException {
        Actor partner = other(actor);
        send(
                actor,
                Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                CreatePartyCommand.getDefaultInstance().toByteArray());
        send(
                actor,
                Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE,
                InvitePartyMemberCommand.newBuilder()
                        .setTargetName(partner.characterName)
                        .build()
                        .toByteArray());
        if (!partner.isOnline()) {
            return;
        }
        Thread.sleep(PAIR_STEP_MILLIS);
        send(
                partner,
                Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_REQUEST_VALUE,
                ListPendingPartyInvitesRequest.getDefaultInstance().toByteArray());
        Thread.sleep(PAIR_STEP_MILLIS);
        send(
                partner,
                Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE,
                AcceptPartyInviteCommand.newBuilder()
                        .setInviteId(partner.hints.partyInviteId)
                        .build()
                        .toByteArray());
        Thread.sleep(random.nextInt(PAIR_STEP_MILLIS));
        send(
                actor,
                Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                EnterDungeonCommand.newBuilder()
                        .setDungeonCode(DUNGEON_CODE)
                        .build()
                        .toByteArray());
    }

    private void startDuel(Actor actor) throws InterruptedException {
        Actor opponent = other(actor);
        send(
                actor,
                Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE,
                ChallengeDuelCommand.newBuilder()
                        .setTargetName(opponent.characterName)
                        .build()
                        .toByteArray());
        if (!opponent.isOnline()) {
            return;
        }
        Thread.sleep(PAIR_STEP_MILLIS);
        send(
                opponent,
                Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE,
                AcceptDuelCommand.newBuilder()
                        .setChallengeId(opponent.hints.challengeId)
                        .build()
                        .toByteArray());
    }

    private void openTradeAndConfirm(Actor actor) throws InterruptedException {
        Actor partner = other(actor);
        send(
                actor,
                Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE,
                RequestTradeCommand.newBuilder()
                        .setTargetName(partner.characterName)
                        .build()
                        .toByteArray());
        if (!partner.isOnline()) {
            return;
        }
        Thread.sleep(PAIR_STEP_MILLIS);
        send(
                partner,
                Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE,
                AcceptTradeRequestCommand.newBuilder()
                        .setSessionId(partner.hints.tradeSessionId)
                        .build()
                        .toByteArray());
        Thread.sleep(PAIR_STEP_MILLIS);
        send(actor, Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, offer(actor));
        send(partner, Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, offer(partner));
        Thread.sleep(PAIR_STEP_MILLIS);
        // Hai lệnh xác nhận bay cùng lúc; bước kế tiếp (có thể là ngắt kết nối) chạy đua với lượt thi hành.
        send(actor, Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, confirm(actor));
        send(partner, Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, confirm(partner));
    }

    private byte[] confirm(Actor actor) {
        return ConfirmTradeCommand.newBuilder()
                .setSessionId(actor.hints.tradeSessionId)
                .setIdempotencyKey(new UUID(random.nextLong(), random.nextLong()).toString())
                .build()
                .toByteArray();
    }

    private void logInOrFail(Actor actor) throws InterruptedException {
        if (!logInFully(actor)) {
            throw new AssertionError("đăng nhập không xong trong " + LOGIN_BUDGET + ": " + actor.characterName);
        }
    }

    private DisconnectFuzzClient openAndConnect(Actor actor) throws InterruptedException {
        DisconnectFuzzClient client = server.open(actor.hints);
        actor.client = client;
        client.send(
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                ConnectRequest.newBuilder().setUsername(actor.username).build().toByteArray());
        return client;
    }

    private DisconnectFuzzClient connected(Actor actor) throws InterruptedException {
        DisconnectFuzzClient client = openAndConnect(actor);
        if (client.await(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, LOGIN_BUDGET).isEmpty()) {
            throw new AssertionError("không nhận được ConnectResponse: " + actor.characterName);
        }
        return client;
    }

    private static void sendSelect(DisconnectFuzzClient client, Actor actor) {
        client.send(
                Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(actor.characterId)
                        .build()
                        .toByteArray());
    }

    private static void send(Actor actor, int opcode, byte[] payload) {
        actor.client().send(opcode, payload);
    }

    private Actor other(Actor actor) {
        Actor picked = actors.get(random.nextInt(actors.size()));
        while (picked.characterId == actor.characterId) {
            picked = actors.get(random.nextInt(actors.size()));
        }
        return picked;
    }

    private byte[] move() {
        return MoveCommand.newBuilder().setDirX(random.nextInt(3) - 1).build().toByteArray();
    }

    private static byte[] chat() {
        return SendChatMessageCommand.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_AREA)
                .setBody("fuzz")
                .build()
                .toByteArray();
    }

    private void attackOpponent(Actor actor) {
        send(
                actor,
                Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                SetTargetCommand.newBuilder()
                        .setTargetEntityId(actor.hints.duelOpponentId)
                        .build()
                        .toByteArray());
        send(
                actor,
                Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                AttackCommand.getDefaultInstance().toByteArray());
    }

    /** Lời chào: ít vàng, thường kèm vài món của chính mình — đôi khi món của NGƯỜI KHÁC (phải hỏng sạch). */
    private byte[] offer(Actor actor) {
        SetTradeOfferCommand.Builder offer = SetTradeOfferCommand.newBuilder()
                .setSessionId(actor.hints.tradeSessionId)
                .setGoldAmount(random.nextInt(MAX_GOLD_OFFER));
        int kind = random.nextInt(10);
        if (kind > 0) {
            long itemId = kind == 1 ? other(actor).itemId : actor.itemId;
            offer.addItems(
                    TradeOfferItemInput.newBuilder().setItemId(itemId).setQuantity(1 + random.nextInt(MAX_ITEM_OFFER)));
        }
        return offer.build().toByteArray();
    }
}
