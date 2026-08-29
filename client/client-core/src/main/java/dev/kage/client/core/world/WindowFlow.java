package dev.kage.client.core.world;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.wire.PacketCodec;

/**
 * Phần vòng đời GIỐNG NHAU của mọi luồng tính năng phía client (chat, nhóm, bạn bè, bang hội, hòm
 * thư, đấu tay đôi, hệ phái, hầm ngục, nhiệm vụ...): nhận gói từ server, biết mình có đang chiếm
 * bàn phím hay không, đóng lại, và dọn sạch khi rời màn hình.
 *
 * <p>Nhận nguyên {@link PacketCodec.Frame} chứ không phải {@code (opcode, payload)}: thất bại của
 * nhiều lệnh về qua {@code ErrorResponse} DÙNG CHUNG, chỉ nối lại được với yêu cầu đã gửi bằng
 * {@code sequenceId} nằm ở header — {@code FactionCoordinator} đã phải phá thế đồng nhất vì chuyện
 * đó, và mọi luồng thêm sau cũng sẽ cần. Một chữ ký cho tất cả, không có ngoại lệ.
 */
interface WindowFlow {

    /**
     * Định tuyến một gói có thể thuộc luồng này.
     *
     * <p>⚠️ Người gọi ({@link PanelHub#dispatch}) hỏi <b>mọi</b> luồng, không dừng ở luồng đầu tiên
     * trả {@code true}: có gói mang nghĩa với hai luồng cùng lúc — {@code AcceptQuestResponse} vừa
     * cập nhật nhiệm vụ ({@link QuestCoordinator}) vừa đóng khung thoại
     * ({@link DialogueCoordinator}). Dừng sớm là mất đúng một trong hai việc.
     *
     * @return {@code true} nếu gói thuộc luồng này và đã xử lý xong
     */
    boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException;

    /**
     * Luồng này có đang chiếm bàn phím / chờ người chơi đọc hay bấm không.
     *
     * <p>Mặc định {@code false} cho luồng KHÔNG có cửa sổ riêng (hầm ngục, nhiệm vụ — chúng vẽ vào
     * panel của luồng khác).
     */
    default boolean isOpen() {
        return false;
    }

    /** Đóng cửa sổ. Mặc định không làm gì, vì luồng không có cửa sổ thì không có gì để đóng. */
    default void close() {}

    /**
     * Dọn sạch khi rời màn hình — không trạng thái nào được sống sót qua một lần
     * {@code WorldScreen.hide()}, xem {@link DialogueCoordinator#reset()} về hậu quả nếu sót.
     */
    default void reset() {}
}
