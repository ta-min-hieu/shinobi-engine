package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.InventoryPanel;
import dev.kage.client.core.ui.hud.ScrollingList;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DismantleItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetJunkPreviewRequest;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.ItemUseEffect;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SetItemLockCommand;
import dev.kage.protocol.SortInventoryCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.UseItemResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link InventoryController} (trạng thái), {@link InventoryPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành túi đồ + trang bị chạy được (ADR-035, opcode 501-508) —
 * cùng hình dạng {@link MailCoordinator}.
 *
 * <p>Tra vào {@link ItemCatalogCoordinator} để hiện TÊN + Ô TRANG BỊ của mỗi item — chưa có catalog
 * thì hiện dạng số ("Vật phẩm #7"), KHÔNG bịa (Điều 24, xem javadoc {@link ItemCatalog}), và vì lý
 * do tương tự nút Mặc cũng ẨN khi chưa có catalog: không biết item có mặc được không thì không đoán.
 * Tra vào {@link GoldCoordinator} để hiện số vàng cạnh tiêu đề — túi đồ là chỗ ĐẦU TIÊN dùng tới nó.
 *
 * <p>Cường hoá (opcode 505/506, GAMEPLAY.md §7.1) cũng đi qua đây — xem {@link #onEnhanceSelected}
 * về việc vì sao lệnh này do chính túi đồ gửi trong khi Bán/Giao dịch thì không.
 *
 * <p><b>Luật một dòng: hỏi lại server sau MỌI lệnh đổi trạng thái</b> (xem {@link #settle}), cùng
 * {@link MailCoordinator}/{@link GuildCoordinator}: client không tự cộng/trừ item hay đổi map trang
 * bị theo suy đoán, kể cả khi lệnh vừa gửi trả OK.
 */
final class InventoryCoordinator implements WindowFlow {

    /**
     * Gộp 2 phụ thuộc "dữ liệu nền" (không phải mạng, không phải panel) để constructor không vượt
     * ngưỡng {@code ExcessiveParameterList} của PMD (config/pmd, minimum=5) — cùng lý do
     * {@link PlayContext} gộp {@code EcsWorld}+{@code InputMapper}.
     */
    record DataSources(
            ItemCatalogCoordinator itemCatalog,
            GoldCoordinator gold,
            ChatCoordinator notices,
            AutoPotion.Vitals vitals) {}

    /**
     * 9 slot trang bị THẬT (7 gốc + phi kiếm ADR-070, linh thú ADR-072) — loại
     * {@code EQUIPMENT_SLOT_UNSPECIFIED} (sentinel, không dùng cho lệnh thật).
     */
    private static final List<EquipmentSlot> REAL_SLOTS = List.of(
            EquipmentSlot.EQUIPMENT_SLOT_WEAPON,
            EquipmentSlot.EQUIPMENT_SLOT_ARMOR,
            EquipmentSlot.EQUIPMENT_SLOT_HELMET,
            EquipmentSlot.EQUIPMENT_SLOT_GLOVE,
            EquipmentSlot.EQUIPMENT_SLOT_BOOT,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_1,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2,
            EquipmentSlot.EQUIPMENT_SLOT_MOUNT,
            EquipmentSlot.EQUIPMENT_SLOT_PET,
            EquipmentSlot.EQUIPMENT_SLOT_ARTIFACT);

    private final InventoryController controller = new InventoryController();

    /** ADR-156 — tự uống đan, đọc túi đồ của luồng này; {@code WorldScreen} gọi {@code update} mỗi khung. */
    private final AutoPotion autoPotion;

    AutoPotion autoPotion() {
        return autoPotion;
    }

    private final InventoryPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final GoldCoordinator gold;

    /** Kênh báo "vừa nhặt được gì" — xem {@link #onItemGranted}. */
    private final ChatCoordinator notices;

    private final I18NBundle i18n = KageI18n.load();

    /** Ghép chuỗi ô chi tiết vật phẩm — tách ra 2026-09-07, xem javadoc {@link InventoryItemText}. */
    private final InventoryItemText itemText;

    /**
     * Người chơi bấm Bán — {@code WorldScreen} nối vào {@code ShopCoordinator}. Túi đồ KHÔNG tự gửi
     * {@code SellItemCommand}: khoá idempotency của lệnh Bán (ADR-025) do {@code ShopController}
     * giữ, và một lệnh phải có ĐÚNG một chủ. Túi đồ chỉ biết {@code inventory_item.id} — thứ mà
     * cửa hàng cần và không có cách nào tự lấy.
     */
    private LongConsumer onSellRequested = itemId -> {};

    /**
     * Người chơi bấm Giao dịch — {@code WorldScreen} nối vào {@code TradeCoordinator}, cùng lý do
     * {@link #onSellRequested}: túi đồ chỉ biết {@code inventory_item.id}, còn phiên giao dịch và
     * khoá idempotency của nó thuộc về luồng giao dịch.
     */
    private LongConsumer onOfferRequested = itemId -> {};

    /** Có đang ở trong một phiên giao dịch không — quyết định có bày nút Giao dịch hay không. */
    private BooleanSupplier inTrade = () -> false;

    /**
     * MÁU TỐI ĐA mới sau khi đổi trang bị (ADR-043) — {@code WorldScreen} nối sang
     * {@code CombatHudController}. Đi qua một consumer thay vì cầm thẳng HUD vì hàm dựng lớp này đã
     * chạm trần 4 tham số của Checkstyle, và vì đúng khuôn {@link #onSellRequested}/
     * {@link #onOfferRequested}: túi đồ báo ra một sự kiện, ai quan tâm thì tự đăng ký.
     */
    private IntConsumer onMaxHpChanged = maxHp -> {};

    /** Đệm dựng sẵn cho {@link InventoryPanel#setItems} — chỉ đổi khi server vừa trả lời. */
    private final List<InventoryPanel.ItemRow> itemRows = new ArrayList<>();

    /** Đệm dựng sẵn cho {@link InventoryPanel#setEquipment} — LUÔN đúng 7 dòng. */
    private final List<InventoryPanel.EquipmentRow> equipmentRows = new ArrayList<>();

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím I — cổng phím CHUNG của mọi cửa sổ, xem
     *     {@link InventoryPanel#setCanOpen}.
     */
    InventoryCoordinator(
            InventoryPanel panel, WorldCommandSender sender, DataSources dataSources, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = dataSources.itemCatalog();
        this.itemText = new InventoryItemText(
                itemTemplateId -> itemCatalog.byId(itemTemplateId).orElse(null), this::itemNameOrFallback);
        this.gold = dataSources.gold();
        this.notices = dataSources.notices();
        this.autoPotion =
                new AutoPotion(dataSources.vitals(), controller::items, itemCatalog::useEffect, sender::useItem);
        panel.setCanOpen(canOpen);
        panel.setOnRefresh(this::onOpened);
        panel.setOnSelect(this::onSelect);
        panel.setOnEquipSelected(this::onEquipSelected);
        panel.setOnUseSelected(this::onUseSelected);
        panel.setOnSellSelected(this::onSellSelected);
        // ADR-097 — kết quả về DailyCoordinator (báo chat + làm mới túi).
        panel.setOnLockSelected(this::onLockSelected);
        // ADR-170 — bán rác: hỏi danh sách sẽ bán; JunkSaleCoordinator mở cửa sổ khi có câu trả lời.
        panel.setOnJunkSelected(() -> sender.sendReadRequest(
                Opcode.OPCODE_GET_JUNK_PREVIEW_REQUEST_VALUE, GetJunkPreviewRequest.getDefaultInstance()));
        // ADR-160 — sắp xếp túi; kết quả về BagActionsCoordinator (báo chat + hỏi lại túi).
        panel.setOnSortSelected(() -> sender.sendReadRequest(
                Opcode.OPCODE_SORT_INVENTORY_COMMAND_VALUE, SortInventoryCommand.getDefaultInstance()));
        panel.setOnDismantleSelected(() -> unlockedSelection()
                .ifPresent(item -> sender.sendReadRequest(
                        Opcode.OPCODE_DISMANTLE_ITEM_COMMAND_VALUE,
                        DismantleItemCommand.newBuilder()
                                .setItemId(item.itemId())
                                .build())));
        panel.setOnOfferSelected(this::onOfferSelected);
        panel.setOnEnhanceSelected(this::onEnhanceSelected);
        panel.setOnToggleProtection(this::onToggleProtection);
        panel.setProtection(controller.useProtection());
        panel.setOnUnequip(this::onUnequip);
        panel.setStatus(text("inventory.hint.open"));
        panel.setGold(goldText());
        itemCatalog.addCatalogListener(this::refreshView);
        gold.addGoldListener(amount -> panel.setGold(goldText()));
        refreshView();
    }

    /** ADR-176 — id món đang chọn trong túi (rỗng nếu chưa chọn); lệnh {@code /dungnhieu} dùng món này. */
    Optional<Long> selectedItemId() {
        return controller.selected().map(InventoryController.Item::itemId);
    }

    /** Xem {@link #onSellRequested} — {@code WorldScreen} nối lời gọi này sang cửa hàng. */
    void setOnSellRequested(LongConsumer listener) {
        this.onSellRequested = listener;
    }

    /**
     * Xem {@link #onOfferRequested} — {@code WorldScreen} nối sang luồng giao dịch.
     *
     * @param inTrade nguồn chân lý về "đang ở phiên giao dịch nào không", đọc từ chính luồng đó
     */
    void setOnOfferRequested(LongConsumer listener, BooleanSupplier inTrade) {
        this.onOfferRequested = listener;
        this.inTrade = inTrade;
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            controller.onInventory(ListInventoryResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE) {
            onEquipSettled(EquipItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE) {
            onUnequipSettled(UnequipItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE) {
            onSellSettled(SellItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE) {
            onEnhanceSettled(EnhanceItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_USE_ITEM_RESPONSE_VALUE) {
            onUseSettled(UseItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE) {
            onItemGranted(ItemGrantedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_ERROR_VALUE) {
            return onEnhanceRejected(ErrorResponse.parseFrom(payload));
        }
        return false;
    }

    /**
     * Kết quả Mặc. {@code EquipItemResponse} KHÔNG có enum outcome (khác {@code ClaimMailOutcome}) —
     * chỉ {@code bool success} + {@code error_key} tự do đã LÀ khoá i18n (NETWORK.md §107), xem
     * javadoc {@link InventoryController}.
     */
    private void onEquipSettled(EquipItemResponse response) {
        publishMaxHp(response.getMaxHp());
        settle(
                response.getSuccess()
                        ? "inventory.notice.equipped"
                        : InventoryController.errorMessageKey(response.getErrorKey())
                                .orElse("inventory.error.unknown"));
    }

    private void onUnequipSettled(UnequipItemResponse response) {
        publishMaxHp(response.getMaxHp());
        settle(
                response.getSuccess()
                        ? "inventory.notice.unequipped"
                        : InventoryController.errorMessageKey(response.getErrorKey())
                                .orElse("inventory.error.unknown"));
    }

    /**
     * ADR-043 — máu tối đa phụ thuộc trang bị từ ADR-036, nên mọi lần mặc/gỡ/tôi luyện thành công
     * đều đổi nó. Trước gói này, vạch máu kẹt ở con số lúc VÀO WORLD cho tới lần hồi sinh đầu tiên:
     * mặc trọn bộ +18 máu mà HUD vẫn ghi {@code 100 / 100} (playtest 2026-09-05).
     *
     * <p>{@code maxHp <= 0} = server không gửi (thao tác thất bại, hoặc server cũ) ⇒ giữ nguyên,
     * không đoán — cùng khuôn {@code CombatHudController.onEnterWorld}/{@code onRespawn}.
     */
    private void publishMaxHp(int maxHp) {
        if (maxHp > 0) {
            onMaxHpChanged.accept(maxHp);
        }
    }

    /** {@code WorldScreen} nối vào {@code CombatHudController} — xem {@link #publishMaxHp}. */
    void setOnMaxHpChanged(IntConsumer listener) {
        this.onMaxHpChanged = listener;
    }

    /**
     * Kết quả Bán. {@code ShopCoordinator} cũng nhận gói này và nói kết quả ra khung cửa hàng —
     * {@link PanelHub#dispatch} hỏi MỌI luồng nên không ai giấu gói của ai. Nói lại ở đây là CẦN,
     * không phải thừa: người chơi bấm Bán từ túi đồ thì khung cửa hàng thường đang đóng.
     *
     * <p>Bảng khoá i18n mượn thẳng của {@link ShopController} thay vì chép lại: cùng một
     * {@code SellOutcome} phải đọc ra cùng một câu, dù bấm Bán từ đâu.
     */
    private void onSellSettled(SellItemResponse response) {
        settle(ShopController.sellMessageKey(response.getOutcome()).orElse("shop.error.unknown"));
    }

    /**
     * Bấm Bán trên item đang chọn. Bán ĐÚNG MỘT món mỗi lần bấm (xem {@code inventory.action.sell})
     * — túi đồ không có ô nhập số lượng, và bán nhầm cả chồng đồ là thứ không hoàn tác được.
     */
    private void onSellSelected() {
        unlockedSelection().ifPresent(item -> onSellRequested.accept(item.itemId()));
    }

    /**
     * ADR-159 — món đang chọn, trừ khi nó đang khoá: khi đó báo ngay "mở khoá trước" thay vì gửi một lệnh chắc chắn bị
     * server từ chối bằng câu của luật khác ("đang mặc"). Server vẫn chặn độc lập — đây chỉ là lời nói rõ hơn.
     */
    private Optional<InventoryController.Item> unlockedSelection() {
        Optional<InventoryController.Item> selected = controller.selected();
        if (selected.isPresent() && controller.isLocked(selected.get().itemId())) {
            panel.setStatus(text("item_lock.blocked"));
            return Optional.empty();
        }
        return selected;
    }

    /** ADR-159 — đảo trạng thái khoá của món đang chọn; kết quả về {@code BagActionsCoordinator}. */
    private void onLockSelected() {
        controller
                .selected()
                .ifPresent(item -> sender.sendReadRequest(
                        Opcode.OPCODE_SET_ITEM_LOCK_COMMAND_VALUE,
                        SetItemLockCommand.newBuilder()
                                .setItemId(item.itemId())
                                .setLocked(!controller.isLocked(item.itemId()))
                                .build()));
    }

    /** Bấm Giao dịch trên item đang chọn — đưa ĐÚNG MỘT món vào lời chào, cùng lý do nút Bán. */
    private void onOfferSelected() {
        unlockedSelection().ifPresent(item -> onOfferRequested.accept(item.itemId()));
    }

    /**
     * Bấm Cường hoá trên item đang chọn (GAMEPLAY.md §7.1).
     *
     * <p>Khác nút Bán và nút Giao dịch, lệnh này do CHÍNH túi đồ gửi: {@code EnhanceItemCommand}
     * không thuộc luồng nào khác, nên khoá idempotency của nó (ADR-025) ở đây là đúng chỗ — một lệnh
     * phải có ĐÚNG một chủ.
     */
    private void onEnhanceSelected() {
        controller.selected().ifPresent(item -> {
            String idempotencyKey = controller.beginEnhance(item.itemId());
            controller.onEnhanceSent(sender.enhanceItem(item.itemId(), controller.useProtection(), idempotencyKey));
        });
    }

    /**
     * Server từ chối lệnh Cường hoá đang treo bằng {@code ErrorResponse} thay vì
     * {@code EnhanceItemResponse} — hiện chỉ có một ca: món đang được chào trong giao dịch (quyết
     * định chủ dự án 2026-09-29, {@code error.item_in_trade}); thêm giá trị vào {@code EnhanceOutcome}
     * là đổi protocol nên server đi đường lỗi chung.
     *
     * @return {@code false} nếu gói không thuộc lệnh Cường hoá đang treo — để luồng khác (hoặc
     *     {@code WorldScreen#onUnclaimedError}) nhận
     */
    private boolean onEnhanceRejected(ErrorResponse error) {
        if (!controller.isPendingEnhance(error.getRelatedSequenceId())) {
            return false;
        }
        controller.onEnhanceSettled();
        settle(InventoryController.errorMessageKey(error.getErrorKey()).orElse("inventory.error.unknown"));
        return true;
    }

    /** Bấm "Dùng" trên item đang chọn (ADR-046 D5) — đan dược. */
    private void onUseSelected() {
        controller.selected().ifPresent(item -> sender.useItem(item.itemId()));
    }

    /**
     * Kết quả Dùng. KHÔNG đụng tới thanh máu ở đây: máu mới tới bằng {@code HealthChangedEvent}
     * (opcode 408) do {@code CombatHudController} xử lý — gói này chỉ nói món đã tiêu hay chưa.
     * Hỏi lại túi đồ vì số lượng vừa đổi (inventory.proto không có event đẩy "túi vừa đổi").
     */
    private void onUseSettled(UseItemResponse response) {
        settle(
                response.getSuccess()
                        ? "inventory.notice.used"
                        : InventoryController.errorMessageKey(response.getErrorKey())
                                .orElse("inventory.error.unknown"));
    }

    /**
     * Gạt công tắc Bảo Vệ — chỉ đổi ý định cho LẦN BẤM SAU, không gửi gì đi.
     *
     * <p>Lời giải thích ("tiêu 1 bùa để chặn rớt cấp") nói ra ở DÒNG TRẠNG THÁI đúng lúc người chơi
     * vừa bật, thay vì một dòng chữ thường trực trong khung: khung túi đồ đã cao sát mép màn hình
     * (xem {@link InventoryPanel#preferredHeight()}), và một câu hướng dẫn chỉ có nghĩa ngay lúc
     * quyết định thì không đáng chiếm chỗ vĩnh viễn.
     */
    private void onToggleProtection() {
        boolean on = controller.toggleProtection();
        panel.setProtection(on);
        panel.setStatus(text(on ? "inventory.hint.protection" : "inventory.hint.protection_off"));
    }

    /**
     * Kết quả một lần cường hoá. Xoá khoá idempotency TRƯỚC khi hỏi lại túi (cùng
     * {@code ShopController#onBuySettled}: lệnh đã có kết luận thì lần bấm sau là một canh bạc mới).
     *
     * <p>Vàng đã trừ KHÔNG cập nhật ở đây: server phát {@code GoldChangedEvent} riêng sau khi commit
     * (ADR-035 Decision-3, {@code GatewayBootstrap}) và {@link GoldCoordinator} là chỗ duy nhất nghe
     * nó — client không tự trừ theo bảng chi phí nó không có.
     */
    private void onEnhanceSettled(EnhanceItemResponse response) {
        publishMaxHp(response.getMaxHp());
        controller.onEnhanceSettled();
        settle(InventoryController.enhanceMessageKey(response.getOutcome()).orElse("inventory.error.unknown"));
    }

    /**
     * Server vừa cộng THẬT một vật phẩm vào túi (nhặt đồ, thưởng nhiệm vụ, mua hàng) — gói này chỉ
     * được gửi SAU khi item đã vào túi thành công (inventory.proto), không phải "quái vừa rơi gì".
     *
     * <p>Trước hôm nay client bỏ qua nó trong im lặng: giết quái xong không có một dòng nào báo, và
     * túi đồ đang mở vẫn hiện ảnh chụp cũ. Hai việc ở đây sửa đúng hai chuyện đó.
     *
     * <p>Chỉ hỏi lại túi khi cửa sổ ĐANG MỞ: một lần rơi đồ có thể sinh nhiều gói liên tiếp
     * ({@code GrantLootUseCase} trả cả danh sách), và hỏi lại toàn bộ túi cho một cửa sổ không ai
     * nhìn là tốn băng thông đúng lúc đang đánh nhau. Lần mở sau tự hỏi lại (xem {@link #onOpened}).
     */
    private void onItemGranted(ItemGrantedEvent event) {
        notices.showSystemNotice(itemGrantedText(event));
        if (panel.isOpen()) {
            sender.listInventory();
        }
    }

    /** "Nhận được Kunai x2" — tên tra qua catalog, chưa có thì hiện dạng số, KHÔNG bịa (Điều 24). */
    private String itemGrantedText(ItemGrantedEvent event) {
        String name = itemCatalog
                .nameKey(event.getItemTemplateId())
                .map(this::text)
                .orElseGet(() -> format("inventory.label.raw_item", event.getItemTemplateId()));
        return format2("inventory.notice.item_granted", name, event.getQuantity());
    }

    /** Nói ra kết quả một lệnh, rồi hỏi lại server — cùng {@link MailCoordinator#settle}. */
    private void settle(String messageKey) {
        panel.setStatus(text(messageKey));
        sender.listInventory();
    }

    /** Người chơi vừa mở cửa sổ (hoặc bấm Làm mới) — hỏi lại server, nguồn chân lý duy nhất. */
    private void onOpened() {
        sender.listInventory();
    }

    private void onSelect(long itemId) {
        controller.select(itemId);
        refreshDetail();
    }

    /**
     * Bấm Mặc trên item ĐANG CHỌN. Slot suy từ catalog, không phải người chơi chọn tay — mỗi item
     * mặc được đúng MỘT slot cố định (item_template.equip_slot).
     */
    private void onEquipSelected() {
        controller.selected().ifPresent(item -> {
            EquipmentSlot slot = itemCatalog.equipSlot(item.itemTemplateId());
            if (slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
                // Nút không đáng lẽ hiện ra lúc này (xem refreshDetail) — phòng hờ trạng thái vừa đổi
                // giữa lúc panel vẽ và lúc người chơi bấm (catalog vừa mất do reconnect, chẳng hạn).
                return;
            }
            sender.equipItem(item.itemId(), slot);
        });
    }

    private void onUnequip(int slotNumber) {
        EquipmentSlot slot = EquipmentSlot.forNumber(slotNumber);
        if (slot == null || slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
            return; // số lạ — panel chỉ vọng lại đúng số nó nhận từ refreshEquipment, không nên xảy ra.
        }
        sender.unequipItem(slot);
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống panel. Gọi sau MỖI response tra cứu, không phải mỗi khung. */
    private void refreshView() {
        List<InventoryController.Item> all = controller.items();
        int shown = Math.min(all.size(), ScrollingList.MAX_BUILT_ROWS);
        itemRows.clear();
        for (int index = 0; index < shown; index++) {
            InventoryController.Item item = all.get(index);
            String described = itemText.describe(item);
            itemRows.add(new InventoryPanel.ItemRow(
                    item.itemId(),
                    iconCodeOf(item),
                    controller.isLocked(item.itemId())
                            ? KageI18n.formatOrKey(i18n, "item_lock.row", described) // ADR-159
                            : described));
        }
        panel.setItems(itemRows, capacityText(all.size()), listNotice(all.size(), all.size() - shown));
        refreshEquipment();
        refreshDetail();
    }

    private void refreshEquipment() {
        equipmentRows.clear();
        for (EquipmentSlot slot : REAL_SLOTS) {
            long equippedItemId = controller.equippedItemId(slot);
            String displayText = equippedItemId == 0L
                    ? text("inventory.slot.empty")
                    : controller
                            .byId(equippedItemId)
                            .map(itemText::describe)
                            .orElseGet(() -> format("inventory.label.raw_item", equippedItemId));
            equipmentRows.add(new InventoryPanel.EquipmentRow(
                    slot.getNumber(), text(slotNameKey(slot)), displayText, equippedItemId != 0L));
        }
        panel.setEquipment(equipmentRows);
    }

    /**
     * Chi tiết item đang chọn + có bày nút Mặc hay không.
     *
     * <p>Nút Bán bày ra với MỌI item đang chọn — bán được hay không là việc của server
     * ({@code SELL_OUTCOME_ITEM_NOT_SELLABLE}), client không có bảng giá bán để đoán trước.
     *
     * <p>ẨN nút Mặc khi chưa có catalog (Điều 24: không biết thì không đoán) VÀ khi item đang chọn ĐÃ
     * đang mặc đúng slot đó — bày ra lúc đó là bày một nút chỉ để mặc lại chính nó, vô nghĩa với
     * người chơi dù server vẫn xử lý được (OK, không hỏng).
     */
    private void refreshDetail() {
        Optional<InventoryController.Item> selected = controller.selected();
        boolean selectedLocked =
                selected.map(item -> controller.isLocked(item.itemId())).orElse(false);
        panel.setLockState(selected.isPresent(), selectedLocked);
        controller
                .selected()
                .ifPresentOrElse(
                        item -> panel.showDetail(
                                itemText.describe(item),
                                primaryActionFor(item),
                                new InventoryPanel.Actions(true, inTrade.getAsBoolean(), true)),
                        () -> panel.showDetail(
                                text("inventory.detail.none"),
                                InventoryPanel.PrimaryAction.NONE,
                                new InventoryPanel.Actions(false, false, false)));
    }

    /**
     * "Mặc" hay "Dùng" — hai thứ loại trừ nhau ({@code ItemTemplate} cấm một món vừa mặc được vừa
     * dùng được), nên chúng dùng chung một ô nút (ADR-046 D5).
     *
     * <p>ẨN nút Mặc khi item đang chọn ĐÃ mặc đúng slot đó: bày ra lúc đó là bày một nút chỉ để
     * mặc lại chính nó, vô nghĩa với người chơi dù server vẫn xử lý được (OK, không hỏng).
     */
    private InventoryPanel.PrimaryAction primaryActionFor(InventoryController.Item item) {
        if (itemCatalog.useEffect(item.itemTemplateId()) != ItemUseEffect.ITEM_USE_EFFECT_UNSPECIFIED) {
            return InventoryPanel.PrimaryAction.USE;
        }
        EquipmentSlot slot = itemCatalog.equipSlot(item.itemTemplateId());
        if (slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED || controller.equippedItemId(slot) == item.itemId()) {
            return InventoryPanel.PrimaryAction.NONE;
        }
        return InventoryPanel.PrimaryAction.EQUIP;
    }

    /** Tên đã dịch của vật phẩm; dạng số khi chưa có catalog — client KHÔNG bịa tên (Điều 24). */
    private String itemNameOrFallback(long itemTemplateId) {
        return itemCatalog
                .nameKey(itemTemplateId)
                .map(this::text)
                .orElseGet(() -> format("inventory.label.raw_item", itemTemplateId));
    }

    /** {@code item_template.code} để tra icon (ADR-028) — rỗng khi chưa có catalog, panel để trống. */
    private String iconCodeOf(InventoryController.Item item) {
        return itemCatalog
                .byId(item.itemTemplateId())
                .map(ItemCatalogEntry::getCode)
                .orElse("");
    }

    private String capacityText(int itemCount) {
        return format2("inventory.label.capacity", itemCount, controller.capacity());
    }

    /**
     * Câu nói thêm dưới danh sách — cùng {@link MailCoordinator#listNotice}.
     */
    private String listNotice(int total, int hidden) {
        if (total == 0) {
            return text("inventory.empty");
        }
        return hidden <= 0 ? "" : format("inventory.list.truncated", hidden);
    }

    /** Bảng tra thay switch (trần CyclomaticComplexity của PMD từ khi có 9 ô). */
    private static final Map<EquipmentSlot, String> SLOT_NAME_KEYS = Map.of(
            EquipmentSlot.EQUIPMENT_SLOT_WEAPON, "equipment.slot.weapon",
            EquipmentSlot.EQUIPMENT_SLOT_ARMOR, "equipment.slot.armor",
            EquipmentSlot.EQUIPMENT_SLOT_HELMET, "equipment.slot.helmet",
            EquipmentSlot.EQUIPMENT_SLOT_GLOVE, "equipment.slot.glove",
            EquipmentSlot.EQUIPMENT_SLOT_BOOT, "equipment.slot.boot",
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_1, "equipment.slot.accessory_1",
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2, "equipment.slot.accessory_2",
            EquipmentSlot.EQUIPMENT_SLOT_MOUNT, "equipment.slot.mount",
            EquipmentSlot.EQUIPMENT_SLOT_PET, "equipment.slot.pet",
            EquipmentSlot.EQUIPMENT_SLOT_ARTIFACT, "equipment.slot.artifact");

    private static String slotNameKey(EquipmentSlot slot) {
        // UNSPECIFIED/UNRECOGNIZED không xuất hiện ở đây — REAL_SLOTS đã lọc trước.
        return SLOT_NAME_KEYS.getOrDefault(slot, "inventory.error.unknown");
    }

    private String goldText() {
        return gold.hasAmount() ? format("inventory.label.gold", gold.amount()) : text("inventory.label.gold_unknown");
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object argument) {
        return KageI18n.formatOrKey(i18n, key, argument);
    }

    private String format2(String key, Object first, Object second) {
        return KageI18n.formatOrKey(i18n, key, first, second);
    }

    /** Rời màn hình/mất kết nối — túi của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("inventory.hint.open"));
        panel.setProtection(controller.useProtection());
        refreshView();
    }
}
