package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import dev.kage.protocol.BuyListingCommand;
import dev.kage.protocol.BuyListingResponse;
import dev.kage.protocol.CreateListingCommand;
import dev.kage.protocol.CreateListingResponse;
import dev.kage.protocol.DepositItemCommand;
import dev.kage.protocol.DepositItemResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListMarketRequest;
import dev.kage.protocol.ListMarketResponse;
import dev.kage.protocol.ListStorageRequest;
import dev.kage.protocol.ListStorageResponse;
import dev.kage.protocol.MarketListingView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetActiveTitleCommand;
import dev.kage.protocol.SetActiveTitleResponse;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.SetTradeOfferResponse;
import dev.kage.protocol.SocketGemCommand;
import dev.kage.protocol.SocketGemResponse;
import dev.kage.protocol.StoredItemView;
import dev.kage.protocol.TradeOfferItemInput;
import dev.kage.protocol.WithdrawItemCommand;
import dev.kage.protocol.WithdrawItemResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bốn đường kinh tế của lô 16 mà trước đó chỉ client có giao diện mới gửi được — kho tông môn
 * (ADR-061, 514–519), khảm Linh Ngọc (ADR-064, 520/521), đeo danh hiệu (ADR-062, 1702/1703), phường
 * thị (ADR-063, 1006–1011).
 *
 * <p>Chạy SAU khi đi bộ: kho chỉ mở khi đứng gần người giữ kho, nên {@code --moveToX} quyết định
 * kết quả {@code TOO_FAR} hay {@code OK} — đúng thứ cần kiểm. Nhận {@code item_template.code} như
 * {@link UseItemScenario}, tra id qua catalog (509/510) và túi đồ (507/508).
 */
