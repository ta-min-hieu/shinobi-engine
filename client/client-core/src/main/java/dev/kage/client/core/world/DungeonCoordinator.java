package dev.kage.client.core.world;

import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.PartyPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DungeonEnteredEvent;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link DungeonController} (trạng thái), {@link PartyPanel}/{@link CombatHud} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành lối vào hầm ngục chạy được (2026-08-27) — cùng hình dạng
 * {@link GuildCoordinator}/{@link PartyCoordinator}, và cùng lý do tồn tại: {@code WorldScreen}
 * đang ở ĐÚNG trần {@code CyclomaticComplexity} của PMD.
 *
 * <p>Dải opcode 1500-1503 chưa từng được client dùng trước hôm nay, dù hạ tầng instance phía server
 * đã xong từ Phase 8 lát cắt 1 (ADR-026).
 *
 * <p>Hai nút nằm trong cửa sổ Nhóm chứ không phải một cửa sổ riêng: hầm ngục lát cắt này GẮN CHẶT
 * với nhóm ({@code ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY}), và một cửa sổ mới chỉ để chứa hai cái nút
 * là thêm một phím tắt nữa cho người chơi phải nhớ mà không đổi được gì.
 */
final class DungeonCoordinator implements WindowFlow {

    private final DungeonController controller = new DungeonController();
    private final CombatHud hud;
    private final ChatCoordinator notices;
    private final I18NBundle i18n = KageI18n.load();

    /** Xem {@link #setOnWorldChanged}. Mặc định không làm gì — lớp này tự chạy được trong test. */
    private Runnable onWorldChanged = () -> {};

    /**
     * @param notices kênh báo kết quả — nhật ký chat, cùng chỗ {@link PartyCoordinator} đã dùng để
     *     giải thích vì sao một lệnh nhóm hỏng. Hai luồng nói về cùng một nhóm người thì nên nói ở
     *     cùng một chỗ.
     */
    DungeonCoordinator(PartyPanel panel, CombatHud hud, WorldCommandSender sender, ChatCoordinator notices) {
        this.hud = hud;
        this.notices = notices;
        panel.setOnEnterDungeon(() -> sender.enterDungeon(DungeonController.DUNGEON_CODE));
        panel.setOnExitDungeon(sender::exitDungeon);
        refreshBanner();
    }

    /**
     * Việc phải làm khi server vừa xác nhận nhân vật ĐỔI World — dọn sạch mọi thực thể đang hiển
     * thị rồi xin lại vị trí của chính mình.
     *
     * <p>Bắt buộc phải có vì giao thức không có gói "đổi World" nào: người rời world chính KHÔNG
     * nhận được despawn của những thứ họ đang thấy (xem khoảng trống thứ hai ở
     * {@link DungeonController}). Không dọn thì bên trong hầm ngục vẫn còn nguyên hình nộm, ông già
     * làng và những người chơi khác của làng đứng lơ lửng — thứ mà chính server cũng không biết là
     * đang hiện trên màn hình này.
     *
     * <p>Đây KHÔNG phải client tự quyết trạng thái: nó chỉ VỨT đi dữ liệu đã hết hạn, không bịa ra
     * dữ liệu mới. World mới tự phát lại spawn cho mọi thứ thật sự nhìn thấy được ở đó
     * ({@code AreaOfInterestTracker.onAttach} khởi tạo tập "đang thấy" rỗng, nên lượt {@code sync}
     * kế tiếp phát lại đủ) — cái bị xoá mà vẫn còn thật sẽ quay lại ngay, cái không quay lại là cái
     * đáng ra không được ở đó.
     */
    void setOnWorldChanged(Runnable listener) {
        this.onWorldChanged = listener;
    }

