package dev.kage.client.core.world;

import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Luồng catalog vật phẩm (ADR-035, opcode 509/510). KHÔNG có cửa sổ riêng: đây là <b>dữ liệu nền</b>
 * mà các giao diện khác (túi đồ, trang bị, cửa hàng, hòm thư) tra vào để hiện tên vật phẩm thay vì
 * "Vật phẩm #7".
 *
 * <p>Hỏi ĐÚNG MỘT LẦN mỗi phiên, lúc vào thế giới. Cache chưa sống sót qua một lần tắt game (chưa có
 * chỗ lưu xuống đĩa) nên {@code known_version} luôn rỗng ở lần hỏi đầu; cơ chế version của A′ vẫn
 * được tôn trọng đầy đủ để ngày thêm cache trên đĩa thì không phải đổi giao thức hay đổi lớp này.
 *
 * <p>Đăng ký sớm trong {@link WorldScreen#registerPanels()}: mọi luồng khác tra vào nó, và gói 510
 * có thể về trước cả khi người chơi mở cửa sổ nào.
 */
final class ItemCatalogCoordinator implements WindowFlow {

    private final ItemCatalog catalog = new ItemCatalog();
    private final WorldCommandSender sender;

    /** Chạy mỗi lần catalog vừa thay đổi — giao diện đang mở phải vẽ lại tên. */
    private Runnable onCatalogChanged = () -> {};

    ItemCatalogCoordinator(WorldCommandSender sender) {
        this.sender = sender;
    }

    /**
     * @param listener chạy khi catalog vừa đổi nội dung. Nhiều giao diện cùng cần thì chỗ gọi tự gộp
     *     — lớp này giữ đúng một listener, đúng như các coordinator khác.
     */
    void setOnCatalogChanged(Runnable listener) {
        this.onCatalogChanged = listener;
    }

    /** Hỏi catalog. Gọi lúc vào thế giới; gọi lại là vô hại (server trả {@code unchanged}). */
    void request() {
        sender.requestItemCatalog(catalog.knownVersion());
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE) {
            return false;
        }
        if (catalog.adopt(ItemCatalogResponse.parseFrom(frame.payload()))) {
            onCatalogChanged.run();
        }
        return true;
    }

    /** Chưa có catalog — giao diện phải hiện dạng số thay vì bịa tên, xem {@link ItemCatalog}. */
    boolean isEmpty() {
        return catalog.isEmpty();
    }

    Optional<ItemCatalogEntry> byId(long itemTemplateId) {
        return catalog.byId(itemTemplateId);
    }

    Optional<ItemCatalogEntry> byCode(String itemCode) {
        return catalog.byCode(itemCode);
    }

    /** Xem {@link ItemCatalog#nameKey(long)} — rỗng nghĩa là chỗ gọi PHẢI tự hiện dạng số. */
    Optional<String> nameKey(long itemTemplateId) {
        return catalog.nameKey(itemTemplateId);
    }

    EquipmentSlot equipSlot(long itemTemplateId) {
        return catalog.equipSlot(itemTemplateId);
    }

    /**
     * KHÔNG dọn catalog khi rời màn hình: nó là dữ liệu tĩnh của server, không phải trạng thái phiên
     * chơi. Giữ lại đúng bằng cách bỏ trống {@link WindowFlow#reset()}.
     */
    @Override
    public void reset() {
        // Cố ý để trống — xem javadoc.
    }
}
