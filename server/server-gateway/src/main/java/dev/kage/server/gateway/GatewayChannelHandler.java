package dev.kage.server.gateway;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.account.api.AccountFacade;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterLimitExceededException;
import dev.kage.character.application.CharacterNotFoundException;
import dev.kage.character.application.DuplicateCharacterNameException;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.ShopListingView;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.shop.api.ShopFacade;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý packet hệ thống + Character trên Netty EventLoop — nhưng CHỈ phần rẻ (decode, validate
 * rẻ, enqueue); phần chạm DB/Redis đẩy sang {@code authExecutor} (Virtual Thread, SERVER.md §2).
 * Từ Phase 3 trở đi, Command chạm gameplay/DB PHẢI enqueue vào server-world hoặc Virtual Thread,
 * KHÔNG BAO GIỜ chạy inline chặn EventLoop.
 *
 * <p>{@code handleConnect} gọi {@link AccountFacade} (ADR-017 ✅ Accepted) — đăng nhập thật bằng
 * username, tự tạo account nếu chưa có (dự án cá nhân, chưa cần mật khẩu ở bước này).
 *
 * <p>Handler này được {@code new} một lần MỖI kết nối (xem {@code GatewayServer.start()}), nên
 * vốn dĩ đã là state per-connection — {@link #boundAccountId} tận dụng đúng điều đó: sau đăng
 * nhập thành công, accountId gắn vào CONNECTION này, không phải đọc lại từ packet ở Command sau
 * (đúng tinh thần "characterId/accountId từ session, không từ packet" — SECURITY.md §4).
 */
final class GatewayChannelHandler extends SimpleChannelInboundHandler<PacketCodec.Frame> {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayChannelHandler.class);

    /**
     * Bảng tra opcode -> handler thay if-else dài (PMD CyclomaticComplexity — mỗi opcode mới thêm
     * vào {@code channelRead0} lại tăng độ phức tạp, {@code UseSkillCommand} là giọt nước tràn ly).
     */
    private interface PacketHandler {
        void handle(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
                throws InvalidProtocolBufferException;
    }

    // NETWORK.md §13 (đề xuất, chưa verify): MoveCommand ≤ tick rate (30/giây).
    private static final int MAX_MOVE_COMMANDS_PER_SECOND = 30;

    private final AccountFacade accountFacade;
    private final CharacterFacade characterFacade;
    private final InventoryFacade inventoryFacade;
    private final ShopFacade shopFacade;
    private final WorldGateway worldGateway;
    private final Executor authExecutor;
    // Per-connection (handler đã là state per-connection, xem javadoc lớp) — không cần đồng bộ
    // hoá, chỉ được đọc/ghi trên Netty EventLoop của CHÍNH connection này.
    private final RateLimiter moveCommandRateLimiter =
            new RateLimiter(MAX_MOVE_COMMANDS_PER_SECOND, Duration.ofSeconds(1));

    // handleConnect/handleSelectCharacter set giá trị này từ Virtual Thread; channelRead0 của
    // packet TIẾP THEO có thể chạy trên EventLoop trước khi Virtual Thread đó xong (Netty không
    // chờ) — cần volatile để đảm bảo visibility giữa 2 thread.
    private volatile Long boundAccountId;
    private volatile Long boundCharacterId;

    // Bind sau khi field khác đã gán xong (field initializer chạy tuần tự trong <init>) — method
    // reference chỉ CHỤP this, không gọi ngay, nên thứ tự khai báo so với constructor không quan trọng.
    // Map.ofEntries thay Map.of (11 cặp vượt quá 10 overload cố định của Map.of) — cùng ngữ nghĩa.
    private final Map<Integer, PacketHandler> handlers = Map.ofEntries(
            Map.entry(Opcode.OPCODE_PING_VALUE, this::handlePing),
            Map.entry(Opcode.OPCODE_CONNECT_REQUEST_VALUE, this::handleConnect),
            Map.entry(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, this::handleListCharacters),
            Map.entry(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, this::handleCreateCharacter),
            Map.entry(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, this::handleSelectCharacter),
            Map.entry(Opcode.OPCODE_MOVE_COMMAND_VALUE, this::handleMoveCommand),
            Map.entry(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, this::handleSetTargetCommand),
            Map.entry(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, this::handleUseSkillCommand),
            Map.entry(Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, this::handleEquipItemCommand),
            Map.entry(Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, this::handleUnequipItemCommand),
            Map.entry(Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, this::handleEnhanceItemCommand),
            Map.entry(Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, this::handleListShopItemsRequest),
            Map.entry(Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, this::handleBuyItemCommand),
            Map.entry(Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, this::handleSellItemCommand));

    GatewayChannelHandler(GatewayFacades facades, WorldGateway worldGateway, Executor authExecutor) {
        this.accountFacade = facades.accountFacade();
        this.characterFacade = facades.characterFacade();
        this.inventoryFacade = facades.inventoryFacade();
        this.shopFacade = facades.shopFacade();
        this.worldGateway = worldGateway;
        this.authExecutor = authExecutor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame) {
        var header = frame.header();
        var handler = handlers.get(header.opcode());
        try {
            if (handler == null) {
                sendError(ctx, header.sequenceId(), 404, "error.unknown_opcode");
                return;
            }
            handler.handle(ctx, header, frame.payload());
        } catch (InvalidProtocolBufferException e) {
            // Fail fast ở MỨC PACKET (Điều 16) — không làm sập kết nối vì 1 payload hỏng.
            LOG.warn("Payload protobuf không hợp lệ: opcode={}, sequenceId={}",
                    header.opcode(), header.sequenceId(), e);
            sendError(ctx, header.sequenceId(), 400, "error.invalid_payload");
        }
    }

    private void handlePing(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var ping = PingCommand.parseFrom(payload);
        var pong = PongResponse.newBuilder()
                .setClientTimestampMs(ping.getClientTimestampMs())
                .setServerTimestampMs(System.currentTimeMillis())
                .build();
        send(ctx, Opcode.OPCODE_PONG_VALUE, header.sequenceId(), pong.toByteArray());
    }

    private void handleConnect(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var request = ConnectRequest.parseFrom(payload);
        var username = request.getUsername();
        var sequenceId = header.sequenceId();

        // Đăng nhập chạm Redis/PostgreSQL (qua AccountFacade) — I/O chặn, KHÔNG được chạy trên
        // Netty EventLoop (SERVER.md §2 — "Cấm: Query DB, logic nặng, chờ lock"). Đẩy sang Virtual
        // Thread; ctx.writeAndFlush an toàn gọi từ thread khác EventLoop (Netty tự chuyển task).
        authExecutor.execute(() -> handleConnectBlocking(ctx, sequenceId, username));
    }

    // PMD AvoidCatchingGenericException: đúng ý — đây là ranh giới I/O bất đồng bộ, PHẢI trả lời
    // client dù lỗi gì xảy ra (DB down, Redis down, username không hợp lệ...), nếu không client
    // treo chờ response không bao giờ tới. Không thể liệt kê hết loại lỗi từ AccountFacade.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleConnectBlocking(ChannelHandlerContext ctx, int sequenceId, String username) {
        try {
            var result = accountFacade.login(username);
            boundAccountId = result.accountId();
            var response = ConnectResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionId(result.sessionToken())
                    .setAccountId(result.accountId())
                    .build();
            send(ctx, Opcode.OPCODE_CONNECT_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (IllegalArgumentException e) {
            LOG.warn("Đăng nhập thất bại: username không hợp lệ", e);
            sendError(ctx, sequenceId, 400, "error.invalid_username");
        } catch (RuntimeException e) {
            LOG.error("Đăng nhập thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.login_failed");
        }
    }

    private void handleListCharacters(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListCharactersRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        var sequenceId = header.sequenceId();
        var accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleListCharactersBlocking(ctx, sequenceId, accountId));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleListCharactersBlocking(ChannelHandlerContext ctx, int sequenceId, long accountId) {
        try {
            var characters = characterFacade.listCharacters(accountId);
            var response = ListCharactersResponse.newBuilder()
                    .addAllCharacters(characters.stream().map(GatewayChannelHandler::toProto).toList())
                    .build();
            send(ctx, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Liệt kê nhân vật thất bại", e);
            sendError(ctx, sequenceId, 500, "error.list_characters_failed");
        }
    }

    private void handleCreateCharacter(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var request = CreateCharacterRequest.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleCreateCharacterBlocking(ctx, sequenceId, accountId, request));
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleCreateCharacterBlocking(
            ChannelHandlerContext ctx, int sequenceId, long accountId, CreateCharacterRequest request) {
        try {
            var character = characterFacade.createCharacter(
                    accountId, request.getName(), request.getIdempotencyKey());
            var response = CreateCharacterResponse.newBuilder()
                    .setSuccess(true)
                    .setCharacter(toProto(character))
                    .build();
            send(ctx, Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (IllegalArgumentException e) {
            LOG.warn("Tạo nhân vật thất bại: tên không hợp lệ", e);
            sendError(ctx, sequenceId, 400, "error.invalid_character_name");
        } catch (CharacterLimitExceededException e) {
            LOG.warn("Tạo nhân vật thất bại: vượt giới hạn", e);
            sendError(ctx, sequenceId, 409, "error.character_limit_exceeded");
        } catch (DuplicateCharacterNameException e) {
            LOG.warn("Tạo nhân vật thất bại: tên trùng", e);
            sendError(ctx, sequenceId, 409, "error.duplicate_character_name");
        } catch (IdempotencyKeyReusedException e) {
            LOG.warn("Tạo nhân vật thất bại: idempotencyKey đã dùng", e);
            sendError(ctx, sequenceId, 409, "error.duplicate_request");
        } catch (RuntimeException e) {
            LOG.error("Tạo nhân vật thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.create_character_failed");
        }
    }

    private void handleSelectCharacter(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var request = SelectCharacterRequest.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        var characterId = request.getCharacterId();
        authExecutor.execute(() -> handleSelectCharacterBlocking(ctx, sequenceId, accountId, characterId));
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleSelectCharacterBlocking(
            ChannelHandlerContext ctx, int sequenceId, long accountId, long characterId) {
        try {
            characterFacade.selectCharacter(accountId, characterId);
            boundCharacterId = characterId;
            // Đăng ký connection NÀY để world outbound (spawn/move/despawn — SERVER.md §6) tìm
            // đúng channel; attachCharacter chỉ enqueue, entity ECS thật tạo ở tick kế tiếp.
            worldGateway.registry().register(characterId, ctx);
            var spawn = characterFacade.loadPosition(characterId);
            var stats = characterFacade.loadStats(characterId);
            var baseStats = new CharacterBaseStats(stats.str(), stats.agi(), stats.intelligence(), stats.vit());
            worldGateway.facade().attachCharacter(characterId, spawn.x(), spawn.y(), baseStats);
            // GAMEPLAY.md §2 "tính lại chỉ số dẫn xuất khi đổi trang bị" — áp dụng NGAY lúc attach,
            // không chỉ lúc equip/unequip sau đó, nếu không nhân vật mặc đồ từ phiên trước sẽ mất
            // bonus khi đăng nhập lại (server-world không tự biết feature-inventory).
            var equipmentBonus = inventoryFacade.computeEquipmentBonus(characterId);
            worldGateway.facade().applyEquipmentBonus(characterId, equipmentBonus.bonusAtk());
            var response = SelectCharacterResponse.newBuilder().setSuccess(true).build();
            send(ctx, Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (CharacterNotFoundException e) {
            // Cùng lỗi dù nhân vật không tồn tại hay thuộc account khác (fail secure — SECURITY.md §4).
            LOG.warn("Chọn nhân vật thất bại: không tìm thấy", e);
            sendError(ctx, sequenceId, 404, "error.character_not_found");
        } catch (RuntimeException e) {
            LOG.error("Chọn nhân vật thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.select_character_failed");
        }
    }

    // MoveCommand KHÔNG chạm DB/Redis — chỉ enqueue vào server-world (rẻ, thread-safe), nên xử
    // lý thẳng trên EventLoop, không cần authExecutor (SERVER.md §2: "EventLoop được: decode,
    // validate rẻ, enqueue").
    private void handleMoveCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = MoveCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // NETWORK.md §13: vượt ngưỡng 1 window → drop packet + log (KHÔNG trả ErrorResponse — im
        // lặng, tránh cho client gian lận biết chính xác ngưỡng để dò); vượt liên tục → ngắt kết nối.
        if (!moveCommandRateLimiter.tryAcquire()) {
            LOG.warn("MoveCommand vượt rate limit: characterId={}", characterId);
            if (moveCommandRateLimiter.shouldDisconnect()) {
                LOG.warn("MoveCommand vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
                ctx.close();
            }
            return;
        }
        worldGateway.facade().enqueueMoveCommand(characterId, sequenceId, command.getDirX(), command.getDirY());
    }

    // SetTargetCommand KHÔNG chạm DB — chỉ enqueue vào server-world (rẻ, thread-safe), xử lý
    // thẳng trên EventLoop giống handleMoveCommand. GDD §6 "Auto-target": đây KHÔNG phải lệnh
    // "đánh 1 nhát" nên không cần rate limit riêng như MoveCommand (tần suất thấp — người chơi
    // chọn mục tiêu, không lặp lại mỗi tick).
    private void handleSetTargetCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = SetTargetCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        worldGateway.facade().setTarget(characterId, command.getTargetEntityId());
    }

    // UseSkillCommand KHÔNG chạm DB — chỉ enqueue vào server-world, xử lý thẳng trên EventLoop
    // giống handleSetTargetCommand. Cooldown skill tự giới hạn tần suất thật (SkillSystem) nên
    // không cần rate limit riêng — spam quá cooldown chỉ là no-op, không ảnh hưởng gameplay.
    private void handleUseSkillCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = UseSkillCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        worldGateway.facade().useSkill(characterId, command.getSkillCode(), command.getTargetEntityId());
    }

    // EquipItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleSelectCharacter.
    private void handleEquipItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = EquipItemCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleEquipItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleEquipItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, EquipItemCommand command) {
        var slot = toDomainSlot(command.getSlot());
        if (slot == null) {
            sendError(ctx, sequenceId, 400, "error.invalid_equipment_slot");
            return;
        }
        var result = inventoryFacade.equipItem(characterId, command.getItemId(), slot);
        if (result == InventoryFacade.EquipResult.OK) {
            // GAMEPLAY.md §2 — mặc đồ xong phải tính lại chỉ số dẫn xuất NGAY, không đợi lần
            // attach kế tiếp (server-world không tự biết trang bị vừa đổi).
            var equipmentBonus = inventoryFacade.computeEquipmentBonus(characterId);
            worldGateway.facade().applyEquipmentBonus(characterId, equipmentBonus.bonusAtk());
        }
        var response = EquipItemResponse.newBuilder()
                .setSuccess(result == InventoryFacade.EquipResult.OK)
                .setErrorKey(toErrorKey(result))
                .build();
        send(ctx, Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // UnequipItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleEquipItemCommand.
    private void handleUnequipItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = UnequipItemCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleUnequipItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleUnequipItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, UnequipItemCommand command) {
        var slot = toDomainSlot(command.getSlot());
        if (slot == null) {
            sendError(ctx, sequenceId, 400, "error.invalid_equipment_slot");
            return;
        }
        var result = inventoryFacade.unequipItem(characterId, slot);
        if (result == InventoryFacade.UnequipResult.OK) {
            var equipmentBonus = inventoryFacade.computeEquipmentBonus(characterId);
            worldGateway.facade().applyEquipmentBonus(characterId, equipmentBonus.bonusAtk());
        }
        var response = UnequipItemResponse.newBuilder()
                .setSuccess(result == InventoryFacade.UnequipResult.OK)
                .setErrorKey(result == InventoryFacade.UnequipResult.OK ? "" : "error.slot_not_equipped")
                .build();
        send(ctx, Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // EnhanceItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleEquipItemCommand.
    private void handleEnhanceItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = EnhanceItemCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleEnhanceItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleEnhanceItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, EnhanceItemCommand command) {
        var result = inventoryFacade.enhanceItem(characterId, command.getItemId());
        var response = EnhanceItemResponse.newBuilder().setOutcome(toProtoOutcome(result)).build();
        send(ctx, Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // Bảng tra thay switch 6 nhánh (PMD CyclomaticComplexity — cùng lý do SLOT_MAPPING bên dưới).
    private static final Map<InventoryFacade.EnhanceResult, EnhanceOutcome> ENHANCE_OUTCOME_MAPPING = Map.of(
            InventoryFacade.EnhanceResult.SUCCESS, EnhanceOutcome.ENHANCE_OUTCOME_SUCCESS,
            InventoryFacade.EnhanceResult.FAILED_NO_DROP, EnhanceOutcome.ENHANCE_OUTCOME_FAILED_NO_DROP,
            InventoryFacade.EnhanceResult.FAILED_DROPPED, EnhanceOutcome.ENHANCE_OUTCOME_FAILED_DROPPED,
            InventoryFacade.EnhanceResult.ITEM_NOT_FOUND, EnhanceOutcome.ENHANCE_OUTCOME_ITEM_NOT_FOUND,
            InventoryFacade.EnhanceResult.MAX_LEVEL, EnhanceOutcome.ENHANCE_OUTCOME_MAX_LEVEL,
            InventoryFacade.EnhanceResult.INSUFFICIENT_GOLD, EnhanceOutcome.ENHANCE_OUTCOME_INSUFFICIENT_GOLD);

    private static EnhanceOutcome toProtoOutcome(InventoryFacade.EnhanceResult result) {
        return ENHANCE_OUTCOME_MAPPING.get(result);
    }

    private void handleListShopItemsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListShopItemsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleListShopItemsBlocking(ctx, sequenceId));
    }

    private void handleListShopItemsBlocking(ChannelHandlerContext ctx, int sequenceId) {
        var listings = shopFacade.listItems().stream()
                .map(listing -> ShopListingView.newBuilder()
                        .setItemTemplateId(listing.itemTemplateId())
                        .setBuyPrice(listing.buyPrice())
                        .setSellPrice(listing.sellPrice())
                        .build())
                .toList();
        var response = ListShopItemsResponse.newBuilder().addAllListings(listings).build();
        send(ctx, Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleBuyItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = BuyItemCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleBuyItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleBuyItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, BuyItemCommand command) {
        var result = shopFacade.buyItem(characterId, command.getItemTemplateId(), command.getQuantity());
        var response = BuyItemResponse.newBuilder().setOutcome(toProtoOutcome(result)).build();
        send(ctx, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<ShopFacade.BuyResult, BuyOutcome> BUY_OUTCOME_MAPPING = Map.of(
            ShopFacade.BuyResult.OK, BuyOutcome.BUY_OUTCOME_OK,
            ShopFacade.BuyResult.ITEM_NOT_IN_SHOP, BuyOutcome.BUY_OUTCOME_ITEM_NOT_IN_SHOP,
            ShopFacade.BuyResult.INSUFFICIENT_GOLD, BuyOutcome.BUY_OUTCOME_INSUFFICIENT_GOLD,
            ShopFacade.BuyResult.INVENTORY_FULL, BuyOutcome.BUY_OUTCOME_INVENTORY_FULL);

    private static BuyOutcome toProtoOutcome(ShopFacade.BuyResult result) {
        return BUY_OUTCOME_MAPPING.get(result);
    }

    private void handleSellItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        var command = SellItemCommand.parseFrom(payload);
        var sequenceId = header.sequenceId();
        var characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        authExecutor.execute(() -> handleSellItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleSellItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SellItemCommand command) {
        var result = shopFacade.sellItem(characterId, command.getItemId(), command.getQuantity());
        var response = SellItemResponse.newBuilder().setOutcome(toProtoOutcome(result)).build();
        send(ctx, Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<ShopFacade.SellResult, SellOutcome> SELL_OUTCOME_MAPPING = Map.of(
            ShopFacade.SellResult.OK, SellOutcome.SELL_OUTCOME_OK,
            ShopFacade.SellResult.ITEM_NOT_FOUND, SellOutcome.SELL_OUTCOME_ITEM_NOT_FOUND,
            ShopFacade.SellResult.ITEM_NOT_SELLABLE, SellOutcome.SELL_OUTCOME_ITEM_NOT_SELLABLE,
            ShopFacade.SellResult.INSUFFICIENT_QUANTITY, SellOutcome.SELL_OUTCOME_INSUFFICIENT_QUANTITY);

    private static SellOutcome toProtoOutcome(ShopFacade.SellResult result) {
        return SELL_OUTCOME_MAPPING.get(result);
    }

    // Bảng tra thay switch 8 nhánh (PMD CyclomaticComplexity — cùng lý do đổi channelRead0 sang
    // Map ở trên). UNSPECIFIED/UNRECOGNIZED cố ý KHÔNG có trong map — get() trả null, đúng ý nghĩa
    // "slot không hợp lệ".
    private static final Map<EquipmentSlot, EquipmentSlotType> SLOT_MAPPING = Map.of(
            EquipmentSlot.EQUIPMENT_SLOT_WEAPON, EquipmentSlotType.WEAPON,
            EquipmentSlot.EQUIPMENT_SLOT_ARMOR, EquipmentSlotType.ARMOR,
            EquipmentSlot.EQUIPMENT_SLOT_HELMET, EquipmentSlotType.HELMET,
            EquipmentSlot.EQUIPMENT_SLOT_GLOVE, EquipmentSlotType.GLOVE,
            EquipmentSlot.EQUIPMENT_SLOT_BOOT, EquipmentSlotType.BOOT,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_1, EquipmentSlotType.ACCESSORY_1,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2, EquipmentSlotType.ACCESSORY_2);

    /** {@code null} nếu client gửi giá trị enum không hợp lệ (VD {@code UNSPECIFIED}/{@code UNRECOGNIZED}). */
    private static EquipmentSlotType toDomainSlot(EquipmentSlot slot) {
        return SLOT_MAPPING.get(slot);
    }

    private static String toErrorKey(InventoryFacade.EquipResult result) {
        return switch (result) {
            case OK -> "";
            case ITEM_NOT_FOUND -> "error.item_not_found";
            case WRONG_SLOT -> "error.wrong_equipment_slot";
        };
    }

    /**
     * {@code characterId} đã chọn cho connection này, hoặc {@code null} nếu chưa chọn. Package-
     * private — dùng trong test (SECURITY.md §4: characterId từ session, không từ packet).
     */
    Long boundCharacterId() {
        return boundCharacterId;
    }

    private static CharacterSummary toProto(Character character) {
        return CharacterSummary.newBuilder()
                .setId(character.id())
                .setName(character.name())
                .setFaction(toProtoFaction(character.faction()))
                .setLevel(character.level())
                .build();
    }

    private static Faction toProtoFaction(dev.kage.character.domain.Faction faction) {
        return switch (faction) {
            case HOA -> Faction.FACTION_HOA;
        };
    }

    private void sendError(ChannelHandlerContext ctx, int relatedSequenceId, int errorCode, String errorKey) {
        var error = ErrorResponse.newBuilder()
                .setErrorCode(errorCode)
                .setErrorKey(errorKey)
                .setRelatedSequenceId(relatedSequenceId)
                .build();
        send(ctx, Opcode.OPCODE_ERROR_VALUE, relatedSequenceId, error.toByteArray());
    }

    private void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        var header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // Mất kết nối (đóng chủ động, timeout, hay lỗi — exceptionCaught cũng dẫn tới sự kiện
        // này) — dọn entity ECS + AoI của nhân vật này (SERVER.md §9: 1 kết nối chết không được
        // để bia đỡ đạn đứng lại trong world).
        var characterId = boundCharacterId;
        if (characterId != null) {
            // Đọc vị trí cuối TRƯỚC unregister() (unregister xoá luôn khỏi lastKnownPosition —
            // đọc ở đây trên EventLoop, rẻ, không phải I/O, không tra lại sau trên authExecutor
            // vì lúc đó có thể đã bị unregister() xoá mất — xem javadoc CharacterPositionPersister#save).
            var lastPosition = worldGateway.registry().lastKnownPosition(characterId);
            worldGateway.registry().unregister(characterId);
            worldGateway.facade().detachCharacter(characterId);
            // SERVER.md §8: logout là 1 trong 3 mốc bắt buộc ghi vị trí. savePosition chạm DB
            // (blocking) — không được chạy trên EventLoop, đẩy sang authExecutor.
            if (lastPosition != null) {
                authExecutor.execute(
                        () -> worldGateway.positionPersister().save(characterId, lastPosition.x(), lastPosition.y()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("Lỗi không mong đợi trên kết nối {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
