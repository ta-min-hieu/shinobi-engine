package dev.kage.client.core.world;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Mọi thứ ở mức ĐƯỜNG TRUYỀN: hai gói của {@code system.proto} — {@code PONG} (2) và
 * {@code SERVER_SHUTTING_DOWN_EVENT} (6) — cộng với việc theo dõi đứt/nối lại ({@link #onTick}).
 *
 * <p><b>Vì sao gộp một lớp.</b> Cả hai đều không thuộc tính năng gameplay nào, đều không có cửa sổ
 * riêng, và đều nói về chính đường truyền chứ không về nhân vật — tách hai lớp một-hàm sẽ là hai
 * lớp cùng nói một chuyện. Đăng ký vào {@link PanelHub} như một luồng không cửa sổ, cùng khuôn
 * {@link DungeonCoordinator}/{@code QuestCoordinator}.
 *
 * <h2>Độ trễ (PONG)</h2>
 *
 * {@code GameConnection} bắn {@code PingCommand} kèm {@code client_timestamp_ms} mỗi 15 giây;
 * server echo lại đúng con số đó. Hiệu số với đồng hồ hiện tại là round-trip time THẬT, đo bằng
 * MỘT đồng hồ duy nhất (đồng hồ của client) nên không phụ thuộc việc hai máy có lệch giờ hay không
 * — {@code server_timestamp_ms} vì vậy CỐ TÌNH không được dùng ở đây.
 *
 * <h2>Server tắt (SERVER_SHUTTING_DOWN_EVENT)</h2>
 *
 * Gói rỗng, server gửi NGAY TRƯỚC khi đóng kết nối lúc bảo trì có chủ đích (SERVER.md §10). Việc
 * duy nhất client làm được lúc này là nói cho người chơi biết đây là bảo trì chứ không phải mạng
 * hỏng — kết nối sẽ đứt ngay sau đó dù client có làm gì đi nữa.
 */
final class ConnectionCoordinator implements WindowFlow {

    /**
     * Trần "còn tin được" cho một round-trip. Bằng ĐÚNG chu kỳ heartbeat (15 giây,
     * {@code GameConnection}): chậm hơn thế thì gói tiếp theo đã đi rồi, con số hiện ra không còn
     * nói về đường truyền hiện tại nữa.
     */
    static final long MAX_PLAUSIBLE_ROUND_TRIP_MS = 15_000L;

    private final CombatHud hud;
    private final ChatCoordinator notices;
    private final I18NBundle i18n = KageI18n.load();

    /**
     * Trạng thái đường truyền ở nhịp TRƯỚC. {@code null} = chưa đo lần nào, nên nhịp đầu tiên chỉ
     * ghi nhận chứ không báo gì — không thì mỗi lần vào world lại hiện một dòng "đã kết nối lại".
     */
    private Boolean wasConnected;

    /**
     * @param notices kênh báo bảo trì — nhật ký chat, cùng chỗ {@link DungeonCoordinator} và
     *     {@link PartyCoordinator} đã dùng: một câu người chơi cần ĐỌC chứ không cần bấm.
     */
    ConnectionCoordinator(CombatHud hud, ChatCoordinator notices) {
        this.hud = hud;
        this.notices = notices;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_PONG_VALUE) {
            onPong(PongResponse.parseFrom(frame.payload()));
            return true;
        }
        if (opcode == Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE) {
            // Gói RỖNG (system.proto) — không parse, chỉ là một tín hiệu.
            notices.showSystemNotice(text("system.notice.server_shutdown"));
            return true;
        }
        return false;
    }

    /** Một nhịp heartbeat vừa về — bỏ qua gói không tin được, xem {@link #plausibleRoundTrip}. */
    private void onPong(PongResponse response) {
        plausibleRoundTrip(response.getClientTimestampMs(), System.currentTimeMillis())
                .ifPresent(hud::setLatency);
    }

    /**
     * Round-trip time của một {@code PongResponse}, hoặc rỗng khi con số không tin được.
     *
     * <p>Tách ra thành hàm tĩnh THUẦN (không libGDX, không đồng hồ hệ thống) để test được thẳng —
     * {@link CombatHud} cần ngữ cảnh GL nên bản thân lớp này không dựng được trong JUnit.
     *
     * <p>Bỏ hai loại gói: <b>âm</b> (dấu thời gian ở TƯƠNG LAI — đồng hồ vừa bị chỉnh giữa lúc gói
     * đi và gói về) và <b>quá {@link #MAX_PLAUSIBLE_ROUND_TRIP_MS}</b> (gói lạc của một phiên cũ).
     * Cả hai chỉ có thể làm con số trên màn hình sai, không có cách nào làm nó đúng hơn.
     *
     * @param clientTimestampMs dấu thời gian client tự gắn lúc gửi, server echo lại nguyên vẹn
     * @param nowMs đồng hồ client lúc gói về — CÙNG một đồng hồ với {@code clientTimestampMs}, nên
     *     hiệu số không phụ thuộc việc hai máy có lệch giờ hay không
     */
    static java.util.OptionalLong plausibleRoundTrip(long clientTimestampMs, long nowMs) {
        long roundTripMs = nowMs - clientTimestampMs;
        if (roundTripMs < 0L || roundTripMs > MAX_PLAUSIBLE_ROUND_TRIP_MS) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of(roundTripMs);
    }

    /**
     * Nhịp mỗi tick: nói cho người chơi biết đường truyền vừa đứt hay vừa nối lại.
     *
     * <p><b>Vì sao cần.</b> {@code GameConnection} tự kết nối lại trong im lặng, nhưng phiên trên
     * server thì KHÔNG sống lại: {@code boundCharacterId} là trạng thái của riêng một channel, và
     * server đã detach nhân vật lúc kết nối đứt. Sau khi nối lại, client ngồi trên một socket sống
     * với một phiên đã chết — mọi lệnh gameplay bị trả về {@code error.not_authenticated}, mà
     * {@code ErrorResponse} trong màn world chỉ có {@code FactionCoordinator} đọc nên **người chơi
     * không thấy gì cả**: thế giới đứng im, thanh máu đứng im, không một dòng thông báo. Cách thoát
     * duy nhất là tự đoán ra mà khởi động lại.
     *
     * <p>Chỗ này CHỈ nói ra sự thật đó. Việc chọn hành vi đúng — tự đăng nhập lại và nối tiếp phiên
     * cũ, hay đưa thẳng về màn đăng nhập — là quyết định thiết kế (/CLAUDE.md §4), chưa chốt.
     *
     * <p>Chỉ báo ở LẰN RANH đổi trạng thái, không báo mỗi tick: người chơi cần biết điều vừa xảy
     * ra, không cần bị nhắc 60 lần một giây.
     */
    void onTick(boolean connected) {
        noticeKeyFor(wasConnected, connected).ifPresent(key -> notices.showSystemNotice(text(key)));
        wasConnected = connected;
    }

    /**
     * Khoá i18n cần nói ra ở lằn ranh trạng thái, hoặc rỗng khi không có gì để nói.
     *
     * <p>Tách thành hàm tĩnh THUẦN vì cùng lý do {@link #plausibleRoundTrip}: {@link CombatHud} cần
     * ngữ cảnh GL nên bản thân lớp này không dựng được trong JUnit.
     *
     * @param previous trạng thái nhịp trước, {@code null} = chưa đo lần nào
     */
    static java.util.Optional<String> noticeKeyFor(Boolean previous, boolean connected) {
        if (previous == null || previous == connected) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(connected ? "system.notice.reconnected" : "system.notice.connection_lost");
    }

    /** Rời màn hình/mất kết nối — độ trễ của phiên trước không được nằm lại trên HUD phiên sau. */
    @Override
    public void reset() {
        hud.clearLatency();
        wasConnected = null;
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }
}
