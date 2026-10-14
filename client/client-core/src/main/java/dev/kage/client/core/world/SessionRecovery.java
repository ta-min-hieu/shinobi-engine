package dev.kage.client.core.world;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.HeldFrames;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tự đăng nhập lại và chọn lại ĐÚNG nhân vật cũ sau khi đường truyền đứt thật — quyết định chủ dự
 * án 2026-09-29 (CLIENT.md §8).
 *
 * <p><b>Vì sao cần.</b> {@code GameConnection} tự nối lại socket, nhưng phiên trên server gắn với
 * channel cũ và đã bị detach lúc channel đóng — socket mới là một kết nối CHƯA đăng nhập. Lớp này
 * chạy lại đúng chuỗi của {@code LoginScreen} → {@code CharacterSelectScreen} (Connect → List →
 * Select) trên socket mới, rồi trả về gói vào world để màn hình dựng lại một {@code WorldScreen}
 * sạch cho cùng nhân vật.
 *
 * <p><b>Dùng gì để đăng nhập lại.</b> Giao thức hôm nay chỉ đăng nhập bằng {@code username}
 * (ADR-017, không mật khẩu); {@code ConnectRequest.token} còn dự phòng và server CHƯA đọc nó, còn
 * {@code session_id} trong {@code ConnectResponse} không có đường nào để trình lại. Nên lớp này giữ
 * {@code username} TRONG BỘ NHỚ của tiến trình đang chạy — không ghi ra đĩa, và không có mật khẩu
 * nào để giữ.
 *
 * <p><b>Không bao giờ chạy sau khi bị đá.</b> {@code error.logged_in_elsewhere} nghĩa là chính người
 * chơi vừa vào nhân vật này từ nơi khác; tự chọn lại sẽ đá ngược bên kia, và bên kia lại tự chọn
 * lại — hai client đá nhau mãi. {@link #shouldStart} chặn trường hợp đó, và nếu khoá ấy tới GIỮA lúc
 * đang phục hồi thì phục hồi dừng ngay, không thử lại.
 *
 * <p><b>Luồng.</b> Thuần: không libGDX, không đồng hồ hệ thống — thời gian đi vào qua {@link #tick}
 * trên render thread (cùng chỗ mọi gói được xử lý, CLIENT.md §8); gói vào qua {@link Link#pollInbound}
 * (hàng đợi của luồng mạng). Nhờ vậy test dựng thẳng được bằng một {@link Link} giả.
 *
 * <p><b>Khớp gói theo {@code sequenceId}.</b> Server trả Response và {@code ErrorResponse} với đúng
 * {@code sequenceId} của Command. Chỉ gói mang số của CHÍNH yêu cầu đang chờ mới được tính — lệnh
 * gameplay lỡ gửi lên socket mới trước khi đăng nhập xong nhận về {@code error.not_authenticated},
 * và lỗi đó không được hiểu nhầm thành "đăng nhập bị từ chối".
 */
final class SessionRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(SessionRecovery.class);

    /** Số lượt bắt tay tối đa (một lượt = Connect → List → Select trên một socket). */
    static final int MAX_ATTEMPTS = 4;

    /** Nghỉ giữa hai lượt, tăng dần — cùng tinh thần backoff của {@code GameConnection} (NETWORK.md §10). */
    private static final float[] RETRY_DELAY_SECONDS = {1f, 2f, 4f};

    /**
     * Hạn TỔNG cho cả lần tự vào lại, mọi lượt cộng lại (quyết định chủ dự án 2026-10-01). Bản cũ cho
     * mỗi lượt chờ socket 45 giây riêng — 4 lượt cộng dồn, kiểm sống 2026-09-30 đo được 3 phút 07 giây
     * đứng nhìn một thế giới im trước khi về màn đăng nhập. {@code GameConnection} thử lại theo 1, 2,
     * 4, 8, 16, 30 giây: 60 giây vẫn phủ năm lần thử đầu, đủ cho một lần mạng chập chờn thật.
     */
    static final float TOTAL_DEADLINE_SECONDS = 60f;

    /** Hạn chờ mỗi Response — cùng con số {@code STEP_TIMEOUT_SECONDS} của hai màn đăng nhập. */
    static final float STEP_TIMEOUT_SECONDS = 5f;

    /** Khoá lý do khi hết lượt mà không vào lại được (không có khoá nào của server để nói). */
    static final String RELOG_FAILED_KEY = "login.error.relog_failed";

    /** Khoá server dùng khi phiên bị đá — cùng chuỗi {@link ConnectionCoordinator#LOGGED_IN_ELSEWHERE_KEY}. */
    private static final String KICKED_KEY = ConnectionCoordinator.LOGGED_IN_ELSEWHERE_KEY;

    /** Trạng thái của cỗ máy. {@link #RESUMED} và {@link #FAILED} là trạng thái cuối. */
    enum Phase {
        WAITING_FOR_SOCKET,
        BACKING_OFF,
        AWAITING_CONNECT,
        AWAITING_CHARACTER_LIST,
        AWAITING_SELECT,
        RESUMED,
        FAILED
    }

    /**
     * Phần đường truyền mà lớp này cần — {@link WorldCommandSender} cài, test cài bản giả.
     */
    interface Link {

        /** Socket hiện có sống không. */
        boolean isConnected();

        /** Xem {@code GameConnection#connectionGeneration()}: đổi = socket đã được dựng lại. */
        int connectionGeneration();

        /**
         * Gửi một yêu cầu, trả về {@code sequenceId} đã gắn cho nó để khớp Response.
         *
         * @throws IOException socket đứt giữa lúc gửi
         */
        int sendRequest(int opcode, byte[] payload) throws IOException;

        /** Gói kế tiếp trong hàng đợi, {@code null} khi rỗng (cùng hợp đồng {@code GameConnection}). */
        PacketCodec.Frame pollInbound();

        /** Đưa gói đã đọc quay lại đầu hàng đợi — cùng hợp đồng {@code GameConnection#replayBeforeQueued}. */
        void replayBeforeQueued(List<PacketCodec.Frame> frames);
    }

    /** Nhân vật (bản mới nhất từ server) và gói vào world — đủ để dựng lại màn world. */
    record Resumed(CharacterSummary character, SelectCharacterResponse enterWorld) {}

    private final Link link;
    private final String username;
    private final long characterId;

    private Phase phase = Phase.WAITING_FOR_SOCKET;
    private float phaseElapsed;
    private float totalElapsed;
    private int attempt;
    private int attemptGeneration;
    private int pendingSequenceId;
    private CharacterSummary character;
    private Resumed resumed;

    /**
     * Gói tới trong lúc chờ {@code SelectCharacterResponse} — server gắn nhân vật vào world TRƯỚC khi trả
     * lời (2026-10-14, docs/README.md dòng 73(c)), nên gói world có thể tới trước. Trả lại cho màn world
     * khi vào được, vứt khi lượt này hỏng.
     */
    private final HeldFrames heldWhileSelecting = new HeldFrames();

    private String failureKey = RELOG_FAILED_KEY;

    /**
     * @param username tên đăng nhập của phiên vừa đứt — chỉ sống trong bộ nhớ (xem javadoc lớp)
     * @param characterId nhân vật phải chọn lại — KHÔNG bao giờ chọn nhân vật khác
     */
    SessionRecovery(Link link, String username, long characterId) {
        this.link = link;
        this.username = username;
        this.characterId = characterId;
    }

    /**
     * Có bắt đầu tự đăng nhập lại ở nhịp này không.
     *
     * @param sessionEndedKey khoá server dùng để kết thúc phiên ({@code error.logged_in_elsewhere}),
     *     {@code null} khi phiên không bị kết thúc — khác {@code null} thì KHÔNG BAO GIỜ tự đăng nhập
     *     lại, kể cả khi socket cũng đã đứt (server đóng kết nối ngay sau khi đá)
     * @param sessionGeneration {@link Link#connectionGeneration()} lúc phiên hiện tại vào world
     * @param currentGeneration {@link Link#connectionGeneration()} bây giờ
     * @param connected socket bây giờ có sống không
     */
    static boolean shouldStart(
            String sessionEndedKey, int sessionGeneration, int currentGeneration, boolean connected) {
        if (sessionEndedKey != null) {
            return false;
        }
        return !connected || currentGeneration != sessionGeneration;
    }

    /** Trạng thái hiện tại. */
    Phase phase() {
        return phase;
    }

    /** Kết quả khi {@link #phase()} là {@link Phase#RESUMED}, rỗng ở mọi trạng thái khác. */
    Optional<Resumed> resumed() {
        return Optional.ofNullable(resumed);
    }

    /** Khoá i18n nói lý do khi {@link #phase()} là {@link Phase#FAILED}. */
    String failureKey() {
        return failureKey;
    }

    /** Lượt đang chạy, đếm từ 1 — để dòng báo nói được "lần thứ mấy". */
    int attemptNumber() {
        return attempt + 1;
    }

    /**
     * Một nhịp: đọc gói, tiến trạng thái, gửi yêu cầu kế tiếp khi tới lúc.
     *
     * <p>Dừng đọc hàng đợi NGAY khi vào world lại được: các gói sau {@code SelectCharacterResponse}
     * (spawn, máu...) thuộc về màn world MỚI, không phải của lớp này.
     */
    Phase tick(float deltaSeconds) {
        phaseElapsed += deltaSeconds;
        totalElapsed += deltaSeconds;
        if (phase != Phase.RESUMED && phase != Phase.FAILED && totalElapsed > TOTAL_DEADLINE_SECONDS) {
            LOG.info("Hết hạn tổng {}s tự đăng nhập lại ở lượt {}", TOTAL_DEADLINE_SECONDS, attemptNumber());
            fail(RELOG_FAILED_KEY);
            return phase;
        }
        switch (phase) {
            case WAITING_FOR_SOCKET -> tickWaitingForSocket();
            case BACKING_OFF -> tickBackingOff();
            case AWAITING_CONNECT, AWAITING_CHARACTER_LIST, AWAITING_SELECT -> tickAwaiting();
            default -> {
                // RESUMED/FAILED — trạng thái cuối, không còn gì để làm.
            }
        }
        return phase;
    }

    private void tickWaitingForSocket() {
        if (link.isConnected()) {
            attemptGeneration = link.connectionGeneration();
            send(
                    Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                    ConnectRequest.newBuilder().setUsername(username).build().toByteArray(),
                    Phase.AWAITING_CONNECT);
        }
        // Socket chưa về thì cứ chờ — hạn tổng ở tick() là thứ chặn chờ vô hạn.
    }

    private void tickBackingOff() {
        if (phaseElapsed >= RETRY_DELAY_SECONDS[Math.min(attempt - 1, RETRY_DELAY_SECONDS.length - 1)]) {
            enter(Phase.WAITING_FOR_SOCKET);
        }
    }

    private void tickAwaiting() {
        PacketCodec.Frame frame;
        while (isAwaiting() && (frame = link.pollInbound()) != null) {
            onFrame(frame);
        }
        if (!isAwaiting()) {
            return;
        }
        if (!link.isConnected() || link.connectionGeneration() != attemptGeneration) {
            attemptFailed("socket đứt giữa lúc bắt tay");
        } else if (phaseElapsed > STEP_TIMEOUT_SECONDS) {
            attemptFailed("hết giờ chờ " + phase);
        }
    }

    private boolean isAwaiting() {
        return phase == Phase.AWAITING_CONNECT
                || phase == Phase.AWAITING_CHARACTER_LIST
                || phase == Phase.AWAITING_SELECT;
    }

    private void onFrame(PacketCodec.Frame frame) {
        try {
            int opcode = frame.header().opcode();
            if (opcode == Opcode.OPCODE_ERROR_VALUE) {
                onError(ErrorResponse.parseFrom(frame.payload()), frame.header().sequenceId());
            } else if (frame.header().sequenceId() == pendingSequenceId) {
                onResponse(opcode, frame.payload());
            } else if (phase == Phase.AWAITING_SELECT) {
                heldWhileSelecting.hold(frame);
            }
            // Gói khác ở các bước trước (PONG, gói lạc của lệnh cũ) — không phải thứ đang chờ, bỏ qua.
        } catch (InvalidProtocolBufferException e) {
            LOG.warn(
                    "Gói trả lời không hợp lệ lúc đăng nhập lại: opcode={}",
                    frame.header().opcode(),
                    e);
            attemptFailed("payload hỏng");
        }
    }

    /**
     * Bị đá thì dừng bất kể {@code sequenceId} (gói đá không trả lời lệnh nào). Lỗi của CHÍNH yêu
     * cầu đang chờ là server từ chối thật (tên không hợp lệ, nhân vật không thuộc tài khoản...) —
     * thử lại cũng ra đúng câu đó, nên dừng và nói lý do. Lỗi của lệnh khác thì bỏ qua.
     */
    private void onError(ErrorResponse error, int sequenceId) {
        if (KICKED_KEY.equals(error.getErrorKey()) || sequenceId == pendingSequenceId) {
            fail(error.getErrorKey());
        }
    }

    private void onResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (phase == Phase.AWAITING_CONNECT && opcode == Opcode.OPCODE_CONNECT_RESPONSE_VALUE) {
            onConnectResponse(ConnectResponse.parseFrom(payload));
        } else if (phase == Phase.AWAITING_CHARACTER_LIST && opcode == Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE) {
            onCharacterList(ListCharactersResponse.parseFrom(payload));
        } else if (phase == Phase.AWAITING_SELECT && opcode == Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE) {
            onSelectResponse(SelectCharacterResponse.parseFrom(payload));
        }
    }

    private void onConnectResponse(ConnectResponse response) {
        if (!response.getSuccess()) {
            fail(response.getErrorKey());
            return;
        }
        send(
                Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                ListCharactersRequest.getDefaultInstance().toByteArray(),
                Phase.AWAITING_CHARACTER_LIST);
    }

    /**
     * Xin lại danh sách chứ không dùng lại {@code CharacterSummary} cũ: cấp và hệ phái có thể đã đổi
     * trong phiên vừa đứt (lên cấp, chọn hệ ở Lv10), và màn world vẽ model theo hệ phái.
     */
    private void onCharacterList(ListCharactersResponse response) {
        Optional<CharacterSummary> same = response.getCharactersList().stream()
                .filter(summary -> summary.getId() == characterId)
                .findFirst();
        if (same.isEmpty()) {
            fail(RELOG_FAILED_KEY);
            return;
        }
        character = same.get();
        send(
                Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                SelectCharacterRequest.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray(),
                Phase.AWAITING_SELECT);
    }

    private void onSelectResponse(SelectCharacterResponse response) {
        if (!response.getSuccess()) {
            fail(response.getErrorKey());
            return;
        }
        resumed = new Resumed(character, response);
        link.replayBeforeQueued(heldWhileSelecting.drain());
        enter(Phase.RESUMED);
    }

    private void send(int opcode, byte[] payload, Phase next) {
        heldWhileSelecting.clear();
        try {
            pendingSequenceId = link.sendRequest(opcode, payload);
            enter(next);
        } catch (IOException e) {
            LOG.debug("Gửi opcode {} lúc đăng nhập lại thất bại", opcode, e);
            attemptFailed("gửi thất bại");
        }
    }

    private void attemptFailed(String reason) {
        attempt++;
        LOG.info("Lượt đăng nhập lại {} hỏng: {}", attempt, reason);
        if (attempt >= MAX_ATTEMPTS) {
            fail(RELOG_FAILED_KEY);
            return;
        }
        enter(Phase.BACKING_OFF);
    }

    /** @param errorKey khoá server gửi; rỗng (gói không mang khoá) thì dùng câu chung */
    private void fail(String errorKey) {
        failureKey = errorKey == null || errorKey.isEmpty() ? RELOG_FAILED_KEY : errorKey;
        enter(Phase.FAILED);
    }

    private void enter(Phase next) {
        phase = next;
        phaseElapsed = 0f;
    }
}