    /** Theo lời server — xem {@link DungeonController#inDungeon()} về giới hạn của câu trả lời này. */
    boolean inDungeon() {
        return controller.inDungeon();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng hầm ngục.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE) {
            settleEnter(EnterDungeonResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE) {
            settleExit(ExitDungeonResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE) {
            // Không field nào cần đọc ở lát cắt này — parse để chắc payload hợp lệ, cùng cách
            // GatewayChannelHandler làm với ExitDungeonCommand.
            DungeonEnteredEvent.parseFrom(payload);
            settlePulledIn();
            return true;
        }
        return false;
    }

    /**
     * Nói ra kết quả, ghi trạng thái theo lời server, rồi dọn màn hình nếu vừa đổi World.
     *
     * <p>⚠️ Thứ tự dưới đây là GIẢ ĐỊNH, không phải bảo đảm: phía server còn đọc DB cho các thành
     * viên khác SAU khi đã attach người gửi, nên vòng {@code sync} của world mới có thể lọt vào trước
     * response — xem mục "Phát hiện phụ" của ADR-047.
     *
     * <p>Thứ tự "response tới TRƯỚC entity của world mới" là thứ làm cho việc dọn ở đây an toàn:
     * {@code handleEnterDungeonBlocking} phía server trả lời NGAY trong lời gọi
     * {@code enterDungeon()}, còn attach/spawn chỉ là lệnh vừa được xếp vào hộp thư của World và
     * phải chờ tick sau — nên không có gói spawn nào của hầm ngục bị cú dọn này xoá mất.
     */
    private void settleEnter(EnterDungeonOutcome outcome) {
        controller.onEnterOutcome(outcome);
        announce(DungeonController.enterMessageKey(outcome));
        if (DungeonController.changesWorld(outcome)) {
            onWorldChanged.run();
        }
        refreshBanner();
    }

    /**
     * ADR-047 — chính người chơi này vừa bị bạn cùng nhóm KÉO vào hầm ngục. Không có lệnh nào của
     * họ đang treo, nên đây là gói ĐẨY chứ không phải response.
     *
     * <p>Đi ĐÚNG con đường {@link #settleEnter} đã đi, chỉ khác dòng chữ: ghi trạng thái, dọn sạch
     * thực thể của world cũ, rồi để {@code onWorldChanged} xin lại vị trí. Server ghi gói này vào
     * kênh TRƯỚC khi attach (ADR-047 D3), nên cú dọn ở đây không thể xoá mất boss hay đồng đội.
     *
     * <p>{@code initiator_character_id} chưa được hiện ra chữ — client chưa có đường tra tên từ id
     * cho người không nằm trong tầm nhìn. Xem javadoc message trong {@code dungeon.proto}.
     */
    private void settlePulledIn() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);
        announce(Optional.of("dungeon.notice.pulled_in"));
        onWorldChanged.run();
        refreshBanner();
    }

    private void settleExit(ExitDungeonOutcome outcome) {
        controller.onExitOutcome(outcome);
        announce(DungeonController.exitMessageKey(outcome));
        if (DungeonController.changesWorld(outcome)) {
            onWorldChanged.run();
        }
        refreshBanner();
    }

    /**
     * @param messageKey rỗng khi client không nhận ra outcome (server MỚI HƠN client) — nói thẳng ra
     *     là không hiểu thay vì im lặng hoặc đoán bừa.
     */
    private void announce(Optional<String> messageKey) {
        notices.showSystemNotice(text(messageKey.orElse("dungeon.error.unknown")));
    }

    /**
     * Dải chữ trên HUD. Đang ở trong hầm ngục là một trạng thái NGUY HIỂM để không biết mình đang ở
     * — map trống, không có mốc nào phân biệt với world chính bằng mắt ở lát cắt này — nên nó phải
     * hiện thường trực chứ không chỉ là một dòng thoáng qua trong nhật ký chat.
     */
    private void refreshBanner() {
        hud.setDungeonBanner(controller.inDungeon() ? text("dungeon.hud.inside") : "", text("dungeon.hud.exit_hint"));
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    /** Rời màn hình/mất kết nối — trạng thái của một phiên chơi không sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        refreshBanner();
    }
}
