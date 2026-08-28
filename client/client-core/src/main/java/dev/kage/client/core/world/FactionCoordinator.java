package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.FactionPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectFactionResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link FactionController} (trạng thái), {@link FactionPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành bước chọn hệ phái Lv10 chạy được (2026-08-27) — cùng hình
 * dạng {@link GuildCoordinator}/{@link DungeonCoordinator}, và cùng lý do tồn tại:
 * {@code WorldScreen} đang ở ĐÚNG trần {@code CyclomaticComplexity} của PMD.
 *
 * <p>Cặp opcode 206/207 chưa từng được client dùng trước hôm nay, dù {@code SelectFactionUseCase}
 * phía server đã xong từ Phase 8.
 *
 * <p><b>Lớp này nhận cả {@code Frame} chứ không chỉ {@code (opcode, payload)}</b> như mọi
 * coordinator khác, và đó là điểm mấu chốt chứ không phải tiện tay: server KHÔNG bao giờ điền
 * {@code SelectFactionResponse.error_key} — mọi thất bại về qua {@code ErrorResponse} (opcode 5),
 * một gói DÙNG CHUNG cho mọi luồng. Thứ duy nhất nói được "lỗi này là lỗi của yêu cầu đổi hệ vừa
 * gửi" là {@code related_sequence_id}, và nó chỉ đọc được từ header của khung tin
 * ({@link #pendingSequenceId}).
 *
 * <p>Cùng con số đó là thứ giữ cho việc áp hệ phái mới sau {@code success=true} không thành "client
 * tự phong hệ phái cho mình": gói trả lời không mang hệ phái nào cả, nên câu duy nhất client được
 * phép kết luận là <i>"server đã chấp nhận ĐÚNG yêu cầu số N của tôi"</i> — xem
 * {@link FactionController} về ba giới hạn còn lại của giao thức này.
 */
final class FactionCoordinator {

    /** Không có yêu cầu nào đang treo. 0 an toàn: {@code GameConnection} đánh số từ 1. */
    private static final int NO_PENDING = 0;

    private final FactionController controller = new FactionController();
    private final FactionPanel panel;
    private final WorldCommandSender sender;
    private final CombatFeedbackPresenter notices;
    private final LocalCharacterAppearance appearance;
    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link FactionPanel#setChoices} — dựng đúng một lần, xem constructor. */
    private final List<String> choiceLabels = new ArrayList<>();

    private int pendingSequenceId = NO_PENDING;

    private Faction pendingFaction = Faction.FACTION_UNSPECIFIED;

    /** Đã nhắc "đủ cấp 10 rồi" chưa — nhắc lại mỗi lần lên cấp sau đó là làm phiền. */
    private boolean announcedEligible;

    /**
     * @param notices chữ bay lên trên đầu nhân vật — chỗ báo "đủ cấp rồi, nhấn H". Dùng nó thay vì
     *     nhật ký chat vì lời mời này xuất hiện đúng vào khoảnh khắc vừa lên cấp, lúc mắt người chơi
     *     đang ở nhân vật giữa trận, không ở góc màn hình (cùng lập luận
     *     {@link CombatFeedbackPresenter#showSelfNotice}).
     * @param appearance model glTF + nút chiêu của chính người chơi, hai thứ DUY NHẤT phụ thuộc hệ
     *     phái — xem {@link LocalCharacterAppearance} (kể cả về việc người chơi KHÁC vẫn thấy model
     *     hệ cũ, thứ giao thức không cho client sửa).
     */
    FactionCoordinator(
            FactionPanel panel,
            WorldCommandSender sender,
            CombatFeedbackPresenter notices,
            LocalCharacterAppearance appearance) {
        this.panel = panel;
        this.sender = sender;
        this.notices = notices;
        this.appearance = appearance;
        for (Faction faction : FactionController.SELECTABLE) {
            choiceLabels.add(text(FactionController.nameKey(faction)));
        }
        panel.setChoices(choiceLabels);
        panel.setOnSelect(this::submit);
    }

    /**
     * Cổng mở cửa sổ, ghép hai vế: khung khác có đang nhận chữ gõ không (câu hỏi của
     * {@code WorldScreen}) VÀ nhân vật này có còn được mời chọn hệ không
     * ({@link FactionController#canOffer()}).
     *
     * <p>Vế thứ hai CHỈ để giao diện gọn — nó không phải một luật client tự áp: server vẫn kiểm lại
     * cấp độ và vẫn từ chối nếu client hiểu sai (Điều 24).
     */
    void setOpenGate(BooleanSupplier noTextInputActive) {
        panel.setCanOpen(() -> noTextInputActive.getAsBoolean() && controller.canOffer());
    }

    /** Cấp + hệ phái lúc vừa vào world, từ {@code CharacterSummary} (server đưa, không phải client suy). */
    void onCharacterState(int level, Faction faction) {
        controller.onCharacterState(level, faction);
        announceIfNewlyEligible();
    }

    /** Cấp vừa đổi giữa trận — {@code ExperienceChangedEvent} (ADR-034). */
    void onLevel(int level) {
        controller.onLevel(level);
        announceIfNewlyEligible();
    }

    /** Cửa sổ đang mở hay không — {@code WorldScreen} chặn phím gameplay khi đúng. */
    boolean isOpen() {
        return panel.isOpen();
    }

    void close() {
        panel.close();
    }

    /**
     * Định tuyến một khung tin có thể thuộc luồng chọn hệ phái.
     *
     * @param frame cả header lẫn payload — xem javadoc lớp về vì sao {@code sequenceId} là bắt buộc.
     * @return {@code true} nếu khung đã được xử lý ở đây. {@code ErrorResponse} của luồng KHÁC trả
     *     {@code false}: opcode 5 dùng chung cho cả giao thức, nuốt hết là cướp gói của người khác.
     */
    boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE) {
            return onResponse(frame.header().sequenceId(), SelectFactionResponse.parseFrom(frame.payload()));
        }
        if (opcode == Opcode.OPCODE_ERROR_VALUE) {
            return onError(ErrorResponse.parseFrom(frame.payload()));
        }
        return false;
    }

    /**
     * Gói trả lời cho yêu cầu đổi hệ.
     *
     * <p>{@code sequenceId} không khớp ⇒ bỏ qua HOÀN TOÀN, kể cả khi {@code success=true}: một gói
     * lạc như thế nghĩa là client không biết nó xác nhận hệ phái NÀO, và đoán bừa ở đúng chỗ này là
     * cách nhanh nhất để màn hình hiện một hệ phái mà nhân vật không hề có (Điều 15).
     */
    private boolean onResponse(int sequenceId, SelectFactionResponse response) {
        if (sequenceId != pendingSequenceId || pendingSequenceId == NO_PENDING) {
            return false;
        }
        Faction confirmed = pendingFaction;
        clearPending();
        if (!response.getSuccess()) {
            // Nhánh này chưa từng xảy ra với server hiện tại (nó trả lỗi qua ErrorResponse), nhưng
            // giao thức cho phép — xử lý theo đúng những gì .proto hứa, không theo cài đặt hôm nay.
            panel.setStatus(text(FactionController.errorMessageKey(response.getErrorKey())));
            return true;
        }
        controller.onSelectionConfirmed(confirmed);
        appearance.applyFaction(confirmed);
        panel.setStatus(text("faction.notice.changed"));
        panel.close();
        notices.showSelfNotice("faction.notice.changed", FloatingCombatText.LEVEL_UP);
        return true;
    }

    /** Lỗi CHUNG của cả giao thức — chỉ nhận cái nào nói rõ nó thuộc về yêu cầu đang treo ở đây. */
    private boolean onError(ErrorResponse error) {
        if (pendingSequenceId == NO_PENDING || error.getRelatedSequenceId() != pendingSequenceId) {
            return false;
        }
        clearPending();
        panel.setStatus(text(FactionController.errorMessageKey(error.getErrorKey())));
        return true;
    }

    /**
     * @param choiceIndex chỉ số trong {@link FactionController#SELECTABLE} — panel không biết
     *     {@code Faction} là gì, xem {@link FactionPanel#setOnSelect}.
     */
    private void submit(int choiceIndex) {
        if (choiceIndex < 0 || choiceIndex >= FactionController.SELECTABLE.size()) {
            return; // panel và danh sách lệch nhau — không gửi một hệ phái đoán bừa.
        }
        pendingFaction = FactionController.SELECTABLE.get(choiceIndex);
        pendingSequenceId = sender.selectFaction(pendingFaction);
        panel.setStatus(text("faction.status.sending"));
    }

    private void clearPending() {
        pendingSequenceId = NO_PENDING;
        pendingFaction = Faction.FACTION_UNSPECIFIED;
    }

    /** Nói ra lời mời đúng MỘT lần, ngay khi nó vừa trở nên đúng. */
    private void announceIfNewlyEligible() {
        if (announcedEligible || !controller.canOffer()) {
            return;
        }
        announcedEligible = true;
        notices.showSelfNotice("faction.hint.open", FloatingCombatText.LEVEL_UP);
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    /** Rời màn hình/mất kết nối — trạng thái của một phiên chơi không sống sót sang phiên sau. */
    void reset() {
        controller.clear();
        clearPending();
        announcedEligible = false;
        panel.setStatus("");
        panel.close();
    }
}
