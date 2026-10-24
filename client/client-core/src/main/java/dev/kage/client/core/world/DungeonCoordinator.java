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
import dev.kage.protocol.WorldChangeReason;
import dev.kage.protocol.WorldChangedEvent;
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
     * ADR-051 — server vừa đưa nhân vật sang World khác ({@link WorldChangeCoordinator} gọi). Việc DỌN
     * màn hình không nằm ở đây; lớp này chỉ ghi cờ "đang ở hầm ngục", cập nhật dải chữ, và nói ra
     * lý do khi lý do đó không có Response nào khác nói thay — gục trong hầm ngục (D5).
     */
    void onWorldChanged(WorldChangedEvent event) {
        controller.onWorldChanged(event.getWorldKind());
        if (event.getReason() == WorldChangeReason.WORLD_CHANGE_REASON_DEFEATED) {
            announce(Optional.of("dungeon.notice.defeated"));
        } else if (event.getReason() == WorldChangeReason.WORLD_CHANGE_REASON_GM_MOVED) {
            // ADR-052 — bị GM dời (summon/goto): không Response nào nói thay, người chơi phải biết vì sao.
            announce(Optional.of("world.notice.gm_moved"));
        }
        refreshBanner();
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
            EnterDungeonResponse response = EnterDungeonResponse.parseFrom(payload);
            settleEnter(response.getOutcome());
            if (response.getOutcome() == EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK) {
                // ADR-099 — lượt còn hôm nay.
                notices.showSystemNotice(
                        KageI18n.formatOrKey(i18n, "dungeon.notice.entries_left", response.getEntriesLeft()));
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE) {
            settleExit(ExitDungeonResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_DUNGEON_ENTERED_EVENT_VALUE) {
            // Không field nào cần đọc ở lát cắt này — parse để chắc payload hợp lệ, cùng cách
            // GatewayChannelHandler làm với ExitDungeonCommand.
            DungeonEnteredEvent.parser().parseFrom(payload);
            settlePulledIn();
            return true;
        }
        return false;
    }

    /**
     * Nói ra kết quả và ghi trạng thái theo lời server. KHÔNG dọn màn hình nữa (ADR-051 D1): việc đó
     * theo {@code WorldChangedEvent}, gói luôn tới TRƯỚC spawn của World mới — Response thì không
     * được bảo đảm như vậy (ADR-047 "Phát hiện phụ"), nên dọn theo nó từng có thể xoá mất boss.
     */
    private void settleEnter(EnterDungeonOutcome outcome) {
        controller.onEnterOutcome(outcome);
        announce(DungeonController.enterMessageKey(outcome));
        refreshBanner();
    }

    /**
     * ADR-047 — chính người chơi này vừa bị bạn cùng nhóm KÉO vào hầm ngục. Không có lệnh nào của
     * họ đang treo, nên đây là gói ĐẨY chứ không phải response.
     *
     * <p>Từ ADR-051 chỉ còn là dòng báo + cờ trạng thái: dọn màn hình theo {@code WorldChangedEvent}
     * (D4 — 1504 vẫn được phát cho client cũ).
     *
     * <p>{@code initiator_character_id} chưa được hiện ra chữ — client chưa có đường tra tên từ id
     * cho người không nằm trong tầm nhìn. Xem javadoc message trong {@code dungeon.proto}.
     */
    private void settlePulledIn() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);
        announce(Optional.of("dungeon.notice.pulled_in"));
        refreshBanner();
    }

    private void settleExit(ExitDungeonOutcome outcome) {
        controller.onExitOutcome(outcome);
        announce(DungeonController.exitMessageKey(outcome));
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