final class EconomyScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;
    private List<ItemCatalogEntry> catalog = List.of();

    EconomyScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    void run(ProbeConfig.EconomyPlan plan) throws IOException {
        catalog = loadCatalog();
        for (String raw : plan.depositItems()) {
            deposit(ShopScenario.Line.parse(raw, ShopScenario.ALL));
        }
        for (String raw : plan.withdrawItems()) {
            withdraw(ShopScenario.Line.parse(raw, ShopScenario.ALL));
        }
        for (String step : plan.socketGems()) {
            int arrow = step.indexOf('>');
            if (arrow >= 0) {
                offerInTrade(ShopScenario.Line.parse(step.substring(0, arrow), 1));
            }
            socket(step.substring(arrow + 1));
        }
        if (!plan.setTitle().isEmpty()) {
            setTitle(plan.setTitle());
        }
        for (String pair : plan.listOnMarket()) {
            createListing(pair);
        }
        for (String code : plan.buyFromMarket()) {
            buyCheapest(code);
        }
    }

    private void deposit(ShopScenario.Line line) throws IOException {
        Optional<InventoryItemView> item = findInBag(line.code(), false);
        if (item.isEmpty()) {
            session.note("gửi kho: túi KHÔNG có '" + line.code() + "' chưa mặc — bỏ qua");
            return;
        }
        int quantity = line.quantity() == ShopScenario.ALL ? item.get().getQuantity() : line.quantity();
        connection.send(
                Opcode.OPCODE_DEPOSIT_ITEM_COMMAND_VALUE,
                DepositItemCommand.newBuilder()
                        .setInventoryItemId(item.get().getItemId())
                        .setQuantity(quantity)
                        .build()
                        .toByteArray());
        DepositItemResponse response = DepositItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_DEPOSIT_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("gửi kho " + line.code() + " x" + quantity + ": " + response.getOutcome() + " | "
                + describeStorage(response.getItemsList(), response.getCapacity()));
    }

    private void withdraw(ShopScenario.Line line) throws IOException {
        long templateId = templateId(line.code()).orElse(-1L);
        connection.send(
                Opcode.OPCODE_LIST_STORAGE_REQUEST_VALUE,
                ListStorageRequest.getDefaultInstance().toByteArray());
        ListStorageResponse storage = ListStorageResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_STORAGE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        Optional<StoredItemView> stored = storage.getItemsList().stream()
                .filter(view -> view.getItemTemplateId() == templateId)
                .findFirst();
        if (stored.isEmpty()) {
            session.note("rút kho: kho KHÔNG có '" + line.code() + "' | "
                    + describeStorage(storage.getItemsList(), storage.getCapacity()));
            return;
        }
        int quantity = line.quantity() == ShopScenario.ALL ? stored.get().getQuantity() : line.quantity();
        connection.send(
                Opcode.OPCODE_WITHDRAW_ITEM_COMMAND_VALUE,
                WithdrawItemCommand.newBuilder()
                        .setStoredItemId(stored.get().getStoredItemId())
                        .setQuantity(quantity)
                        .build()
                        .toByteArray());
        WithdrawItemResponse response = WithdrawItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_WITHDRAW_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("rút kho " + line.code() + " x" + quantity + ": " + response.getOutcome() + " | "
                + describeStorage(response.getItemsList(), response.getCapacity()));
    }

    /** {@code pair} dạng {@code maTrangBi:maNgoc}; trang bị đang mặc cũng được (ADR-064). */
    private void socket(String pair) throws IOException {
        String[] parts = pair.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("--socketGems cần dạng trangBi:ngoc, nhận '" + pair + "'");
        }
        Optional<InventoryItemView> target = findInBag(parts[0], true);
        Optional<InventoryItemView> gem = findInBag(parts[1], false);
        if (target.isEmpty() || gem.isEmpty()) {
            session.note("khảm " + pair + ": túi thiếu trang bị hoặc ngọc — bỏ qua");
            return;
        }
        int sequenceId = connection.send(
                Opcode.OPCODE_SOCKET_GEM_COMMAND_VALUE,
                SocketGemCommand.newBuilder()
                        .setTargetItemId(target.get().getItemId())
                        .setGemItemId(gem.get().getItemId())
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        PacketCodec.Frame frame = readReplyOrError(Opcode.OPCODE_SOCKET_GEM_RESPONSE_VALUE, sequenceId);
        if (frame.header().opcode() == Opcode.OPCODE_ERROR_VALUE) {
            ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
            session.note("khảm " + pair + ": BỊ TỪ CHỐI " + error.getErrorKey() + " (" + error.getErrorCode() + ")");
            return;
        }
        SocketGemResponse response = SocketGemResponse.parseFrom(frame.payload());
        session.note(String.format(
                Locale.ROOT,
                "khảm %s: %s hỏngLiênTiếp=%d/%d máuTốiĐa=%d",
                pair,
                response.getOutcome(),
                response.getFailuresInARow(),
                response.getPityAfter(),
                response.getMaxHp()));
    }

    /**
     * Đặt lại lời chào của MÌNH trong phiên giao dịch đang mở (thay hẳn lời chào cũ) — tiền đề cho bước khảm
     * ngay sau nó ({@code --socketGems=ma:soLuong>trangBi:ngoc}, docs/README.md dòng 82).
     */
    private void offerInTrade(ShopScenario.Line line) throws IOException {
        Optional<InventoryItemView> item = findInBag(line.code(), true);
        connection.send(
                Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE,
                GetTradeStatusRequest.getDefaultInstance().toByteArray());
        GetTradeStatusResponse status = GetTradeStatusResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        if (item.isEmpty() || !status.getInTrade()) {
            session.note("chào " + line.code() + ": túi không có hoặc không trong giao dịch nào — bỏ qua");
            return;
        }
        connection.send(
                Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE,
                SetTradeOfferCommand.newBuilder()
                        .setSessionId(status.getSessionId())
                        .addItems(TradeOfferItemInput.newBuilder()
                                .setItemId(item.get().getItemId())
                                .setQuantity(line.quantity()))
                        .build()
                        .toByteArray());
        SetTradeOfferResponse response = SetTradeOfferResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("chào " + line.code() + " x" + line.quantity() + " trong phiên " + status.getSessionId() + ": "
                + response.getOutcome());
    }

    /** Phản hồi {@code opcode}, hoặc {@code ErrorResponse} của ĐÚNG lệnh {@code sequenceId}. */
    private PacketCodec.Frame readReplyOrError(int opcode, int sequenceId) throws IOException {
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            int got = frame.header().opcode();
            boolean ownError = got == Opcode.OPCODE_ERROR_VALUE
                    && ErrorResponse.parseFrom(frame.payload()).getRelatedSequenceId() == sequenceId;
            if (got == opcode || ownError) {
                return frame;
            }
        }
        throw new IOException("Không thấy opcode " + opcode + " sau " + MAX_FRAMES_TO_SKIP + " frame");
    }

    private void setTitle(String achievementCode) throws IOException {
        connection.send(
                Opcode.OPCODE_SET_ACTIVE_TITLE_COMMAND_VALUE,
                SetActiveTitleCommand.newBuilder()
                        .setAchievementCode(achievementCode)
                        .build()
                        .toByteArray());
        SetActiveTitleResponse response = SetActiveTitleResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_SET_ACTIVE_TITLE_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("đeo danh hiệu " + achievementCode + ": " + response.getOutcome() + " đangĐeo='"
                + response.getActiveTitleCode() + "'");
    }

    /** {@code pair} dạng {@code maVatPham:giaTong}; treo cả chồng đầu tiên chưa mặc. */
    private void createListing(String pair) throws IOException {
        String[] parts = pair.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("--listOnMarket cần dạng ma:gia, nhận '" + pair + "'");
        }
        Optional<InventoryItemView> item = findInBag(parts[0], false);
        if (item.isEmpty()) {
            session.note("treo phường thị: túi KHÔNG có '" + parts[0] + "' — bỏ qua");
            return;
        }
        connection.send(
                Opcode.OPCODE_CREATE_LISTING_COMMAND_VALUE,
                CreateListingCommand.newBuilder()
                        .setInventoryItemId(item.get().getItemId())
                        .setQuantity(item.get().getQuantity())
                        .setPrice(Long.parseLong(parts[1]))
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        CreateListingResponse response = CreateListingResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_CREATE_LISTING_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note("treo " + parts[0] + " x" + item.get().getQuantity() + " giá " + parts[1] + ": "
                + response.getOutcome());
    }

    private void buyCheapest(String code) throws IOException {
        long templateId = templateId(code).orElse(-1L);
        connection.send(
                Opcode.OPCODE_LIST_MARKET_REQUEST_VALUE,
                ListMarketRequest.newBuilder()
                        .setItemTemplateId(templateId)
                        .build()
                        .toByteArray());
        ListMarketResponse market = ListMarketResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_MARKET_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        Optional<MarketListingView> cheapest = market.getListingsList().stream()
                .filter(listing -> listing.getItemTemplateId() == templateId)
                .min((left, right) -> Long.compare(left.getPrice(), right.getPrice()));
        if (cheapest.isEmpty()) {
            session.note("mua phường thị: không có dòng nào bán '" + code + "' (phí " + market.getFeePercent() + "%)");
            return;
        }
        connection.send(
                Opcode.OPCODE_BUY_LISTING_COMMAND_VALUE,
                BuyListingCommand.newBuilder()
                        .setListingId(cheapest.get().getListingId())
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        BuyListingResponse response = BuyListingResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_BUY_LISTING_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note(String.format(
                Locale.ROOT,
                "mua %s x%d giá %d của %s: %s",
                code,
                cheapest.get().getQuantity(),
                cheapest.get().getPrice(),
                cheapest.get().getSellerName(),
                response.getOutcome()));
    }

    private String describeStorage(List<StoredItemView> items, int capacity) {
        StringBuilder text =
                new StringBuilder("kho ").append(items.size()).append('/').append(capacity);
        for (StoredItemView view : items) {
            text.append(' ')
                    .append(codeOf(view.getItemTemplateId()))
                    .append('x')
                    .append(view.getQuantity());
        }
        return text.toString();
    }

    private String codeOf(long templateId) {
        return catalog.stream()
                .filter(entry -> entry.getId() == templateId)
                .map(ItemCatalogEntry::getCode)
                .findFirst()
                .orElse("#" + templateId);
    }

    private Optional<Long> templateId(String code) {
        return catalog.stream()
                .filter(entry -> code.equals(entry.getCode()))
                .findFirst()
                .map(ItemCatalogEntry::getId);
    }

    /**
     * Chồng đầu tiên của {@code code} trong túi. {@code allowEquipped=false} bỏ qua món đang mặc —
     * gửi kho/treo bán món đang mặc là bị từ chối, không phải thứ đang muốn đo.
     */
    private Optional<InventoryItemView> findInBag(String code, boolean allowEquipped) throws IOException {
        Optional<Long> wanted = templateId(code);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        connection.send(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.getDefaultInstance().toByteArray());
        ListInventoryResponse inventory = ListInventoryResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        List<Long> equippedIds = inventory.getEquippedList().stream()
                .map(slot -> slot.getItemId())
                .toList();
        return inventory.getItemsList().stream()
                .filter(item -> item.getItemTemplateId() == wanted.get())
                .filter(item -> allowEquipped || !equippedIds.contains(item.getItemId()))
                .findFirst();
    }

    private List<ItemCatalogEntry> loadCatalog() throws IOException {
        connection.send(
                Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE,
                GetItemCatalogRequest.getDefaultInstance().toByteArray());
        return ItemCatalogResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload())
                .getEntriesList();
    }
}
