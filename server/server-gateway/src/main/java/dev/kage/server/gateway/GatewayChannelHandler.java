package dev.kage.server.gateway;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginResult;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterLimitExceededException;
import dev.kage.character.application.CharacterNotFoundException;
import dev.kage.character.application.DuplicateCharacterNameException;
import dev.kage.character.application.FactionSelectionLevelTooLowException;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.chat.api.ChatFacade;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;
import dev.kage.friend.api.FriendFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.mail.api.MailFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.AcceptDuelCommand;
import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.AcceptFriendRequestCommand;
import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.AcceptFriendRequestResponse;
import dev.kage.protocol.AcceptPartyInviteCommand;
import dev.kage.protocol.AcceptPartyInviteOutcome;
import dev.kage.protocol.AcceptPartyInviteResponse;
import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.ApproveGuildJoinRequestCommand;
import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.ApproveGuildJoinRequestResponse;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.ClaimMailOutcome;
import dev.kage.protocol.ClaimMailResponse;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.CreateGuildResponse;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.DeclineDuelCommand;
import dev.kage.protocol.DeclineDuelOutcome;
import dev.kage.protocol.DeclineDuelResponse;
import dev.kage.protocol.DeleteMailCommand;
import dev.kage.protocol.DeleteMailOutcome;
import dev.kage.protocol.DeleteMailResponse;
import dev.kage.protocol.DialogueEndedResponse;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DialogueOptionView;
import dev.kage.protocol.DisbandGuildCommand;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.DisbandGuildResponse;
import dev.kage.protocol.DuelChallengeDeclinedEvent;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ExitDuelCommand;
import dev.kage.protocol.ExitDuelOutcome;
import dev.kage.protocol.ExitDuelResponse;
import dev.kage.protocol.ExitDungeonCommand;
import dev.kage.protocol.ExitDungeonOutcome;
import dev.kage.protocol.ExitDungeonResponse;
import dev.kage.protocol.Faction;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.GetMyGuildRequest;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GetMyPartyRequest;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberOutcome;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.KickGuildMemberCommand;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.KickGuildMemberResponse;
import dev.kage.protocol.KickPartyMemberCommand;
import dev.kage.protocol.KickPartyMemberOutcome;
import dev.kage.protocol.KickPartyMemberResponse;
import dev.kage.protocol.LeaveGuildCommand;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.LeaveGuildResponse;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.LeavePartyOutcome;
import dev.kage.protocol.LeavePartyResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListGuildMembersRequest;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.ListPartyMembersRequest;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingFriendRequestsRequest;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsRequest;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.ListPendingPartyInvitesRequest;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.ListQuestsRequest;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MailView;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingFriendRequestView;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.RejectFriendRequestCommand;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RejectFriendRequestResponse;
import dev.kage.protocol.RejectGuildJoinRequestCommand;
import dev.kage.protocol.RejectGuildJoinRequestOutcome;
import dev.kage.protocol.RejectGuildJoinRequestResponse;
import dev.kage.protocol.RejectPartyInviteCommand;
import dev.kage.protocol.RejectPartyInviteOutcome;
import dev.kage.protocol.RejectPartyInviteResponse;
import dev.kage.protocol.RemoveFriendCommand;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.RemoveFriendResponse;
import dev.kage.protocol.RequestTradeCommand;
import dev.kage.protocol.RequestTradeOutcome;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SelectDialogueOptionCommand;
import dev.kage.protocol.SelectFactionRequest;
import dev.kage.protocol.SelectFactionResponse;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SendChatMessageOutcome;
import dev.kage.protocol.SendChatMessageResponse;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.SetTradeOfferOutcome;
import dev.kage.protocol.SetTradeOfferResponse;
import dev.kage.protocol.ShopListingView;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.TradeCloseReason;
import dev.kage.protocol.TradeClosedEvent;
import dev.kage.protocol.TradeOfferItemView;
import dev.kage.protocol.TradePhase;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.TradeUpdatedEvent;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.UseItemCommand;
import dev.kage.protocol.UseItemResponse;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.application.GetPvpRatingUseCase;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterEntry;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.WorldFacade;
import dev.kage.shop.api.ShopFacade;
import dev.kage.trade.api.TradeFacade;

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
// PMD CyclomaticComplexity (class-level, ngưỡng mặc định 80, lớp này ~366): PMD 7.10.0 chưa từng
// chạy được thật trên JDK 25 (StackOverflowError type-inference — xem STATIC-ANALYSIS.md), nên đây
// là lần đầu class này bị đo. Nguyên nhân THẬT: 1 handler dispatch ~119 opcode (mỗi handleXBlocking
// riêng, complexity từng method thấp — bảng opcode->handler đã cố tình tránh if-else dài, xem
// javadoc PacketHandler bên dưới) — không phải 1 method phình to. Tách theo feature (mỗi feature 1
// handler riêng, server-gateway chỉ định tuyến) là thay đổi kiến trúc tầng gateway, cần chủ dự án
// quyết định (CLAUDE.md §4) — KHÔNG tự làm ở đây, chỉ ghi nhận nợ kỹ thuật.
@SuppressWarnings("PMD.CyclomaticComplexity")
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

    // Chủ dự án duyệt 2026-09-24: trần CHUNG cho mọi lệnh chạm DB (mọi lệnh đi qua executeBlocking).
    // Cỡ theo client THẬT, đếm từ client-core: đăng nhập ≤4 (Connect, ListCharacters, CreateCharacter,
    // SelectCharacter), vào world 2 (GetItemCatalog, ListQuests — WorldScreen.show), rồi mở MỌI cửa sổ
    // trong cùng một giây: bang 3, nhóm 3, bạn bè 2, túi đồ/thư/cửa hàng/xếp hạng mỗi cái 1 = 18.
    // Trần 40 = GẤP ĐÔI mức cao nhất của client thật (chủ dự án chọn 2026-09-24, thay cho 20 ban
    // đầu): 18/20 là biên quá mỏng, vì vài lệnh client tự gửi khi nhận event do NGƯỜI KHÁC gây ra
    // (mỗi TradeUpdatedEvent ⇒ GetTradeStatus; NewMailEvent/ItemGrantedEvent khi đang mở cửa sổ ⇒
    // listMail/listInventory) — một đối tác giao dịch spam có thể đẩy nạn nhân sát trần rồi ngắt oan.
    // Một client spam hàng nghìn lệnh/giây vẫn chạm ngay.
    private static final int MAX_DB_COMMANDS_PER_SECOND = 40;

    // Khoá i18n cho GoldChangedEvent lúc vừa vào world (ADR-035 Decision-3) — KHÔNG phải câu tiếng
    // Việt cứng. Các lý do còn lại (mua/bán/cường hoá/thư/giao dịch) do adapter server-database đặt,
    // xem GoldLedger javadoc vì sao chúng nằm ở đó chứ không ở đây.
    private static final String REASON_ENTER_WORLD = "gold.reason.enter_world";

    private final AccountFacade accountFacade;
    private final CharacterFacade characterFacade;
    private final InventoryFacade inventoryFacade;
    private final ShopFacade shopFacade;
    private final DialogueFacade dialogueFacade;
    private final QuestFacade questFacade;
    private final FriendFacade friendFacade;
    private final MailFacade mailFacade;
    private final ChatFacade chatFacade;
    private final GuildFacade guildFacade;
    private final PartyFacade partyFacade;
    private final TradeFacade tradeFacade;
    private final PvpFacade pvpFacade;
    private final WorldGateway worldGateway;
    private final Executor authExecutor;
    private final GatewayNetworkMetrics networkMetrics;
    private final GatewayTracing tracing;
    // Per-connection (handler đã là state per-connection, xem javadoc lớp) — không cần đồng bộ
    // hoá, chỉ được đọc/ghi trên Netty EventLoop của CHÍNH connection này.
    private final RateLimiter moveCommandRateLimiter =
            new RateLimiter(MAX_MOVE_COMMANDS_PER_SECOND, Duration.ofSeconds(1));
    // Chỉ chạm trong executeBlocking, mà executeBlocking chỉ được gọi từ các handle*Command/Request
    // do channelRead0 phát — tức trên EventLoop của CHÍNH kết nối này, cùng quy ước moveCommandRateLimiter.
    private final RateLimiter dbCommandRateLimiter = new RateLimiter(MAX_DB_COMMANDS_PER_SECOND, Duration.ofSeconds(1));
    // NETWORK.md §13 (đề xuất, chưa verify) — World nghiêm ngặt nhất (toàn server thấy), Khu vực
    // lỏng nhất (chỉ người gần thấy), Riêng tư ở giữa, khớp bảng GAMEPLAY.md §10.
    private final RateLimiter chatWorldRateLimiter = new RateLimiter(5, Duration.ofSeconds(10));
    private final RateLimiter chatPrivateRateLimiter = new RateLimiter(10, Duration.ofSeconds(10));
    private final RateLimiter chatAreaRateLimiter = new RateLimiter(15, Duration.ofSeconds(10));
    // GAMEPLAY.md §10: Bang hội = "Vừa", giữa World (nghiêm ngặt nhất) và Area (lỏng nhất).
    private final RateLimiter chatGuildRateLimiter = new RateLimiter(10, Duration.ofSeconds(10));
    // GAMEPLAY.md §10: Party = "Lỏng", cùng mức Area (party nhỏ, ít rủi ro spam hơn World/Guild).
    private final RateLimiter chatPartyRateLimiter = new RateLimiter(15, Duration.ofSeconds(10));

    // handleConnect/handleSelectCharacter set giá trị này từ Virtual Thread; channelRead0 của
    // packet TIẾP THEO có thể chạy trên EventLoop trước khi Virtual Thread đó xong (Netty không
    // chờ) — cần volatile để đảm bảo visibility giữa 2 thread.
    private volatile Long boundAccountId;
    private volatile Long boundCharacterId;

    // Một lần dọn mỗi kết nối — xem releaseCharacter (channelInactive và bindOrReleaseIfClosed có
    // thể cùng thấy kết nối chết).
    private final java.util.concurrent.atomic.AtomicBoolean characterReleased =
            new java.util.concurrent.atomic.AtomicBoolean();

    // Bind sau khi field khác đã gán xong (field initializer chạy tuần tự trong <init>) — method
    // reference chỉ CHỤP this, không gọi ngay, nên thứ tự khai báo so với constructor không quan trọng.
    // Map.ofEntries thay Map.of (11 cặp vượt quá 10 overload cố định của Map.of) — cùng ngữ nghĩa.
    private final Map<Integer, PacketHandler> handlers = Map.ofEntries(
            Map.entry(Opcode.OPCODE_PING_VALUE, this::handlePing),
            Map.entry(Opcode.OPCODE_CONNECT_REQUEST_VALUE, this::handleConnect),
            Map.entry(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, this::handleListCharacters),
            Map.entry(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, this::handleCreateCharacter),
            Map.entry(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, this::handleSelectCharacter),
            Map.entry(Opcode.OPCODE_SELECT_FACTION_REQUEST_VALUE, this::handleSelectFactionRequest),
            Map.entry(Opcode.OPCODE_MOVE_COMMAND_VALUE, this::handleMoveCommand),
            Map.entry(Opcode.OPCODE_JUMP_COMMAND_VALUE, this::handleJumpCommand),
            Map.entry(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, this::handleSetTargetCommand),
            Map.entry(Opcode.OPCODE_ATTACK_COMMAND_VALUE, this::handleAttackCommand),
            Map.entry(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, this::handleUseSkillCommand),
            Map.entry(Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, this::handleEquipItemCommand),
            Map.entry(Opcode.OPCODE_UNEQUIP_ITEM_COMMAND_VALUE, this::handleUnequipItemCommand),
            Map.entry(Opcode.OPCODE_ENHANCE_ITEM_COMMAND_VALUE, this::handleEnhanceItemCommand),
            Map.entry(Opcode.OPCODE_USE_ITEM_COMMAND_VALUE, this::handleUseItemCommand),
            Map.entry(Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE, this::handleListInventoryRequest),
            Map.entry(Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE, this::handleGetItemCatalogRequest),
            Map.entry(Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE, this::handleListQuestsRequest),
            Map.entry(Opcode.OPCODE_LIST_SHOP_ITEMS_REQUEST_VALUE, this::handleListShopItemsRequest),
            Map.entry(Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE, this::handleBuyItemCommand),
            Map.entry(Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE, this::handleSellItemCommand),
            Map.entry(Opcode.OPCODE_TALK_TO_NPC_COMMAND_VALUE, this::handleTalkToNpcCommand),
            Map.entry(Opcode.OPCODE_SELECT_DIALOGUE_OPTION_COMMAND_VALUE, this::handleSelectDialogueOptionCommand),
            Map.entry(Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE, this::handleClaimQuestRewardCommand),
            Map.entry(Opcode.OPCODE_SEND_FRIEND_REQUEST_COMMAND_VALUE, this::handleSendFriendRequestCommand),
            Map.entry(Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_COMMAND_VALUE, this::handleAcceptFriendRequestCommand),
            Map.entry(Opcode.OPCODE_REJECT_FRIEND_REQUEST_COMMAND_VALUE, this::handleRejectFriendRequestCommand),
            Map.entry(Opcode.OPCODE_REMOVE_FRIEND_COMMAND_VALUE, this::handleRemoveFriendCommand),
            Map.entry(Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE, this::handleListFriendsRequest),
            Map.entry(
                    Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_REQUEST_VALUE,
                    this::handleListPendingFriendRequestsRequest),
            Map.entry(Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE, this::handleSendMailCommand),
            Map.entry(Opcode.OPCODE_LIST_MAIL_REQUEST_VALUE, this::handleListMailRequest),
            Map.entry(Opcode.OPCODE_CLAIM_MAIL_COMMAND_VALUE, this::handleClaimMailCommand),
            Map.entry(Opcode.OPCODE_DELETE_MAIL_COMMAND_VALUE, this::handleDeleteMailCommand),
            Map.entry(Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, this::handleSendChatMessageCommand),
            Map.entry(Opcode.OPCODE_CREATE_GUILD_COMMAND_VALUE, this::handleCreateGuildCommand),
            Map.entry(Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_COMMAND_VALUE, this::handleSendGuildJoinRequestCommand),
            Map.entry(
                    Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_COMMAND_VALUE, this::handleApproveGuildJoinRequestCommand),
            Map.entry(Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_COMMAND_VALUE, this::handleRejectGuildJoinRequestCommand),
            Map.entry(Opcode.OPCODE_LEAVE_GUILD_COMMAND_VALUE, this::handleLeaveGuildCommand),
            Map.entry(Opcode.OPCODE_DISBAND_GUILD_COMMAND_VALUE, this::handleDisbandGuildCommand),
            Map.entry(Opcode.OPCODE_KICK_GUILD_MEMBER_COMMAND_VALUE, this::handleKickGuildMemberCommand),
            Map.entry(Opcode.OPCODE_LIST_GUILD_MEMBERS_REQUEST_VALUE, this::handleListGuildMembersRequest),
            Map.entry(
                    Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_REQUEST_VALUE,
                    this::handleListPendingGuildJoinRequestsRequest),
            Map.entry(Opcode.OPCODE_GET_MY_GUILD_REQUEST_VALUE, this::handleGetMyGuildRequest),
            Map.entry(Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE, this::handleCreatePartyCommand),
            Map.entry(Opcode.OPCODE_INVITE_PARTY_MEMBER_COMMAND_VALUE, this::handleInvitePartyMemberCommand),
            Map.entry(Opcode.OPCODE_ACCEPT_PARTY_INVITE_COMMAND_VALUE, this::handleAcceptPartyInviteCommand),
            Map.entry(Opcode.OPCODE_REJECT_PARTY_INVITE_COMMAND_VALUE, this::handleRejectPartyInviteCommand),
            Map.entry(Opcode.OPCODE_LEAVE_PARTY_COMMAND_VALUE, this::handleLeavePartyCommand),
            Map.entry(Opcode.OPCODE_KICK_PARTY_MEMBER_COMMAND_VALUE, this::handleKickPartyMemberCommand),
            Map.entry(Opcode.OPCODE_LIST_PARTY_MEMBERS_REQUEST_VALUE, this::handleListPartyMembersRequest),
            Map.entry(
                    Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_REQUEST_VALUE, this::handleListPendingPartyInvitesRequest),
            Map.entry(Opcode.OPCODE_GET_MY_PARTY_REQUEST_VALUE, this::handleGetMyPartyRequest),
            Map.entry(Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE, this::handleRequestTradeCommand),
            Map.entry(Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE, this::handleAcceptTradeRequestCommand),
            Map.entry(Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE, this::handleCancelTradeCommand),
            Map.entry(Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, this::handleSetTradeOfferCommand),
            Map.entry(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, this::handleConfirmTradeCommand),
            Map.entry(Opcode.OPCODE_GET_TRADE_STATUS_REQUEST_VALUE, this::handleGetTradeStatusRequest),
            Map.entry(Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE, this::handleEnterDungeonCommand),
            Map.entry(Opcode.OPCODE_EXIT_DUNGEON_COMMAND_VALUE, this::handleExitDungeonCommand),
            Map.entry(Opcode.OPCODE_CHALLENGE_DUEL_COMMAND_VALUE, this::handleChallengeDuelCommand),
            Map.entry(Opcode.OPCODE_ACCEPT_DUEL_COMMAND_VALUE, this::handleAcceptDuelCommand),
            Map.entry(Opcode.OPCODE_DECLINE_DUEL_COMMAND_VALUE, this::handleDeclineDuelCommand),
            Map.entry(Opcode.OPCODE_EXIT_DUEL_COMMAND_VALUE, this::handleExitDuelCommand),
            Map.entry(Opcode.OPCODE_GET_PVP_RATING_REQUEST_VALUE, this::handleGetPvpRatingRequest));

    GatewayChannelHandler(
            GatewayFacades facades,
            WorldGateway worldGateway,
            Executor authExecutor,
            GatewayObservability observability) {
        this.accountFacade = facades.accountFacade();
        this.characterFacade = facades.characterFacade();
        this.inventoryFacade = facades.inventoryFacade();
        this.shopFacade = facades.contentFacades().shopFacade();
        this.dialogueFacade = facades.contentFacades().dialogueFacade();
        this.questFacade = facades.contentFacades().questFacade();
        this.friendFacade = facades.contentFacades().socialFacades().friendFacade();
        this.mailFacade = facades.contentFacades().socialFacades().mailFacade();
        this.chatFacade = facades.contentFacades().socialFacades().chatFacade();
        this.guildFacade = facades.contentFacades().socialFacades().guildFacade();
        this.partyFacade = facades.contentFacades().socialFacades().partyFacade();
        this.tradeFacade = facades.contentFacades().socialFacades().tradeFacade();
        this.pvpFacade = facades.contentFacades().socialFacades().pvpFacade();
        this.worldGateway = worldGateway;
        this.authExecutor = authExecutor;
        this.networkMetrics = observability.networkMetrics();
        this.tracing = observability.tracing();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        networkMetrics.connectionOpened();
    }

    // javac [try] + PMD UnusedLocalVariable: `scope` không đọc trong thân try — ĐÚNG Ý,
    // try-with-resources chỉ dùng để đảm bảo Scope.close() gọi đúng lúc (OTel Context propagation),
    // không cần đọc biến đó. PMD 7.26.0 không tự loại trừ resource variable kiểu này (khác javac).
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PacketCodec.Frame frame) {
        PacketHeader header = frame.header();
        networkMetrics.recordPacketReceived(header.opcode(), frame.payload().length);
        PacketHandler handler = handlers.get(header.opcode());
        // OBSERVABILITY.md §6 — span "gateway.command" bao decode+validate+dispatch, 1 chỗ trung
        // tâm áp dụng cho MỌI opcode (không retrofit sâu hơn từng handler — xem javadoc GatewayTracing).
        Span span = tracing.startCommandSpan(opcodeNameForTracing(header.opcode()));
        try (Scope scope = span.makeCurrent()) {
            if (handler == null) {
                sendError(ctx, header.sequenceId(), 404, "error.unknown_opcode");
                return;
            }
            handler.handle(ctx, header, frame.payload());
        } catch (InvalidProtocolBufferException e) {
            // Fail fast ở MỨC PACKET (Điều 16) — không làm sập kết nối vì 1 payload hỏng.
            LOG.warn(
                    "Payload protobuf không hợp lệ: opcode={}, sequenceId={}", header.opcode(), header.sequenceId(), e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            sendError(ctx, header.sequenceId(), 400, "error.invalid_payload");
        } finally {
            span.end();
        }
    }

    /** {@code opcode} lạ (packet giả mạo) gộp vào 1 tag "unknown" — cùng lý do {@code GatewayNetworkMetrics}. */
    private static String opcodeNameForTracing(int opcode) {
        Opcode value = Opcode.forNumber(opcode);
        return value != null ? value.name() : "unknown";
    }

    private void handlePing(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        PingCommand ping = PingCommand.parseFrom(payload);
        PongResponse pong = PongResponse.newBuilder()
                .setClientTimestampMs(ping.getClientTimestampMs())
                .setServerTimestampMs(System.currentTimeMillis())
                .build();
        send(ctx, Opcode.OPCODE_PONG_VALUE, header.sequenceId(), pong.toByteArray());
    }

    private void handleConnect(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ConnectRequest request = ConnectRequest.parseFrom(payload);
        String username = request.getUsername();
        int sequenceId = header.sequenceId();

        // Đăng nhập chạm Redis/PostgreSQL (qua AccountFacade) — I/O chặn, KHÔNG được chạy trên
        // Netty EventLoop (SERVER.md §2 — "Cấm: Query DB, logic nặng, chờ lock"). Đẩy sang Virtual
        // Thread; ctx.writeAndFlush an toàn gọi từ thread khác EventLoop (Netty tự chuyển task).
        executeBlocking(ctx, sequenceId, () -> handleConnectBlocking(ctx, sequenceId, username));
    }

    // PMD AvoidCatchingGenericException: đúng ý — đây là ranh giới I/O bất đồng bộ, PHẢI trả lời
    // client dù lỗi gì xảy ra (DB down, Redis down, username không hợp lệ...), nếu không client
    // treo chờ response không bao giờ tới. Không thể liệt kê hết loại lỗi từ AccountFacade.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleConnectBlocking(ChannelHandlerContext ctx, int sequenceId, String username) {
        try {
            LoginResult result = accountFacade.login(username);
            boundAccountId = result.accountId();
            ConnectResponse response = ConnectResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionId(result.sessionToken())
                    .setAccountId(result.accountId())
                    .build();
            send(ctx, Opcode.OPCODE_CONNECT_RESPONSE_VALUE, sequenceId, response.toByteArray());
            networkMetrics.recordLoginSuccess();
        } catch (IllegalArgumentException e) {
            networkMetrics.recordLoginFailed();
            LOG.warn("Đăng nhập thất bại: username không hợp lệ", e);
            sendError(ctx, sequenceId, 400, "error.invalid_username");
        } catch (RuntimeException e) {
            networkMetrics.recordLoginFailed();
            LOG.error("Đăng nhập thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.login_failed");
        }
    }

    private void handleListCharacters(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListCharactersRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListCharactersBlocking(ctx, sequenceId, accountId));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleListCharactersBlocking(ChannelHandlerContext ctx, int sequenceId, long accountId) {
        try {
            List<Character> characters = characterFacade.listCharacters(accountId);
            ListCharactersResponse response = ListCharactersResponse.newBuilder()
                    .addAllCharacters(characters.stream()
                            .map(GatewayChannelHandler::toProto)
                            .toList())
                    .build();
            send(ctx, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Liệt kê nhân vật thất bại", e);
            sendError(ctx, sequenceId, 500, "error.list_characters_failed");
        }
    }

    private void handleCreateCharacter(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CreateCharacterRequest request = CreateCharacterRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleCreateCharacterBlocking(ctx, sequenceId, accountId, request));
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleCreateCharacterBlocking(
            ChannelHandlerContext ctx, int sequenceId, long accountId, CreateCharacterRequest request) {
        try {
            Character character =
                    characterFacade.createCharacter(accountId, request.getName(), request.getIdempotencyKey());
            CreateCharacterResponse response = CreateCharacterResponse.newBuilder()
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
        SelectCharacterRequest request = SelectCharacterRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        long characterId = request.getCharacterId();
        executeBlocking(ctx, sequenceId, () -> handleSelectCharacterBlocking(ctx, sequenceId, accountId, characterId));
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleSelectCharacterBlocking(
            ChannelHandlerContext ctx, int sequenceId, long accountId, long characterId) {
        try {
            Character character = characterFacade.selectCharacter(accountId, characterId);
            // THỨ TỰ (2026-09-24): đọc DB → gửi response → RỒI MỚI đăng ký + attach — client BỎ mọi
            // gói tới trước response (chat, bạn bè online, spawn quái không phát lại). Xem
            // bindOrReleaseIfClosed.
            PositionSnapshot spawn = characterFacade.loadPosition(characterId);
            CharacterStats stats = characterFacade.loadStats(characterId);
            CharacterBaseStats baseStats = new CharacterBaseStats(
                    stats.str(),
                    stats.agi(),
                    stats.intelligence(),
                    stats.vit(),
                    FactionMapping.toElement(character.faction()),
                    character.level());
            // Phase 9 lát cắt 6: định tuyến vào khu vực (channel) còn chỗ thay vì thẳng 1 World
            // duy nhất — MainWorldChannelRegistry tự mở thêm khu vực nếu cần (giới hạn người/khu
            // vực chặn AoI phình theo TỔNG N toàn map, xem javadoc class đó).
            // GAMEPLAY.md §2 "tính lại chỉ số dẫn xuất khi đổi trang bị" — đọc TRƯỚC khi attach và
            // gửi CÙNG một lệnh world. Tách làm hai lệnh (attach rồi applyEquipmentBonus) để lại một
            // tick mà entity đã ở trong world với chỉ số NỀN: đo thật 2026-09-18, đăng nhập cạnh quái
            // ăn 37 sát thương trên thanh máu 100 thay vì 27 trên thanh 206.
            EquipmentBonusSnapshot bonus = currentEquipmentBonus(characterId);
            SelectCharacterResponse response = enterWorldResponse(bonus, spawn);
            send(ctx, Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, sequenceId, response.toByteArray());
            // Đăng ký connection NÀY để world outbound (spawn/move/despawn — SERVER.md §6) tìm
            // đúng channel; attachCharacter chỉ enqueue, entity ECS thật tạo ở tick kế tiếp.
            worldGateway
                    .registry()
                    .register(characterId, ctx, CharacterAppearance.of(toProtoFaction(character.faction())));
            worldGateway
                    .mainWorldChannels()
                    .attachCharacter(new CharacterEntry(characterId, spawn.x(), spawn.y(), baseStats, bonus));
            if (!bindOrReleaseIfClosed(ctx, characterId)) {
                return;
            }
            sendInitialSelfState(characterId);
            // Đang trên authExecutor (Virtual Thread) rồi — friendFacade.listFriends chạm DB, an
            // toàn gọi thẳng, không cần đẩy thêm 1 lớp executor nữa (khác channelInactive bên dưới).
            notifyFriendsPresenceChanged(characterId, true);
        } catch (CharacterNotFoundException e) {
            // Cùng lỗi dù nhân vật không tồn tại hay thuộc account khác (fail secure — SECURITY.md §4).
            LOG.warn("Chọn nhân vật thất bại: không tìm thấy", e);
            sendError(ctx, sequenceId, 404, "error.character_not_found");
        } catch (RuntimeException e) {
            LOG.error("Chọn nhân vật thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.select_character_failed");
        }
    }

    /**
     * Gán {@code boundCharacterId} SAU khi đăng ký + attach, rồi dọn ngay nếu kết nối đã chết.
     *
     * <p><b>Vì sao gán muộn</b>: lệnh tới trong khoảng giữa response và attach (di chuyển, tấn
     * công...) chạy trên EventLoop và gọi {@code currentWorldFacade}, vốn NÉM nếu nhân vật chưa thuộc
     * World nào — tức {@code exceptionCaught} đóng luôn kết nối. Gán muộn thì lệnh đó chỉ nhận
     * {@code error.not_authenticated}, không ai bị đá.
     *
     * <p><b>Vì sao phải tự dọn</b>: kết nối chết TRƯỚC khi gán thì {@code channelInactive} thấy
     * {@code boundCharacterId == null} và không dọn gì — bản cũ còn tệ hơn: nó dọn TRƯỚC khi attach
     * rồi handler attach tiếp, để lại một thực thể "bóng ma" đứng mãi trong world. Nay kiểm lại sau
     * khi gán; {@link #releaseCharacter} chỉ chạy đúng một lần dù hai phía cùng thấy.
     *
     * @return {@code false} nếu kết nối đã chết và nhân vật vừa được dọn
     */
    private boolean bindOrReleaseIfClosed(ChannelHandlerContext ctx, long characterId) {
        boundCharacterId = characterId;
        if (ctx.channel().isActive()) {
            return true;
        }
        releaseCharacter(characterId);
        return false;
    }

    /**
     * Gói "vào world thành công" kèm MÁU của người chơi (ADR-036 hậu kiểm, playtest 2026-08-30).
     *
     * <p>Client PHẢI biết máu tối đa ngay tại đây: từ ADR-036 nó phụ thuộc trang bị, nên nếu thiếu
     * thì vạch máu hiện {@code "139 / 100"} — tử số là số thật của server, mẫu số là hằng số client
     * tự đoán — cho tới lần hồi sinh đầu tiên. Entity ECS lúc này mới chỉ được XẾP HÀNG (tạo ở tick
     * kế tiếp) nên không hỏi world được; hỏi CÔNG THỨC thay vì hỏi entity, và công thức chỉ có đúng
     * một bản ({@link WorldFacade#characterMaxHp}).
     *
     * <p>{@code currentHp = maxHp} vì {@code handleAttach} LUÔN cho nhân vật vào world với máu đầy:
     * máu hiện tại chưa được lưu qua phiên ({@code character_stat.current_hp} có cột nhưng world
     * không đọc). Khi nào HP được lưu thật thì đổi ĐÚNG chỗ này.
     */
    private static SelectCharacterResponse enterWorldResponse(EquipmentBonusSnapshot bonus, PositionSnapshot spawn) {
        int maxHp = WorldFacade.characterMaxHp(bonus);
        return SelectCharacterResponse.newBuilder()
                .setSuccess(true)
                .setCurrentHp(maxHp)
                .setMaxHp(maxHp)
                // ADR-041 — ĐÚNG snapshot vừa dùng để attachCharacter, không đọc DB lần hai: một
                // lần đọc, hai chỗ dùng, nên world và client không thể lệch nhau.
                .setSpawnX(spawn.x())
                .setSpawnY(spawn.y())
                .build();
    }

    /**
     * Chỉ có HAI kết quả tôi luyện thực sự đổi {@code enhance_level} — và do đó đổi chỉ số: thành
     * công (lên 1 cấp) và thất bại-rớt-cấp (xuống 1 cấp). Sáu kết quả còn lại để nguyên món đồ.
     */
    private static boolean enhanceChangedStats(InventoryFacade.EnhanceResult result) {
        return result == InventoryFacade.EnhanceResult.SUCCESS
                || result == InventoryFacade.EnhanceResult.FAILED_DROPPED;
    }

    /**
     * Tính lại chỉ số trang bị cho world VÀ trả về MÁU TỐI ĐA mới để gửi cho client (ADR-043).
     *
     * <p><b>Vì sao KHÔNG đọc thẳng entity</b>: {@link WorldFacade#applyEquipmentBonus} đẩy lệnh vào
     * hàng đợi của vòng tick, nên ngay sau lời gọi này {@code HealthComponent} vẫn còn số CŨ. Hỏi
     * CÔNG THỨC ({@link WorldFacade#characterMaxHp}) thay vì hỏi entity — đúng cách
     * {@code enterWorldResponse} đã làm, và công thức đó chỉ có MỘT bản (ADR-043 D5, phương án (a)
     * chủ dự án chọn).
     *
     * <p>⚠️ <b>Giới hạn có ý thức của phương án (a)</b>: máu HIỆN TẠI thì gateway KHÔNG biết — nó
     * nằm trong {@code HealthComponent} của luồng tick và world không có đường đọc đồng bộ nào. Nên
     * {@code current_hp} bỏ trống (0 = "giữ nguyên số của bạn"). Hệ quả còn lại: server dịch máu
     * hiện tại theo độ chênh của máu tối đa, nên trong chốc lát client hiển thị TỬ SỐ cũ (vd
     * {@code 100 / 118}) cho tới gói mang HP tiếp theo. MẪU SỐ — thứ sai trước ADR-043 — nay đúng
     * ngay. Muốn đúng cả tử số thì phải làm phương án (b) của D5 (đợi tick).
     */
    private int applyEquipmentBonusAndComputeMaxHp(long characterId) {
        EquipmentBonusSnapshot bonus = currentEquipmentBonus(characterId);
        currentWorldFacade(characterId).applyEquipmentBonus(characterId, bonus);
        return WorldFacade.characterMaxHp(bonus);
    }

    /**
     * Cầu nối {@code feature-inventory} → {@code server-world} cho chỉ số trang bị (ADR-036 D2).
     * server-world KHÔNG biết feature nào tồn tại (Điều 23), nên bản dịch {@code EquipmentBonus} →
     * {@link EquipmentBonusSnapshot} phải nằm ở gateway — đúng chỗ đã dịch mọi thứ khác giữa 2 bên.
     *
     * <p>Gọi ở BA nơi và chỉ ba nơi đó: lúc attach (nếu thiếu, đồ mặc từ phiên trước mất tác dụng
     * khi đăng nhập lại), lúc mặc đồ thành công, lúc gỡ đồ thành công. Chạm DB — chỉ gọi trên
     * {@code authExecutor} (Virtual Thread), không bao giờ trên EventLoop.
     */
    private EquipmentBonusSnapshot currentEquipmentBonus(long characterId) {
        EquipmentBonus bonus = inventoryFacade.computeEquipmentBonus(characterId);
        return new EquipmentBonusSnapshot(bonus.bonusAtk(), bonus.bonusDef(), bonus.bonusRes(), bonus.bonusHp());
    }

    /**
     * Hai chỉ số VÔ HƯỚNG của chính người chơi phải có số NGAY lúc vào world, không đợi sự kiện đầu
     * tiên: vạch EXP (2026-08-09) và số vàng (ADR-035 Decision-3 — "người chơi chỉ phát hiện mình
     * nghèo sau khi bấm mua"). Cả hai chỉ ĐỌC, không ghi gì.
     *
     * <p>Tách thành method riêng để {@code handleSelectCharacterBlocking} không vượt Checkstyle
     * MethodLength. Đang trên {@code authExecutor} (Virtual Thread) nên chạm DB ở đây an toàn.
     */
    private void sendInitialSelfState(long characterId) {
        // amount=0 = chỉ đọc trạng thái hiện tại, không cộng gì (xem GrantExperienceUseCase).
        worldGateway.registry().sendExperience(characterId, characterFacade.grantExperience(characterId, 0L));
        // delta=0: đây là ĐỒNG BỘ số dư, không phải một lần vàng đổi. Mọi lần đổi THẬT tự phát từ
        // GoldLedger (server-database) qua GoldChangeListener, không đi qua đây.
        worldGateway
                .registry()
                .sendGoldChanged(characterId, characterFacade.loadGold(characterId), 0L, REASON_ENTER_WORLD);
    }

    // SelectFactionRequest chạm DB (findById + UPDATE) — cần authExecutor, cùng pattern
    // handleCreateCharacter/handleSelectCharacter. characterId từ boundCharacterId (session, ĐÃ
    // bound ở SelectCharacterRequest) — KHÔNG có field character_id trong packet (Điều 24).
    private void handleSelectFactionRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SelectFactionRequest request = SelectFactionRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        if (request.getFaction() == Faction.FACTION_UNSPECIFIED || request.getFaction() == Faction.UNRECOGNIZED) {
            sendError(ctx, sequenceId, 400, "error.invalid_faction");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleSelectFactionBlocking(ctx, sequenceId, characterId, request));
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleSelectFactionBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SelectFactionRequest request) {
        try {
            characterFacade.selectFaction(characterId, toDomainFaction(request.getFaction()));
            // Ngoại hình cache ở registry được ghi MỘT LẦN lúc vào world — không cập nhật ở đây thì
            // mọi CharacterSpawnedEvent phát về sau vẫn mang hệ phái CŨ, xem
            // ConnectedCharacterRegistry#updateAppearance.
            worldGateway.registry().updateAppearance(characterId, CharacterAppearance.of(request.getFaction()));
            // ADR-045 — người ĐANG nhìn thấy nhân vật này (và chính họ) mới biết model đã đổi. Phải
            // đi qua hàng đợi world vì AoI chỉ đọc được trên tick thread. Chưa vào World nào thì
            // không có ai đang nhìn — bỏ qua, lần vào world sau CharacterSpawnedEvent đã mang hệ
            // phái mới nhờ updateAppearance ở dòng trên.
            currentWorldFacadeIfPresent(characterId).ifPresent(facade -> facade.enqueueAppearanceChanged(characterId));
            SelectFactionResponse response =
                    SelectFactionResponse.newBuilder().setSuccess(true).build();
            send(ctx, Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (CharacterNotFoundException e) {
            LOG.warn("Chọn hệ phái thất bại: không tìm thấy nhân vật", e);
            sendError(ctx, sequenceId, 404, "error.character_not_found");
        } catch (FactionSelectionLevelTooLowException e) {
            LOG.warn("Chọn hệ phái thất bại: chưa đủ cấp", e);
            sendError(ctx, sequenceId, 400, "error.level_too_low");
        } catch (RuntimeException e) {
            LOG.error("Chọn hệ phái thất bại: lỗi không mong đợi", e);
            sendError(ctx, sequenceId, 500, "error.select_faction_failed");
        }
    }

    /**
     * World nhân vật này ĐANG THẬT SỰ ở trong — dungeon nếu đã {@code EnterDungeonCommand}, duel
     * Arena nếu đang đấu (Phase 8 lát cắt 4), khu vực (channel) map chính họ đang được định tuyến
     * vào nếu không cái nào (Phase 8 lát cắt 1 ADR-026 + Phase 9 lát cắt 6 nhiều khu vực). Mọi lệnh
     * gameplay per-tick (move/target/skill/chat) phải qua đây thay vì gọi thẳng 1 World cố định,
     * nếu không lệnh của nhân vật trong dungeon/duel/khu vực khác sẽ lạc vào World sai (nơi entity
     * của họ đã bị detach). 1 nhân vật không bao giờ ở quá 1 trong 3 chỗ này cùng lúc (xem giới hạn
     * CHỦ Ý ở {@code ArenaInstanceRegistry} javadoc) nên thứ tự kiểm tra không quan trọng.
     */
    private WorldFacade currentWorldFacade(long characterId) {
        return currentWorldFacadeIfPresent(characterId)
                .orElseThrow(() -> new IllegalStateException(
                        "Nhân vật online nhưng không thuộc World nào — characterId=" + characterId));
    }

    /**
     * Như {@link #currentWorldFacade} nhưng RỖNG thay vì ném, cho những đường đi hợp lệ cả khi
     * nhân vật chưa vào World nào — ví dụ đổi hệ phái ngay sau khi chọn nhân vật (ADR-045).
     */
    private Optional<WorldFacade> currentWorldFacadeIfPresent(long characterId) {
        return worldGateway
                .dungeonInstanceRegistry()
                .worldFacadeFor(characterId)
                .or(() -> worldGateway.arenaInstanceRegistry().worldFacadeFor(characterId))
                .or(() -> worldGateway.mainWorldChannels().worldFacadeFor(characterId));
    }

    // MoveCommand KHÔNG chạm DB/Redis — chỉ enqueue vào server-world (rẻ, thread-safe), nên xử
    // lý thẳng trên EventLoop, không cần authExecutor (SERVER.md §2: "EventLoop được: decode,
    // validate rẻ, enqueue").
    private void handleMoveCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        MoveCommand command = MoveCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // NETWORK.md §13: vượt ngưỡng 1 window → drop packet + log (KHÔNG trả ErrorResponse — im
        // lặng, tránh cho client gian lận biết chính xác ngưỡng để dò); vượt liên tục → ngắt kết nối.
        if (!moveCommandRateLimiter.tryAcquire()) {
            networkMetrics.recordRateLimitRejected("move");
            LOG.warn("MoveCommand vượt rate limit: characterId={}", characterId);
            if (moveCommandRateLimiter.shouldDisconnect()) {
                LOG.warn("MoveCommand vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
                ctx.close();
            }
            return;
        }
        currentWorldFacade(characterId)
                .enqueueMoveCommand(characterId, sequenceId, command.getDirX(), command.getDirY());
    }

    // ADR-033 — JumpCommand KHÔNG chạm DB/Redis, cùng lý do xử lý thẳng trên EventLoop như
    // handleMoveCommand. Dùng CHUNG moveCommandRateLimiter (không tạo limiter riêng): nhảy là hành
    // động di chuyển liên quan, và world tự chặn double-jump qua điều kiện grounded (WorldFacade.
    // handleJumpIntent) nên rate limit riêng không thêm phòng thủ thật nào.
    private void handleJumpCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        JumpCommand.parseFrom(payload); // message rỗng — parse chỉ để validate wire format.
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        if (!moveCommandRateLimiter.tryAcquire()) {
            networkMetrics.recordRateLimitRejected("move");
            LOG.warn("JumpCommand vượt rate limit: characterId={}", characterId);
            if (moveCommandRateLimiter.shouldDisconnect()) {
                LOG.warn("JumpCommand vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
                ctx.close();
            }
            return;
        }
        currentWorldFacade(characterId).enqueueJumpCommand(characterId, sequenceId);
    }

    // SetTargetCommand KHÔNG chạm DB — chỉ enqueue vào server-world (rẻ, thread-safe), xử lý
    // thẳng trên EventLoop giống handleMoveCommand. GDD §6 "Auto-target": đây KHÔNG phải lệnh
    // "đánh 1 nhát" nên không cần rate limit riêng như MoveCommand (tần suất thấp — người chơi
    // chọn mục tiêu, không lặp lại mỗi tick).
    private void handleSetTargetCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SetTargetCommand command = SetTargetCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        currentWorldFacade(characterId).setTarget(characterId, command.getTargetEntityId());
    }

    // AttackCommand KHÔNG chạm DB — chỉ enqueue vào server-world, xử lý thẳng trên EventLoop giống
    // handleSetTargetCommand. Không rate limit riêng: cooldown đánh thường (CombatSystem, 1 đòn/
    // giây) tự chặn tần suất THẬT — spam gói quá cooldown chỉ là no-op, cùng lý do UseSkillCommand
    // bên dưới không có rate limit.
    private void handleAttackCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AttackCommand.parseFrom(payload); // message rỗng — parse chỉ để validate wire format.
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        currentWorldFacade(characterId).attack(characterId);
    }

    // UseSkillCommand KHÔNG chạm DB — chỉ enqueue vào server-world, xử lý thẳng trên EventLoop
    // giống handleSetTargetCommand. Cooldown skill tự giới hạn tần suất thật (SkillSystem) nên
    // không cần rate limit riêng — spam quá cooldown chỉ là no-op, không ảnh hưởng gameplay.
    private void handleUseSkillCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UseSkillCommand command = UseSkillCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        currentWorldFacade(characterId).useSkill(characterId, command.getSkillCode(), command.getTargetEntityId());
    }

    // EquipItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleSelectCharacter.
    private void handleEquipItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        EquipItemCommand command = EquipItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleEquipItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleEquipItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, EquipItemCommand command) {
        EquipmentSlotType slot = toDomainSlot(command.getSlot());
        if (slot == null) {
            sendError(ctx, sequenceId, 400, "error.invalid_equipment_slot");
            return;
        }
        // ADR-036 D4 — cấp nhân vật ĐỌC TỪ DB ngay tại đây, không lấy từ client và không cache:
        // người chơi có thể vừa lên cấp xong (GrantExperienceUseCase) trong cùng phiên.
        int characterLevel =
                characterFacade.findById(characterId).map(Character::level).orElse(0);
        InventoryFacade.EquipResult result =
                inventoryFacade.equipItem(characterId, command.getItemId(), slot, characterLevel);
        int maxHp = 0;
        if (result == InventoryFacade.EquipResult.OK) {
            // GAMEPLAY.md §2 — mặc đồ xong phải tính lại chỉ số dẫn xuất NGAY, không đợi lần
            // attach kế tiếp (server-world không tự biết trang bị vừa đổi).
            maxHp = applyEquipmentBonusAndComputeMaxHp(characterId);
        }
        EquipItemResponse response = EquipItemResponse.newBuilder()
                .setSuccess(result == InventoryFacade.EquipResult.OK)
                .setErrorKey(toErrorKey(result))
                .setMaxHp(maxHp)
                .build();
        send(ctx, Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // UnequipItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleEquipItemCommand.
    private void handleUnequipItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UnequipItemCommand command = UnequipItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleUnequipItemBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleUnequipItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, UnequipItemCommand command) {
        EquipmentSlotType slot = toDomainSlot(command.getSlot());
        if (slot == null) {
            sendError(ctx, sequenceId, 400, "error.invalid_equipment_slot");
            return;
        }
        InventoryFacade.UnequipResult result = inventoryFacade.unequipItem(characterId, slot);
        int maxHp = 0;
        if (result == InventoryFacade.UnequipResult.OK) {
            maxHp = applyEquipmentBonusAndComputeMaxHp(characterId);
        }
        UnequipItemResponse response = UnequipItemResponse.newBuilder()
                .setSuccess(result == InventoryFacade.UnequipResult.OK)
                .setErrorKey(result == InventoryFacade.UnequipResult.OK ? "" : "error.slot_not_equipped")
                .setMaxHp(maxHp)
                .build();
        send(ctx, Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // EnhanceItemCommand chạm DB (feature-inventory) — Virtual Thread, giống handleEquipItemCommand.
    private void handleEnhanceItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        EnhanceItemCommand command = EnhanceItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // Context.current() PHẢI chụp NGAY ở đây (còn trên EventLoop, bên trong span "gateway.command"
        // của channelRead0) — Virtual Thread chạy handleEnhanceItemBlocking không tự thấy context đó.
        // Gộp characterId/command/tracingContext vào 1 record — tránh vượt Checkstyle ParameterNumber
        // (max 4) mà không cần đổi field instance (per-connection nhưng có thể pipeline nhiều lệnh).
        EnhanceItemRequest request = new EnhanceItemRequest(sequenceId, characterId, command, Context.current());
        executeBlocking(ctx, sequenceId, () -> handleEnhanceItemBlocking(ctx, request));
    }

    private record EnhanceItemRequest(
            int sequenceId, long characterId, EnhanceItemCommand command, Context tracingContext) {}

    /**
     * Luồng ĐẠI DIỆN duy nhất được retrofit span con sâu (feature + DB gộp làm 1, xem javadoc
     * {@link GatewayTracing}) khớp ví dụ đầy đủ OBSERVABILITY.md §6 — CHƯA làm cho ~50 opcode còn
     * lại (phạm vi lát cắt 9, không phải thiếu sót).
     */
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"}) // xem giải thích ở channelRead0
    private void handleEnhanceItemBlocking(ChannelHandlerContext ctx, EnhanceItemRequest request) {
        Span span = tracing.startChildSpan("feature.inventory.enhance", request.tracingContext());
        try (Scope scope = span.makeCurrent()) {
            InventoryFacade.EnhanceResult result = inventoryFacade.enhanceItem(
                    request.characterId(),
                    request.command().getItemId(),
                    request.command().getUseProtection(),
                    request.command().getIdempotencyKey());
            networkMetrics.recordEnhanceResult(result.name());
            span.setAttribute("result", result.name());
            // ADR-043 — tôi luyện ĐỔI `enhance_level`, mà ADR-036 D2/D3 nhân hệ số cường hoá thẳng
            // vào `bonus_hp`/`bonus_atk`. Trước 2026-09-05 nhánh này KHÔNG HỀ gọi
            // `applyEquipmentBonus`, nên tôi luyện một món ĐANG MẶC không đổi chỉ số thật cho tới
            // lần đăng nhập sau — sai ở CHÍNH SERVER, không chỉ ở hiển thị. Cùng luật GAMEPLAY.md
            // §2 mà mặc/gỡ đồ đã tuân từ ADR-036.
            int maxHp = enhanceChangedStats(result) ? applyEquipmentBonusAndComputeMaxHp(request.characterId()) : 0;
            EnhanceItemResponse response = EnhanceItemResponse.newBuilder()
                    .setOutcome(toProtoOutcome(result))
                    .setMaxHp(maxHp)
                    .build();
            send(ctx, Opcode.OPCODE_ENHANCE_ITEM_RESPONSE_VALUE, request.sequenceId(), response.toByteArray());
        } finally {
            span.end();
        }
    }

    /**
     * ADR-046 D5 — dùng vật phẩm (đan dược). Chạm DB (trừ một cái trong túi + audit) nên đẩy sang
     * Virtual Thread, KHÔNG chặn EventLoop — cùng pattern {@link #handleEnhanceItemCommand}.
     */
    private void handleUseItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UseItemCommand command = UseItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        long inventoryItemId = command.getInventoryItemId();
        executeBlocking(ctx, sequenceId, () -> handleUseItemBlocking(ctx, sequenceId, characterId, inventoryItemId));
    }

    /**
     * Hai nửa của một lần uống đan: {@code feature-inventory} trừ món (transaction + audit) rồi trả
     * về hiệu ứng, {@code server-world} cộng máu. Gateway là chỗ DUY NHẤT nối được hai bên — cùng
     * hình dạng {@code onMonsterKilled} → {@code grantLoot}.
     *
     * <p>Máu mới KHÔNG đi kèm response: hàng đợi world là bất đồng bộ nên lúc này lệnh còn chưa
     * chạy (bẫy đã trả giá ở ADR-043). Client nhận số mới qua {@code HealthChangedEvent} (408).
     */
    private void handleUseItemBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, long inventoryItemId) {
        UseItemUseCase.UseItemResult result = inventoryFacade.useItem(characterId, inventoryItemId);
        if (result.outcome() == UseItemUseCase.Outcome.OK && result.effect() == ItemUseEffect.HEAL_HP) {
            // RỖNG nếu nhân vật vừa rớt mạng giữa chừng — món đã trừ rồi, nhưng không có entity
            // nào để cộng máu và cũng không còn ai để gửi gói. Không phải lỗi, đừng ném.
            currentWorldFacadeIfPresent(characterId)
                    .ifPresent(facade -> facade.enqueueHealCharacter(characterId, result.value()));
        }
        UseItemResponse response = UseItemResponse.newBuilder()
                .setSuccess(result.outcome() == UseItemUseCase.Outcome.OK)
                .setErrorKey(useItemErrorKey(result.outcome()))
                .setRemainingQuantity(result.remainingQuantity())
                .build();
        send(ctx, Opcode.OPCODE_USE_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static dev.kage.protocol.ItemUseEffect toProtoUseEffect(ItemUseEffect effect) {
        if (effect == null) {
            return dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_UNSPECIFIED;
        }
        return switch (effect) {
            case HEAL_HP -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_HEAL_HP;
        };
    }

    private static String useItemErrorKey(UseItemUseCase.Outcome outcome) {
        return switch (outcome) {
            case OK -> "";
            case ITEM_NOT_FOUND -> "error.item_not_found";
            case NOT_USABLE -> "error.item_not_usable";
        };
    }

    // Bảng tra thay switch 6 nhánh (PMD CyclomaticComplexity — cùng lý do SLOT_MAPPING bên dưới).
    private static final Map<InventoryFacade.EnhanceResult, EnhanceOutcome> ENHANCE_OUTCOME_MAPPING = Map.of(
            InventoryFacade.EnhanceResult.SUCCESS, EnhanceOutcome.ENHANCE_OUTCOME_SUCCESS,
            InventoryFacade.EnhanceResult.FAILED_NO_DROP, EnhanceOutcome.ENHANCE_OUTCOME_FAILED_NO_DROP,
            InventoryFacade.EnhanceResult.FAILED_DROPPED, EnhanceOutcome.ENHANCE_OUTCOME_FAILED_DROPPED,
            InventoryFacade.EnhanceResult.FAILED_PROTECTED, EnhanceOutcome.ENHANCE_OUTCOME_FAILED_PROTECTED,
            InventoryFacade.EnhanceResult.ITEM_NOT_FOUND, EnhanceOutcome.ENHANCE_OUTCOME_ITEM_NOT_FOUND,
            InventoryFacade.EnhanceResult.MAX_LEVEL, EnhanceOutcome.ENHANCE_OUTCOME_MAX_LEVEL,
            InventoryFacade.EnhanceResult.INSUFFICIENT_GOLD, EnhanceOutcome.ENHANCE_OUTCOME_INSUFFICIENT_GOLD,
            InventoryFacade.EnhanceResult.DUPLICATE_REQUEST, EnhanceOutcome.ENHANCE_OUTCOME_DUPLICATE_REQUEST);

    private static EnhanceOutcome toProtoOutcome(InventoryFacade.EnhanceResult result) {
        return ENHANCE_OUTCOME_MAPPING.get(result);
    }

    // --- ADR-035 (Accepted 2026-08-27): 3 request đọc-thuần mở khoá túi đồ/catalog/nhật ký quest ---
    // Cả 3 chạm DB (feature-inventory / feature-quest) nên đẩy sang Virtual Thread, KHÔNG chặn
    // EventLoop — cùng pattern handleListShopItemsRequest/handleListFriendsRequest ngay dưới đây.
    // characterId LUÔN lấy từ boundCharacterId (session), KHÔNG bao giờ từ payload: 2 trong 3 packet
    // này cố ý không có field nào (SECURITY.md §4, chống IDOR).

    private void handleListInventoryRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListInventoryRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListInventoryBlocking(ctx, sequenceId, characterId));
    }

    private void handleListInventoryBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        InventorySnapshot snapshot = inventoryFacade.listInventory(characterId);
        ListInventoryResponse.Builder response = ListInventoryResponse.newBuilder()
                .setCapacity(snapshot.inventory().capacity());
        List<ItemStack> items = snapshot.inventory().items();
        for (int slotIndex = 0; slotIndex < items.size(); slotIndex++) {
            ItemStack stack = items.get(slotIndex);
            // slot_index = VỊ TRÍ trong danh sách, đúng bằng giá trị PostgresInventoryAdapter ghi
            // vào cột slot_index lúc save (nó cũng đánh số theo vị trí) — không có nguồn thứ hai.
            response.addItems(InventoryItemView.newBuilder()
                    .setItemId(stack.id())
                    .setItemTemplateId(stack.itemTemplateId())
                    .setQuantity(stack.quantity())
                    .setEnhanceLevel(stack.enhanceLevel())
                    .setSlotIndex(slotIndex)
                    .build());
        }
        snapshot.equipment()
                .slots()
                .forEach((slot, itemId) -> response.addEquipped(EquippedSlotView.newBuilder()
                        .setSlot(toProtoSlot(slot))
                        .setItemId(itemId)
                        .build()));
        send(
                ctx,
                Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE,
                sequenceId,
                response.build().toByteArray());
    }

    private void handleGetItemCatalogRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetItemCatalogRequest request = GetItemCatalogRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleGetItemCatalogBlocking(ctx, sequenceId, request.getKnownVersion()));
    }

    /**
     * SERVER phán quyết cache của client còn hợp lệ hay không (ADR-035 Decision-2, phương án A′) —
     * client chỉ gửi lại chuỗi nó đang giữ, không tự kết luận gì. Khớp thì trả {@code unchanged} +
     * danh sách RỖNG (vài chục byte); không khớp thì gửi TOÀN BỘ catalog (thay hẳn, không cộng dồn).
     */
    private void handleGetItemCatalogBlocking(ChannelHandlerContext ctx, int sequenceId, String knownVersion) {
        ItemCatalog catalog = inventoryFacade.itemCatalog();
        boolean unchanged = catalog.version().equals(knownVersion);
        ItemCatalogResponse.Builder response =
                ItemCatalogResponse.newBuilder().setUnchanged(unchanged).setVersion(catalog.version());
        if (!unchanged) {
            catalog.entries()
                    .forEach(entry -> response.addEntries(dev.kage.protocol.ItemCatalogEntry.newBuilder()
                            .setId(entry.id())
                            .setCode(entry.code())
                            .setNameKey(entry.nameKey())
                            .setMaxStack(entry.maxStack())
                            // equipSlot null (item không mặc được) -> UNSPECIFIED, khớp equip_slot NULL trong DB.
                            .setEquipSlot(toProtoSlot(entry.equipSlot()))
                            .setBonusAtk(entry.bonusAtk())
                            .setBonusDef(entry.bonusDef())
                            .setBonusRes(entry.bonusRes())
                            .setBonusHp(entry.bonusHp())
                            .setRequiredLevel(entry.requiredLevel())
                            // ADR-046 D5 — useEffect null (món không dùng được) -> UNSPECIFIED,
                            // khớp use_effect NULL trong DB, cùng luật equipSlot ở trên.
                            .setUseEffect(toProtoUseEffect(entry.useEffect()))
                            .setUseValue(entry.useValue())
                            .build()));
        }
        send(
                ctx,
                Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE,
                sequenceId,
                response.build().toByteArray());
    }

    private void handleListQuestsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListQuestsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListQuestsBlocking(ctx, sequenceId, characterId));
    }

    private void handleListQuestsBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListQuestsResponse.Builder response = ListQuestsResponse.newBuilder();
        for (dev.kage.quest.domain.QuestLogEntry entry : questFacade.listQuests(characterId)) {
            response.addQuests(dev.kage.protocol.QuestLogEntry.newBuilder()
                    .setQuestCode(entry.questCode())
                    .setStatus(toProtoQuestStatus(entry.status()))
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE,
                sequenceId,
                response.build().toByteArray());
    }

    private static final Map<dev.kage.quest.domain.QuestStatus, dev.kage.protocol.QuestStatus> QUEST_STATUS_MAPPING =
            Map.of(
                    dev.kage.quest.domain.QuestStatus.IN_PROGRESS,
                            dev.kage.protocol.QuestStatus.QUEST_STATUS_IN_PROGRESS,
                    dev.kage.quest.domain.QuestStatus.COMPLETED, dev.kage.protocol.QuestStatus.QUEST_STATUS_COMPLETED,
                    dev.kage.quest.domain.QuestStatus.CLAIMED, dev.kage.protocol.QuestStatus.QUEST_STATUS_CLAIMED);

    private static dev.kage.protocol.QuestStatus toProtoQuestStatus(dev.kage.quest.domain.QuestStatus status) {
        return QUEST_STATUS_MAPPING.get(status);
    }

    private void handleListShopItemsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListShopItemsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListShopItemsBlocking(ctx, sequenceId));
    }

    private void handleListShopItemsBlocking(ChannelHandlerContext ctx, int sequenceId) {
        send(
                ctx,
                Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE,
                sequenceId,
                buildShopListingsResponse().toByteArray());
    }

    /** Gộp lại — dùng cả cho {@code ListShopItemsRequest} thật lẫn nhánh action="open_shop" của dialogue. */
    private ListShopItemsResponse buildShopListingsResponse() {
        List<ShopListingView> listings = shopFacade.listItems().stream()
                .map(listing -> ShopListingView.newBuilder()
                        .setItemTemplateId(listing.itemTemplateId())
                        .setBuyPrice(listing.buyPrice())
                        .setSellPrice(listing.sellPrice())
                        .build())
                .toList();
        return ListShopItemsResponse.newBuilder().addAllListings(listings).build();
    }

    private void handleBuyItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        BuyItemCommand command = BuyItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // Context.current() PHẢI chụp NGAY ở đây (còn trên EventLoop, bên trong span "gateway.command"
        // của channelRead0) — cùng lý do EnhanceItemRequest ở handleEnhanceItemCommand.
        BuyItemRequest request = new BuyItemRequest(sequenceId, characterId, command, Context.current());
        executeBlocking(ctx, sequenceId, () -> handleBuyItemBlocking(ctx, request));
    }

    private record BuyItemRequest(int sequenceId, long characterId, BuyItemCommand command, Context tracingContext) {}

    /**
     * BuyItemCommand chạm gold thật (cùng lớp rủi ro cường hoá) — retrofit span con sâu
     * "feature.shop.buy" khớp pattern {@link #handleEnhanceItemBlocking} (OBSERVABILITY.md §6).
     */
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"}) // xem giải thích ở channelRead0
    private void handleBuyItemBlocking(ChannelHandlerContext ctx, BuyItemRequest request) {
        Span span = tracing.startChildSpan("feature.shop.buy", request.tracingContext());
        try (Scope scope = span.makeCurrent()) {
            ShopFacade.BuyResult result = shopFacade.buyItem(
                    request.characterId(),
                    request.command().getItemTemplateId(),
                    request.command().getQuantity(),
                    request.command().getIdempotencyKey());
            span.setAttribute("result", result.name());
            BuyItemResponse response = BuyItemResponse.newBuilder()
                    .setOutcome(toProtoOutcome(result))
                    .build();
            send(ctx, Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE, request.sequenceId(), response.toByteArray());
        } finally {
            span.end();
        }
    }

    private static final Map<ShopFacade.BuyResult, BuyOutcome> BUY_OUTCOME_MAPPING = Map.of(
            ShopFacade.BuyResult.OK, BuyOutcome.BUY_OUTCOME_OK,
            ShopFacade.BuyResult.ITEM_NOT_IN_SHOP, BuyOutcome.BUY_OUTCOME_ITEM_NOT_IN_SHOP,
            ShopFacade.BuyResult.INSUFFICIENT_GOLD, BuyOutcome.BUY_OUTCOME_INSUFFICIENT_GOLD,
            ShopFacade.BuyResult.INVENTORY_FULL, BuyOutcome.BUY_OUTCOME_INVENTORY_FULL,
            ShopFacade.BuyResult.DUPLICATE_REQUEST, BuyOutcome.BUY_OUTCOME_DUPLICATE_REQUEST);

    private static BuyOutcome toProtoOutcome(ShopFacade.BuyResult result) {
        return BUY_OUTCOME_MAPPING.get(result);
    }

    private void handleSellItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SellItemCommand command = SellItemCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // Context.current() PHẢI chụp NGAY ở đây — cùng lý do handleBuyItemCommand.
        SellItemRequest request = new SellItemRequest(sequenceId, characterId, command, Context.current());
        executeBlocking(ctx, sequenceId, () -> handleSellItemBlocking(ctx, request));
    }

    private record SellItemRequest(int sequenceId, long characterId, SellItemCommand command, Context tracingContext) {}

    /**
     * SellItemCommand chạm gold thật (cùng lớp rủi ro cường hoá/mua đồ) — retrofit span con sâu
     * "feature.shop.sell" khớp pattern {@link #handleEnhanceItemBlocking} (OBSERVABILITY.md §6).
     */
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"}) // xem giải thích ở channelRead0
    private void handleSellItemBlocking(ChannelHandlerContext ctx, SellItemRequest request) {
        Span span = tracing.startChildSpan("feature.shop.sell", request.tracingContext());
        try (Scope scope = span.makeCurrent()) {
            ShopFacade.SellResult result = shopFacade.sellItem(
                    request.characterId(),
                    request.command().getItemId(),
                    request.command().getQuantity(),
                    request.command().getIdempotencyKey());
            span.setAttribute("result", result.name());
            SellItemResponse response = SellItemResponse.newBuilder()
                    .setOutcome(toProtoOutcome(result))
                    .build();
            send(ctx, Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE, request.sequenceId(), response.toByteArray());
        } finally {
            span.end();
        }
    }

    private static final Map<ShopFacade.SellResult, SellOutcome> SELL_OUTCOME_MAPPING = Map.of(
            ShopFacade.SellResult.OK, SellOutcome.SELL_OUTCOME_OK,
            ShopFacade.SellResult.ITEM_NOT_FOUND, SellOutcome.SELL_OUTCOME_ITEM_NOT_FOUND,
            ShopFacade.SellResult.ITEM_NOT_SELLABLE, SellOutcome.SELL_OUTCOME_ITEM_NOT_SELLABLE,
            ShopFacade.SellResult.INSUFFICIENT_QUANTITY, SellOutcome.SELL_OUTCOME_INSUFFICIENT_QUANTITY,
            ShopFacade.SellResult.DUPLICATE_REQUEST, SellOutcome.SELL_OUTCOME_DUPLICATE_REQUEST,
            // Không thêm giá trị mới vào shop.proto: ITEM_NOT_SELLABLE là đúng ở trạng thái hiện
            // tại, và câu chữ phía client (shop.error.item_not_sellable) đã nhắc "đang mặc thì gỡ ra".
            ShopFacade.SellResult.ITEM_EQUIPPED, SellOutcome.SELL_OUTCOME_ITEM_NOT_SELLABLE);

    private static SellOutcome toProtoOutcome(ShopFacade.SellResult result) {
        return SELL_OUTCOME_MAPPING.get(result);
    }

    private void handleTalkToNpcCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        TalkToNpcCommand command = TalkToNpcCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleTalkToNpcBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleTalkToNpcBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, TalkToNpcCommand command) {
        Optional<DialogueNode> node = dialogueFacade.startDialogue(command.getNpcCode());
        if (node.isEmpty()) {
            sendError(ctx, sequenceId, 404, "error.npc_not_found");
            return;
        }
        send(
                ctx,
                Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
                sequenceId,
                toResponse(node.get()).toByteArray());
        pushQuestProgressFromTalking(ctx, characterId, command.getNpcCode());
    }

    /**
     * Mục tiêu quest loại THỨ HAI — "đi gặp X" (2026-09-09, nhịp mở arc 2). Chạy SAU khi cây hội
     * thoại đã đi ra dây: người chơi phải thấy NPC nói gì trước, thông báo hoàn thành quest tới sau.
     *
     * <p>⚠️ NPC KHÔNG TỒN TẠI thì không có sự kiện nào cả — nhánh {@code node.isEmpty()} phía trên
     * đã {@code return}. Nói cách khác quest chỉ nhích khi hội thoại THẬT SỰ mở được, không nhích
     * khi client gửi một mã NPC bịa ra (Điều 3 — server phán quyết, không tin client).
     *
     * <p>Cùng hình dạng {@code ConnectedCharacterRegistry.processQuestBlocking} (đường quái chết):
     * {@code sequenceId = 0} vì đây là sự kiện server tự đẩy, không phải phản hồi cho lệnh nào.
     */
    private void pushQuestProgressFromTalking(ChannelHandlerContext ctx, long characterId, String npcCode) {
        List<QuestFacade.QuestStatusChange> completed = questFacade.onNpcTalked(characterId, npcCode);
        for (QuestFacade.QuestStatusChange statusChange : completed) {
            QuestStatusChangedEvent event = QuestStatusChangedEvent.newBuilder()
                    .setQuestCode(statusChange.questCode())
                    .setStatus(toProtoQuestStatus(statusChange.status()))
                    .build();
            send(ctx, Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    private void handleSelectDialogueOptionCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SelectDialogueOptionCommand command = SelectDialogueOptionCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleSelectDialogueOptionBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleSelectDialogueOptionBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SelectDialogueOptionCommand command) {
        DialogueFacade.SelectOptionResult result =
                dialogueFacade.selectOption(command.getNpcCode(), command.getCurrentNodeId(), command.getOptionIndex());
        switch (result) {
            case DialogueFacade.SelectOptionResult.Node r ->
                send(
                        ctx,
                        Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
                        sequenceId,
                        toResponse(r.node()).toByteArray());
            case DialogueFacade.SelectOptionResult.OpenShop ignored ->
                send(
                        ctx,
                        Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE,
                        sequenceId,
                        buildShopListingsResponse().toByteArray());
            case DialogueFacade.SelectOptionResult.Ended ignored ->
                send(
                        ctx,
                        Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE,
                        sequenceId,
                        DialogueEndedResponse.newBuilder().build().toByteArray());
            case DialogueFacade.SelectOptionResult.NotFound ignored ->
                sendError(ctx, sequenceId, 404, "error.dialogue_option_not_found");
            case DialogueFacade.SelectOptionResult.AcceptQuest r -> {
                QuestFacade.AcceptResult acceptResult = questFacade.acceptQuest(characterId, r.questCode());
                AcceptQuestResponse response = AcceptQuestResponse.newBuilder()
                        .setOutcome(toProtoOutcome(acceptResult))
                        .build();
                send(ctx, Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
            }
        }
    }

    private static final Map<QuestFacade.AcceptResult, AcceptQuestOutcome> ACCEPT_QUEST_OUTCOME_MAPPING = Map.of(
            QuestFacade.AcceptResult.OK,
            AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_OK,
            QuestFacade.AcceptResult.QUEST_NOT_FOUND,
            AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_QUEST_NOT_FOUND,
            QuestFacade.AcceptResult.ALREADY_ACCEPTED,
            AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_ALREADY_ACCEPTED,
            QuestFacade.AcceptResult.PREREQUISITE_NOT_MET,
            AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET,
            QuestFacade.AcceptResult.SCRIPT_ERROR,
            AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_SCRIPT_ERROR);

    private static AcceptQuestOutcome toProtoOutcome(QuestFacade.AcceptResult result) {
        return ACCEPT_QUEST_OUTCOME_MAPPING.get(result);
    }

    private void handleClaimQuestRewardCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ClaimQuestRewardCommand command = ClaimQuestRewardCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleClaimQuestRewardBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleClaimQuestRewardBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, ClaimQuestRewardCommand command) {
        QuestFacade.ClaimResult result =
                questFacade.claimReward(characterId, command.getQuestCode(), command.getIdempotencyKey());
        ClaimQuestRewardResponse.Builder responseBuilder = ClaimQuestRewardResponse.newBuilder()
                .setOutcome(toProtoOutcome(result.outcome()))
                .setExpGranted(result.expGranted());
        for (QuestFacade.ItemReward item : result.itemsGranted()) {
            responseBuilder.addItemsGranted(ItemRewardView.newBuilder()
                    .setItemTemplateCode(item.itemTemplateCode())
                    .setQuantity(item.quantity())
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
        if (result.outcome() == QuestFacade.ClaimOutcome.OK) {
            // EXP thưởng quest đi thẳng vào `character.exp` qua PostgresQuestExpAdapter, KHÔNG qua
            // GrantExperienceUseCase, nên client không hề biết vạch EXP đã đổi: playtest 2026-09-05
            // thấy nhận 500 EXP mà HUD vẫn đứng ở 20/200 cho tới khi đăng nhập lại (lúc đó
            // sendInitialSelfState mới đồng bộ). amount=0 = CHỈ ĐỌC trạng thái hiện tại rồi gửi,
            // đúng cách sendInitialSelfState làm lúc vào world — không cộng thêm lần nữa.
            worldGateway.registry().sendExperience(characterId, characterFacade.grantExperience(characterId, 0L));
        }
    }

    private static final Map<QuestFacade.ClaimOutcome, ClaimQuestRewardOutcome> CLAIM_QUEST_REWARD_OUTCOME_MAPPING =
            Map.of(
                    QuestFacade.ClaimOutcome.OK,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK,
                    QuestFacade.ClaimOutcome.QUEST_NOT_FOUND,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_QUEST_NOT_FOUND,
                    QuestFacade.ClaimOutcome.NOT_ACCEPTED,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_NOT_ACCEPTED,
                    QuestFacade.ClaimOutcome.NOT_COMPLETED_YET,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_NOT_COMPLETED_YET,
                    QuestFacade.ClaimOutcome.DUPLICATE_REQUEST,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_DUPLICATE_REQUEST,
                    QuestFacade.ClaimOutcome.SCRIPT_ERROR,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_SCRIPT_ERROR,
                    QuestFacade.ClaimOutcome.INVENTORY_FULL,
                    ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_INVENTORY_FULL);

    private static ClaimQuestRewardOutcome toProtoOutcome(QuestFacade.ClaimOutcome outcome) {
        return CLAIM_QUEST_REWARD_OUTCOME_MAPPING.get(outcome);
    }

    private void handleSendFriendRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendFriendRequestCommand command = SendFriendRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleSendFriendRequestBlocking(ctx, sequenceId, characterId, command));
    }

    // PMD AvoidCatchingGenericException: đúng ý — đây là ranh giới I/O bất đồng bộ (authExecutor,
    // Virtual Thread), PHẢI trả lời client dù lỗi gì xảy ra, cùng lý do handleConnectBlocking. Một
    // exception ở đây KHÔNG rơi vào exceptionCaught của pipeline (đang chạy trên Executor riêng,
    // không phải EventLoop) — nếu không bắt, client treo chờ response không bao giờ tới.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleSendFriendRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SendFriendRequestCommand command) {
        try {
            Optional<Character> target = characterFacade.findByName(command.getTargetName());
            if (target.isEmpty()) {
                SendFriendRequestResponse response = SendFriendRequestResponse.newBuilder()
                        .setOutcome(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND)
                        .build();
                send(ctx, Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
                return;
            }
            FriendFacade.SendRequestResult result =
                    friendFacade.sendRequest(characterId, target.get().id());
            SendFriendRequestResponse response = SendFriendRequestResponse.newBuilder()
                    .setOutcome(toProtoOutcome(result))
                    .build();
            send(ctx, Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Gửi lời mời kết bạn thất bại", e);
            sendError(ctx, sequenceId, 500, "error.send_friend_request_failed");
        }
    }

    private static final Map<FriendFacade.SendRequestResult, SendFriendRequestOutcome>
            SEND_FRIEND_REQUEST_OUTCOME_MAPPING = Map.of(
                    FriendFacade.SendRequestResult.OK,
                    SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK,
                    FriendFacade.SendRequestResult.CANNOT_FRIEND_SELF,
                    SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_CANNOT_FRIEND_SELF,
                    FriendFacade.SendRequestResult.ALREADY_FRIENDS,
                    SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS,
                    FriendFacade.SendRequestResult.REQUEST_ALREADY_PENDING,
                    SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING);

    private static SendFriendRequestOutcome toProtoOutcome(FriendFacade.SendRequestResult result) {
        return SEND_FRIEND_REQUEST_OUTCOME_MAPPING.get(result);
    }

    private void handleAcceptFriendRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AcceptFriendRequestCommand command = AcceptFriendRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleAcceptFriendRequestBlocking(ctx, sequenceId, characterId, command));
    }

    // PMD AvoidCatchingGenericException: cùng lý do handleSendFriendRequestBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleAcceptFriendRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, AcceptFriendRequestCommand command) {
        try {
            FriendFacade.AcceptResult result = friendFacade.accept(command.getFriendshipId(), characterId);
            AcceptFriendRequestOutcome outcome = result == FriendFacade.AcceptResult.OK
                    ? AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK
                    : AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND;
            AcceptFriendRequestResponse response =
                    AcceptFriendRequestResponse.newBuilder().setOutcome(outcome).build();
            send(ctx, Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Chấp nhận lời mời kết bạn thất bại", e);
            sendError(ctx, sequenceId, 500, "error.accept_friend_request_failed");
        }
    }

    private void handleRejectFriendRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RejectFriendRequestCommand command = RejectFriendRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleRejectFriendRequestBlocking(ctx, sequenceId, characterId, command));
    }

    // PMD AvoidCatchingGenericException: cùng lý do handleSendFriendRequestBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleRejectFriendRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, RejectFriendRequestCommand command) {
        try {
            FriendFacade.RejectResult result = friendFacade.reject(command.getFriendshipId(), characterId);
            RejectFriendRequestOutcome outcome = result == FriendFacade.RejectResult.OK
                    ? RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK
                    : RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_NOT_FOUND;
            RejectFriendRequestResponse response =
                    RejectFriendRequestResponse.newBuilder().setOutcome(outcome).build();
            send(ctx, Opcode.OPCODE_REJECT_FRIEND_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Từ chối lời mời kết bạn thất bại", e);
            sendError(ctx, sequenceId, 500, "error.reject_friend_request_failed");
        }
    }

    private void handleRemoveFriendCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RemoveFriendCommand command = RemoveFriendCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleRemoveFriendBlocking(ctx, sequenceId, characterId, command));
    }

    // PMD AvoidCatchingGenericException: cùng lý do handleSendFriendRequestBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleRemoveFriendBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, RemoveFriendCommand command) {
        try {
            Optional<Character> target = characterFacade.findByName(command.getTargetName());
            if (target.isEmpty()) {
                RemoveFriendResponse response = RemoveFriendResponse.newBuilder()
                        .setOutcome(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_TARGET_NOT_FOUND)
                        .build();
                send(ctx, Opcode.OPCODE_REMOVE_FRIEND_RESPONSE_VALUE, sequenceId, response.toByteArray());
                return;
            }
            FriendFacade.RemoveResult result =
                    friendFacade.remove(characterId, target.get().id());
            RemoveFriendOutcome outcome = result == FriendFacade.RemoveResult.OK
                    ? RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_OK
                    : RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_NOT_FRIENDS;
            RemoveFriendResponse response =
                    RemoveFriendResponse.newBuilder().setOutcome(outcome).build();
            send(ctx, Opcode.OPCODE_REMOVE_FRIEND_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Huỷ kết bạn thất bại", e);
            sendError(ctx, sequenceId, 500, "error.remove_friend_failed");
        }
    }

    private void handleListFriendsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListFriendsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListFriendsBlocking(ctx, sequenceId, characterId));
    }

    // PMD AvoidCatchingGenericException: cùng lý do handleSendFriendRequestBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleListFriendsBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        try {
            ListFriendsResponse.Builder responseBuilder = ListFriendsResponse.newBuilder();
            for (long friendId : friendFacade.listFriends(characterId)) {
                // findByName/findById chạm DB mỗi bạn — chấp nhận N+1 ở quy mô danh sách bạn bè MVP
                // (vài chục), không phải đường nóng mỗi tick (khác AoI/combat).
                String name =
                        characterFacade.findById(friendId).map(Character::name).orElse("");
                responseBuilder.addFriends(FriendView.newBuilder()
                        .setCharacterId(friendId)
                        .setName(name)
                        .setOnline(worldGateway.registry().isOnline(friendId))
                        .build());
            }
            send(
                    ctx,
                    Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE,
                    sequenceId,
                    responseBuilder.build().toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Liệt kê danh sách bạn bè thất bại", e);
            sendError(ctx, sequenceId, 500, "error.list_friends_failed");
        }
    }

    private void handleListPendingFriendRequestsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListPendingFriendRequestsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListPendingFriendRequestsBlocking(ctx, sequenceId, characterId));
    }

    // PMD AvoidCatchingGenericException: cùng lý do handleSendFriendRequestBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleListPendingFriendRequestsBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        try {
            ListPendingFriendRequestsResponse.Builder responseBuilder = ListPendingFriendRequestsResponse.newBuilder();
            for (FriendFacade.PendingRequestView pending : friendFacade.listPendingRequests(characterId)) {
                String requesterName = characterFacade
                        .findById(pending.requesterId())
                        .map(Character::name)
                        .orElse("");
                responseBuilder.addRequests(PendingFriendRequestView.newBuilder()
                        .setFriendshipId(pending.friendshipId())
                        .setRequesterId(pending.requesterId())
                        .setRequesterName(requesterName)
                        .build());
            }
            send(
                    ctx,
                    Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_RESPONSE_VALUE,
                    sequenceId,
                    responseBuilder.build().toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Liệt kê lời mời kết bạn đang chờ thất bại", e);
            sendError(ctx, sequenceId, 500, "error.list_pending_friend_requests_failed");
        }
    }

    private void handleCreateGuildCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CreateGuildCommand command = CreateGuildCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleCreateGuildBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleCreateGuildBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, CreateGuildCommand command) {
        GuildFacade.CreateResult result = guildFacade.createGuild(characterId, command.getName());
        CreateGuildResponse.Builder responseBuilder =
                CreateGuildResponse.newBuilder().setOutcome(toProtoOutcome(result.outcome()));
        if (result.guildId() != null) {
            responseBuilder.setGuildId(result.guildId());
        }
        send(
                ctx,
                Opcode.OPCODE_CREATE_GUILD_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private static final Map<GuildFacade.CreateOutcome, CreateGuildOutcome> CREATE_GUILD_OUTCOME_MAPPING = Map.of(
            GuildFacade.CreateOutcome.OK, CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK,
            GuildFacade.CreateOutcome.ALREADY_IN_GUILD, CreateGuildOutcome.CREATE_GUILD_OUTCOME_ALREADY_IN_GUILD,
            GuildFacade.CreateOutcome.NAME_TAKEN, CreateGuildOutcome.CREATE_GUILD_OUTCOME_NAME_TAKEN);

    private static CreateGuildOutcome toProtoOutcome(GuildFacade.CreateOutcome outcome) {
        return CREATE_GUILD_OUTCOME_MAPPING.get(outcome);
    }

    private void handleSendGuildJoinRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendGuildJoinRequestCommand command = SendGuildJoinRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleSendGuildJoinRequestBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleSendGuildJoinRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SendGuildJoinRequestCommand command) {
        GuildFacade.SendJoinRequestResult result = guildFacade.sendJoinRequest(characterId, command.getGuildName());
        SendGuildJoinRequestResponse response = SendGuildJoinRequestResponse.newBuilder()
                .setOutcome(toProtoOutcome(result))
                .build();
        send(ctx, Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<GuildFacade.SendJoinRequestResult, SendGuildJoinRequestOutcome>
            SEND_GUILD_JOIN_REQUEST_OUTCOME_MAPPING = Map.of(
                    GuildFacade.SendJoinRequestResult.OK,
                    SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_OK,
                    GuildFacade.SendJoinRequestResult.ALREADY_IN_GUILD,
                    SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_ALREADY_IN_GUILD,
                    GuildFacade.SendJoinRequestResult.GUILD_NOT_FOUND,
                    SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND,
                    GuildFacade.SendJoinRequestResult.REQUEST_ALREADY_PENDING,
                    SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING);

    private static SendGuildJoinRequestOutcome toProtoOutcome(GuildFacade.SendJoinRequestResult result) {
        return SEND_GUILD_JOIN_REQUEST_OUTCOME_MAPPING.get(result);
    }

    private void handleApproveGuildJoinRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ApproveGuildJoinRequestCommand command = ApproveGuildJoinRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleApproveGuildJoinRequestBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleApproveGuildJoinRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, ApproveGuildJoinRequestCommand command) {
        GuildFacade.ApproveResult result = guildFacade.approveJoinRequest(command.getRequestId(), characterId);
        ApproveGuildJoinRequestResponse response = ApproveGuildJoinRequestResponse.newBuilder()
                .setOutcome(toProtoOutcome(result))
                .build();
        send(ctx, Opcode.OPCODE_APPROVE_GUILD_JOIN_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<GuildFacade.ApproveResult, ApproveGuildJoinRequestOutcome>
            APPROVE_GUILD_JOIN_REQUEST_OUTCOME_MAPPING = Map.of(
                    GuildFacade.ApproveResult.OK,
                    ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_OK,
                    GuildFacade.ApproveResult.NOT_FOUND,
                    ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_NOT_FOUND,
                    GuildFacade.ApproveResult.TARGET_ALREADY_IN_GUILD,
                    ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD);

    private static ApproveGuildJoinRequestOutcome toProtoOutcome(GuildFacade.ApproveResult result) {
        return APPROVE_GUILD_JOIN_REQUEST_OUTCOME_MAPPING.get(result);
    }

    private void handleRejectGuildJoinRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RejectGuildJoinRequestCommand command = RejectGuildJoinRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleRejectGuildJoinRequestBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleRejectGuildJoinRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, RejectGuildJoinRequestCommand command) {
        GuildFacade.RejectResult result = guildFacade.rejectJoinRequest(command.getRequestId(), characterId);
        RejectGuildJoinRequestOutcome outcome = result == GuildFacade.RejectResult.OK
                ? RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK
                : RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_NOT_FOUND;
        RejectGuildJoinRequestResponse response =
                RejectGuildJoinRequestResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_REJECT_GUILD_JOIN_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleLeaveGuildCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        LeaveGuildCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleLeaveGuildBlocking(ctx, sequenceId, characterId));
    }

    private void handleLeaveGuildBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        LeaveGuildOutcome outcome =
                switch (guildFacade.leaveGuild(characterId)) {
                    case OK -> LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_OK;
                    case NOT_IN_GUILD -> LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_NOT_IN_GUILD;
                    case LEADER_CANNOT_LEAVE -> LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE;
                };
        LeaveGuildResponse response =
                LeaveGuildResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_LEAVE_GUILD_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleDisbandGuildCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        DisbandGuildCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleDisbandGuildBlocking(ctx, sequenceId, characterId));
    }

    private void handleDisbandGuildBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        DisbandGuildOutcome outcome = guildFacade.disbandGuild(characterId) == GuildFacade.DisbandResult.OK
                ? DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK
                : DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_NOT_LEADER;
        DisbandGuildResponse response =
                DisbandGuildResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_DISBAND_GUILD_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleKickGuildMemberCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        KickGuildMemberCommand command = KickGuildMemberCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleKickGuildMemberBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleKickGuildMemberBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, KickGuildMemberCommand command) {
        Optional<Character> target = characterFacade.findByName(command.getTargetName());
        if (target.isEmpty()) {
            KickGuildMemberResponse response = KickGuildMemberResponse.newBuilder()
                    .setOutcome(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_TARGET_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_KICK_GUILD_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        GuildFacade.KickResult result =
                guildFacade.kickMember(characterId, target.get().id());
        KickGuildMemberOutcome outcome =
                switch (result) {
                    case OK -> KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_OK;
                    case CANNOT_KICK_SELF -> KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF;
                    case NOT_FOUND -> KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_NOT_FOUND;
                };
        KickGuildMemberResponse response =
                KickGuildMemberResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_KICK_GUILD_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleListGuildMembersRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListGuildMembersRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListGuildMembersBlocking(ctx, sequenceId, characterId));
    }

    private void handleListGuildMembersBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListGuildMembersResponse.Builder responseBuilder = ListGuildMembersResponse.newBuilder();
        for (GuildFacade.MemberView member : guildFacade.listMembers(characterId)) {
            // findById chạm DB mỗi thành viên — chấp nhận N+1 ở quy mô 1 bang MVP (vài chục),
            // cùng lý do ListFriendsResponse.
            String name = characterFacade
                    .findById(member.characterId())
                    .map(Character::name)
                    .orElse("");
            responseBuilder.addMembers(GuildMemberView.newBuilder()
                    .setCharacterId(member.characterId())
                    .setName(name)
                    .setIsLeader(member.isLeader())
                    .setOnline(worldGateway.registry().isOnline(member.characterId()))
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleListPendingGuildJoinRequestsRequest(
            ChannelHandlerContext ctx, PacketHeader header, byte[] payload) throws InvalidProtocolBufferException {
        ListPendingGuildJoinRequestsRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx, sequenceId, () -> handleListPendingGuildJoinRequestsBlocking(ctx, sequenceId, characterId));
    }

    private void handleListPendingGuildJoinRequestsBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListPendingGuildJoinRequestsResponse.Builder responseBuilder =
                ListPendingGuildJoinRequestsResponse.newBuilder();
        for (GuildFacade.PendingJoinRequestView pending : guildFacade.listPendingJoinRequests(characterId)) {
            String name = characterFacade
                    .findById(pending.characterId())
                    .map(Character::name)
                    .orElse("");
            responseBuilder.addRequests(PendingGuildJoinRequestView.newBuilder()
                    .setRequestId(pending.requestId())
                    .setCharacterId(pending.characterId())
                    .setCharacterName(name)
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_PENDING_GUILD_JOIN_REQUESTS_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleGetMyGuildRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetMyGuildRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleGetMyGuildBlocking(ctx, sequenceId, characterId));
    }

    private void handleGetMyGuildBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        Optional<GuildFacade.MyGuildView> myGuild = guildFacade.findMyGuild(characterId);
        GetMyGuildResponse.Builder responseBuilder =
                GetMyGuildResponse.newBuilder().setInGuild(myGuild.isPresent());
        myGuild.ifPresent(guild -> responseBuilder
                .setGuildId(guild.guildId())
                .setGuildName(guild.guildName())
                .setIsLeader(guild.isLeader()));
        send(
                ctx,
                Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleCreatePartyCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CreatePartyCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleCreatePartyBlocking(ctx, sequenceId, characterId));
    }

    private void handleCreatePartyBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        PartyFacade.CreateResult result = partyFacade.createParty(characterId);
        CreatePartyOutcome outcome = result.outcome() == PartyFacade.CreateOutcome.OK
                ? CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK
                : CreatePartyOutcome.CREATE_PARTY_OUTCOME_ALREADY_IN_PARTY;
        CreatePartyResponse.Builder responseBuilder =
                CreatePartyResponse.newBuilder().setOutcome(outcome);
        if (result.partyId() != null) {
            responseBuilder.setPartyId(result.partyId());
        }
        send(
                ctx,
                Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleInvitePartyMemberCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        InvitePartyMemberCommand command = InvitePartyMemberCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleInvitePartyMemberBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleInvitePartyMemberBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, InvitePartyMemberCommand command) {
        Optional<Character> target = characterFacade.findByName(command.getTargetName());
        if (target.isEmpty()) {
            InvitePartyMemberResponse response = InvitePartyMemberResponse.newBuilder()
                    .setOutcome(InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_TARGET_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        PartyFacade.InviteResult result =
                partyFacade.invite(characterId, target.get().id());
        InvitePartyMemberResponse response = InvitePartyMemberResponse.newBuilder()
                .setOutcome(toProtoOutcome(result))
                .build();
        send(ctx, Opcode.OPCODE_INVITE_PARTY_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<PartyFacade.InviteResult, InvitePartyMemberOutcome> INVITE_PARTY_MEMBER_OUTCOME_MAPPING =
            Map.of(
                    PartyFacade.InviteResult.OK,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_OK,
                    PartyFacade.InviteResult.NOT_LEADER,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_NOT_LEADER,
                    PartyFacade.InviteResult.CANNOT_INVITE_SELF,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_CANNOT_INVITE_SELF,
                    PartyFacade.InviteResult.TARGET_ALREADY_IN_PARTY,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_TARGET_ALREADY_IN_PARTY,
                    PartyFacade.InviteResult.PARTY_FULL,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_PARTY_FULL,
                    PartyFacade.InviteResult.INVITE_ALREADY_PENDING,
                    InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_INVITE_ALREADY_PENDING);

    private static InvitePartyMemberOutcome toProtoOutcome(PartyFacade.InviteResult result) {
        return INVITE_PARTY_MEMBER_OUTCOME_MAPPING.get(result);
    }

    private void handleAcceptPartyInviteCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AcceptPartyInviteCommand command = AcceptPartyInviteCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleAcceptPartyInviteBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleAcceptPartyInviteBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, AcceptPartyInviteCommand command) {
        PartyFacade.AcceptResult result = partyFacade.acceptInvite(command.getInviteId(), characterId);
        AcceptPartyInviteOutcome outcome =
                switch (result) {
                    case OK -> AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_OK;
                    case NOT_FOUND -> AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_NOT_FOUND;
                    case ALREADY_IN_PARTY -> AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_ALREADY_IN_PARTY;
                    case PARTY_FULL -> AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_PARTY_FULL;
                };
        AcceptPartyInviteResponse response =
                AcceptPartyInviteResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_ACCEPT_PARTY_INVITE_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleRejectPartyInviteCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RejectPartyInviteCommand command = RejectPartyInviteCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleRejectPartyInviteBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleRejectPartyInviteBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, RejectPartyInviteCommand command) {
        PartyFacade.RejectResult result = partyFacade.rejectInvite(command.getInviteId(), characterId);
        RejectPartyInviteOutcome outcome = result == PartyFacade.RejectResult.OK
                ? RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK
                : RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_NOT_FOUND;
        RejectPartyInviteResponse response =
                RejectPartyInviteResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_REJECT_PARTY_INVITE_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleLeavePartyCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        LeavePartyCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleLeavePartyBlocking(ctx, sequenceId, characterId));
    }

    private void handleLeavePartyBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        LeavePartyOutcome outcome = partyFacade.leaveParty(characterId) == PartyFacade.LeaveResult.OK
                ? LeavePartyOutcome.LEAVE_PARTY_OUTCOME_OK
                : LeavePartyOutcome.LEAVE_PARTY_OUTCOME_NOT_IN_PARTY;
        LeavePartyResponse response =
                LeavePartyResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_LEAVE_PARTY_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleKickPartyMemberCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        KickPartyMemberCommand command = KickPartyMemberCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleKickPartyMemberBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleKickPartyMemberBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, KickPartyMemberCommand command) {
        Optional<Character> target = characterFacade.findByName(command.getTargetName());
        if (target.isEmpty()) {
            KickPartyMemberResponse response = KickPartyMemberResponse.newBuilder()
                    .setOutcome(KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_TARGET_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_KICK_PARTY_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        PartyFacade.KickResult result =
                partyFacade.kickMember(characterId, target.get().id());
        KickPartyMemberOutcome outcome =
                switch (result) {
                    case OK -> KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_OK;
                    case CANNOT_KICK_SELF -> KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_CANNOT_KICK_SELF;
                    case NOT_FOUND -> KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_NOT_FOUND;
                };
        KickPartyMemberResponse response =
                KickPartyMemberResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_KICK_PARTY_MEMBER_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleListPartyMembersRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListPartyMembersRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListPartyMembersBlocking(ctx, sequenceId, characterId));
    }

    private void handleListPartyMembersBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListPartyMembersResponse.Builder responseBuilder = ListPartyMembersResponse.newBuilder();
        for (PartyFacade.MemberView member : partyFacade.listMembers(characterId)) {
            String name = characterFacade
                    .findById(member.characterId())
                    .map(Character::name)
                    .orElse("");
            responseBuilder.addMembers(PartyMemberView.newBuilder()
                    .setCharacterId(member.characterId())
                    .setName(name)
                    .setIsLeader(member.isLeader())
                    .setOnline(worldGateway.registry().isOnline(member.characterId()))
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleListPendingPartyInvitesRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListPendingPartyInvitesRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListPendingPartyInvitesBlocking(ctx, sequenceId, characterId));
    }

    private void handleListPendingPartyInvitesBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListPendingPartyInvitesResponse.Builder responseBuilder = ListPendingPartyInvitesResponse.newBuilder();
        for (PartyFacade.PendingInviteView pending : partyFacade.listPendingInvites(characterId)) {
            responseBuilder.addInvites(PendingPartyInviteView.newBuilder()
                    .setInviteId(pending.inviteId())
                    .setPartyId(pending.partyId())
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_PENDING_PARTY_INVITES_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleGetMyPartyRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetMyPartyRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleGetMyPartyBlocking(ctx, sequenceId, characterId));
    }

    private void handleGetMyPartyBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        Optional<PartyFacade.MyPartyView> myParty = partyFacade.findMyParty(characterId);
        GetMyPartyResponse.Builder responseBuilder =
                GetMyPartyResponse.newBuilder().setInParty(myParty.isPresent());
        myParty.ifPresent(party -> responseBuilder.setPartyId(party.partyId()).setIsLeader(party.isLeader()));
        send(
                ctx,
                Opcode.OPCODE_GET_MY_PARTY_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    // EnterDungeonCommand chạm PartyFacade/CharacterFacade (DB) qua DungeonInstanceRegistry —
    // Virtual Thread, giống handleGetMyPartyRequest.
    private void handleEnterDungeonCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        EnterDungeonCommand command = EnterDungeonCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleEnterDungeonBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleEnterDungeonBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, EnterDungeonCommand command) {
        DungeonInstanceRegistry.EnterOutcome outcome =
                worldGateway.dungeonInstanceRegistry().enterDungeon(characterId, command.getDungeonCode());
        EnterDungeonOutcome protoOutcome =
                switch (outcome) {
                    case OK -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK;
                    case NOT_IN_PARTY -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY;
                    case PARTY_MEMBER_OFFLINE -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE;
                    case ALREADY_IN_DUNGEON -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON;
                    case UNKNOWN_DUNGEON -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_UNKNOWN_DUNGEON;
                };
        EnterDungeonResponse response =
                EnterDungeonResponse.newBuilder().setOutcome(protoOutcome).build();
        // ⚠️ Response đi SAU khi mọi người đã được dời — và đó là BẮT BUỘC, không phải quán tính.
        // Mục "Phát hiện phụ" của ADR-047 đề xuất đảo lại (trả lời trước, dời sau) để đóng khe
        // response-vs-spawn của chính người gửi. Đã dựng thử 2026-09-13 và HAI test có sẵn bác ngay:
        //   · partyShouldEnterAndExitDungeonAsSeparateWorld — lệnh vào THỨ HAI (của B, người vừa bị
        //     kéo) nhận OK thay vì ALREADY_IN_DUNGEON;
        //   · memberShouldStillExitDungeon_afterLeavingPartyInside — lệnh RA ngay sau đó nhận
        //     NOT_IN_DUNGEON thay vì OK.
        // Lý do chung: Response còn là MỐC happens-before cho lệnh TIẾP THEO của client. Trả lời
        // trước khi trạng thái kịp đổi thì lệnh kế tiếp đọc phải trạng thái cũ. Khe của người gửi
        // vẫn còn (nợ cũ, không chặn gì); đóng nó cần một cách khác — xem ADR-047.
        send(ctx, Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // ExitDungeonCommand chạm CharacterFacade (DB) qua DungeonInstanceRegistry — Virtual Thread.
    private void handleExitDungeonCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ExitDungeonCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleExitDungeonBlocking(ctx, sequenceId, characterId));
    }

    private void handleExitDungeonBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        DungeonInstanceRegistry.ExitOutcome outcome =
                worldGateway.dungeonInstanceRegistry().exitDungeon(characterId);
        ExitDungeonOutcome protoOutcome = outcome == DungeonInstanceRegistry.ExitOutcome.OK
                ? ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK
                : ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON;
        ExitDungeonResponse response =
                ExitDungeonResponse.newBuilder().setOutcome(protoOutcome).build();
        send(ctx, Opcode.OPCODE_EXIT_DUNGEON_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    // ChallengeDuelCommand chạm CharacterFacade (tra tên) qua ArenaInstanceRegistry — Virtual
    // Thread, giống handleRequestTradeCommand (cùng pattern "resolve tên TRƯỚC khi gọi facade").
    private void handleChallengeDuelCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ChallengeDuelCommand command = ChallengeDuelCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleChallengeDuelBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleChallengeDuelBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, ChallengeDuelCommand command) {
        Optional<Character> target = characterFacade.findByName(command.getTargetName());
        if (target.isEmpty()) {
            ChallengeDuelResponse response = ChallengeDuelResponse.newBuilder()
                    .setOutcome(ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        ArenaInstanceRegistry.ChallengeResult result = worldGateway
                .arenaInstanceRegistry()
                .challengeDuel(characterId, target.get().id());
        ChallengeDuelResponse.Builder responseBuilder =
                ChallengeDuelResponse.newBuilder().setOutcome(toProtoOutcome(result.outcome()));
        if (result.outcome() == ArenaInstanceRegistry.ChallengeOutcome.OK) {
            responseBuilder.setChallengeId(result.challengeId());
        }
        send(
                ctx,
                Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
        if (result.outcome() == ArenaInstanceRegistry.ChallengeOutcome.OK) {
            String challengerName =
                    characterFacade.findById(characterId).map(Character::name).orElse("");
            DuelChallengeReceivedEvent event = DuelChallengeReceivedEvent.newBuilder()
                    .setChallengeId(result.challengeId())
                    .setChallengerCharacterId(characterId)
                    .setChallengerName(challengerName)
                    .build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            target.get().id(),
                            Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE,
                            0,
                            event.toByteArray());
        }
    }

    private static final Map<ArenaInstanceRegistry.ChallengeOutcome, ChallengeDuelOutcome>
            CHALLENGE_DUEL_OUTCOME_MAPPING = Map.of(
                    ArenaInstanceRegistry.ChallengeOutcome.OK,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK,
                    ArenaInstanceRegistry.ChallengeOutcome.CANNOT_CHALLENGE_SELF,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_CANNOT_CHALLENGE_SELF,
                    ArenaInstanceRegistry.ChallengeOutcome.ALREADY_IN_DUEL,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_ALREADY_IN_DUEL,
                    ArenaInstanceRegistry.ChallengeOutcome.TARGET_ALREADY_IN_DUEL,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_ALREADY_IN_DUEL,
                    ArenaInstanceRegistry.ChallengeOutcome.TARGET_OFFLINE,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_OFFLINE);

    private static ChallengeDuelOutcome toProtoOutcome(ArenaInstanceRegistry.ChallengeOutcome outcome) {
        return CHALLENGE_DUEL_OUTCOME_MAPPING.get(outcome);
    }

    private void handleAcceptDuelCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AcceptDuelCommand command = AcceptDuelCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleAcceptDuelBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleAcceptDuelBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, AcceptDuelCommand command) {
        ArenaInstanceRegistry.AcceptResult result =
                worldGateway.arenaInstanceRegistry().acceptDuel(characterId, command.getChallengeId());
        AcceptDuelOutcome outcome = result.outcome() == ArenaInstanceRegistry.AcceptOutcome.OK
                ? AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_OK
                : AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND;
        AcceptDuelResponse response =
                AcceptDuelResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (result.outcome() != ArenaInstanceRegistry.AcceptOutcome.OK) {
            return;
        }
        long challengerCharacterId = result.challengerCharacterId();
        String challengerName = characterFacade
                .findById(challengerCharacterId)
                .map(Character::name)
                .orElse("");
        String accepterName =
                characterFacade.findById(characterId).map(Character::name).orElse("");
        DuelStartedEvent eventForAccepter = DuelStartedEvent.newBuilder()
                .setOpponentCharacterId(challengerCharacterId)
                .setOpponentName(challengerName)
                .build();
        send(ctx, Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE, 0, eventForAccepter.toByteArray());
        DuelStartedEvent eventForChallenger = DuelStartedEvent.newBuilder()
                .setOpponentCharacterId(characterId)
                .setOpponentName(accepterName)
                .build();
        worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        challengerCharacterId,
                        Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE,
                        0,
                        eventForChallenger.toByteArray());
    }

    private void handleDeclineDuelCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        DeclineDuelCommand command = DeclineDuelCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleDeclineDuelBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleDeclineDuelBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, DeclineDuelCommand command) {
        ArenaInstanceRegistry.DeclineResult result =
                worldGateway.arenaInstanceRegistry().declineDuel(characterId, command.getChallengeId());
        DeclineDuelOutcome outcome = result.outcome() == ArenaInstanceRegistry.DeclineOutcome.OK
                ? DeclineDuelOutcome.DECLINE_DUEL_OUTCOME_OK
                : DeclineDuelOutcome.DECLINE_DUEL_OUTCOME_NOT_FOUND;
        DeclineDuelResponse response =
                DeclineDuelResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_DECLINE_DUEL_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (result.outcome() == ArenaInstanceRegistry.DeclineOutcome.OK) {
            DuelChallengeDeclinedEvent event = DuelChallengeDeclinedEvent.newBuilder()
                    .setChallengeId(command.getChallengeId())
                    .build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            result.challengerCharacterId(),
                            Opcode.OPCODE_DUEL_CHALLENGE_DECLINED_EVENT_VALUE,
                            0,
                            event.toByteArray());
        }
    }

    // ExitDuelCommand (bỏ cuộc giữa chừng) chạm PvpFacade (DB, ghi rating THUA) qua
    // ArenaInstanceRegistry — Virtual Thread. DuelResultEvent cho CẢ 2 người do
    // ArenaInstanceRegistry tự gửi (settleDuel), handler này chỉ trả ExitDuelResponse.
    private void handleExitDuelCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ExitDuelCommand.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleExitDuelBlocking(ctx, sequenceId, characterId));
    }

    private void handleExitDuelBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ArenaInstanceRegistry.ExitResult result =
                worldGateway.arenaInstanceRegistry().forfeitDuel(characterId);
        ExitDuelOutcome outcome = result.outcome() == ArenaInstanceRegistry.ExitOutcome.OK
                ? ExitDuelOutcome.EXIT_DUEL_OUTCOME_OK
                : ExitDuelOutcome.EXIT_DUEL_OUTCOME_NOT_IN_DUEL;
        ExitDuelResponse response =
                ExitDuelResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_EXIT_DUEL_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleGetPvpRatingRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetPvpRatingRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleGetPvpRatingBlocking(ctx, sequenceId, characterId));
    }

    private void handleGetPvpRatingBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        GetPvpRatingUseCase.RatingView view = pvpFacade.findRating(characterId);
        GetPvpRatingResponse response = GetPvpRatingResponse.newBuilder()
                .setRating(view.rating())
                .setWins(view.wins())
                .setLosses(view.losses())
                .build();
        send(ctx, Opcode.OPCODE_GET_PVP_RATING_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private void handleRequestTradeCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RequestTradeCommand command = RequestTradeCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleRequestTradeBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleRequestTradeBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, RequestTradeCommand command) {
        Optional<Character> target = characterFacade.findByName(command.getTargetName());
        if (target.isEmpty()) {
            RequestTradeResponse response = RequestTradeResponse.newBuilder()
                    .setOutcome(RequestTradeOutcome.REQUEST_TRADE_OUTCOME_TARGET_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        TradeFacade.RequestResult result =
                tradeFacade.requestTrade(characterId, target.get().id());
        RequestTradeResponse.Builder responseBuilder =
                RequestTradeResponse.newBuilder().setOutcome(toProtoOutcome(result.outcome()));
        if (result.sessionId() != null) {
            responseBuilder.setSessionId(result.sessionId());
        }
        send(
                ctx,
                Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
        if (result.outcome() == TradeFacade.RequestOutcome.OK) {
            String senderName =
                    characterFacade.findById(characterId).map(Character::name).orElse("");
            TradeRequestedEvent event = TradeRequestedEvent.newBuilder()
                    .setSessionId(result.sessionId())
                    .setFromCharacterId(characterId)
                    .setFromName(senderName)
                    .build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            target.get().id(), Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    private static final Map<TradeFacade.RequestOutcome, RequestTradeOutcome> REQUEST_TRADE_OUTCOME_MAPPING = Map.of(
            TradeFacade.RequestOutcome.OK,
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK,
            TradeFacade.RequestOutcome.CANNOT_TRADE_SELF,
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_CANNOT_TRADE_SELF,
            TradeFacade.RequestOutcome.ALREADY_IN_TRADE,
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_ALREADY_IN_TRADE,
            TradeFacade.RequestOutcome.TARGET_ALREADY_IN_TRADE,
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_TARGET_ALREADY_IN_TRADE);

    private static RequestTradeOutcome toProtoOutcome(TradeFacade.RequestOutcome outcome) {
        return REQUEST_TRADE_OUTCOME_MAPPING.get(outcome);
    }

    private void handleAcceptTradeRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AcceptTradeRequestCommand command = AcceptTradeRequestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleAcceptTradeRequestBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleAcceptTradeRequestBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, AcceptTradeRequestCommand command) {
        TradeFacade.AcceptResult result = tradeFacade.acceptRequest(command.getSessionId(), characterId);
        AcceptTradeRequestOutcome outcome = result == TradeFacade.AcceptResult.OK
                ? AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_OK
                : AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND;
        AcceptTradeRequestResponse response =
                AcceptTradeRequestResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (result == TradeFacade.AcceptResult.OK) {
            notifyCounterpart(characterId, command.getSessionId());
        }
    }

    private void handleCancelTradeCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CancelTradeCommand command = CancelTradeCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleCancelTradeBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleCancelTradeBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, CancelTradeCommand command) {
        Optional<TradeFacade.TradeStatusView> statusBefore = tradeFacade.getStatus(characterId);
        TradeFacade.CancelResult result = tradeFacade.cancel(command.getSessionId(), characterId);
        CancelTradeOutcome outcome = result == TradeFacade.CancelResult.OK
                ? CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK
                : CancelTradeOutcome.CANCEL_TRADE_OUTCOME_NOT_FOUND;
        CancelTradeResponse response =
                CancelTradeResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_CANCEL_TRADE_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (result == TradeFacade.CancelResult.OK && statusBefore.isPresent()) {
            TradeClosedEvent event = TradeClosedEvent.newBuilder()
                    .setSessionId(command.getSessionId())
                    .setReason(TradeCloseReason.TRADE_CLOSE_REASON_CANCELLED)
                    .build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            statusBefore.get().counterpartCharacterId(),
                            Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE,
                            0,
                            event.toByteArray());
        }
    }

    private void handleSetTradeOfferCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SetTradeOfferCommand command = SetTradeOfferCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleSetTradeOfferBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleSetTradeOfferBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SetTradeOfferCommand command) {
        List<TradeFacade.OfferItemInput> items = command.getItemsList().stream()
                .map(i -> new TradeFacade.OfferItemInput(i.getItemId(), i.getQuantity()))
                .toList();
        TradeFacade.SetOfferResult result =
                tradeFacade.setOffer(command.getSessionId(), characterId, command.getGoldAmount(), items);
        SetTradeOfferOutcome outcome = result == TradeFacade.SetOfferResult.OK
                ? SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_OK
                : SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND;
        SetTradeOfferResponse response =
                SetTradeOfferResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (result == TradeFacade.SetOfferResult.OK) {
            notifyCounterpart(characterId, command.getSessionId());
        }
    }

    private void handleConfirmTradeCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ConfirmTradeCommand command = ConfirmTradeCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // Context.current() PHẢI chụp NGAY ở đây — cùng lý do handleBuyItemCommand.
        ConfirmTradeRequest request = new ConfirmTradeRequest(sequenceId, characterId, command, Context.current());
        executeBlocking(ctx, sequenceId, () -> handleConfirmTradeBlocking(ctx, request));
    }

    private record ConfirmTradeRequest(
            int sequenceId, long characterId, ConfirmTradeCommand command, Context tracingContext) {}

    /**
     * ConfirmTradeCommand — NƠI DUY NHẤT {@code ConfirmTradeUseCase} xác thực lại TOÀN BỘ lời chào
     * LÚC THI HÀNH rồi chạm gold/item thật (feature-trade/CLAUDE.md, SECURITY.md) — cùng lớp rủi ro
     * cường hoá/mua-bán, retrofit span con sâu "feature.trade.confirm" khớp pattern
     * {@link #handleEnhanceItemBlocking} (OBSERVABILITY.md §6).
     */
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"}) // xem giải thích ở channelRead0
    private void handleConfirmTradeBlocking(ChannelHandlerContext ctx, ConfirmTradeRequest request) {
        Span span = tracing.startChildSpan("feature.trade.confirm", request.tracingContext());
        try (Scope scope = span.makeCurrent()) {
            long characterId = request.characterId();
            ConfirmTradeCommand command = request.command();
            Optional<TradeFacade.TradeStatusView> statusBefore = tradeFacade.getStatus(characterId);
            TradeFacade.ConfirmResult result =
                    tradeFacade.confirm(command.getSessionId(), characterId, command.getIdempotencyKey());
            span.setAttribute("result", result.name());
            ConfirmTradeOutcome outcome =
                    switch (result) {
                        case OK -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OK;
                        case EXECUTED -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED;
                        case NOT_FOUND -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_NOT_FOUND;
                        case OFFER_INVALID -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OFFER_INVALID;
                        case DUPLICATE_REQUEST -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_DUPLICATE_REQUEST;
                    };
            ConfirmTradeResponse response =
                    ConfirmTradeResponse.newBuilder().setOutcome(outcome).build();
            send(ctx, Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE, request.sequenceId(), response.toByteArray());
            statusBefore.ifPresent(status ->
                    notifyCounterpartOfConfirmResult(status.counterpartCharacterId(), result, command.getSessionId()));
        } finally {
            span.end();
        }
    }

    /** Đẩy event cho đối phương SAU KHI {@code ConfirmTradeUseCase} đã trả kết quả — tách khỏi
     * {@code handleConfirmTradeBlocking} chỉ để giữ độ phức tạp method dưới ngưỡng CyclomaticComplexity. */
    private void notifyCounterpartOfConfirmResult(
            long counterpartId, TradeFacade.ConfirmResult result, long sessionId) {
        if (result == TradeFacade.ConfirmResult.EXECUTED) {
            TradeClosedEvent event = TradeClosedEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setReason(TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED)
                    .build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            counterpartId, Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE, 0, event.toByteArray());
        } else if (result == TradeFacade.ConfirmResult.OK || result == TradeFacade.ConfirmResult.OFFER_INVALID) {
            TradeUpdatedEvent event =
                    TradeUpdatedEvent.newBuilder().setSessionId(sessionId).build();
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            counterpartId, Opcode.OPCODE_TRADE_UPDATED_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    /** Đẩy {@code TradeUpdatedEvent} cho đối phương của {@code characterId} trong phiên hiện tại. */
    private void notifyCounterpart(long characterId, long sessionId) {
        Optional<TradeFacade.TradeStatusView> status = tradeFacade.getStatus(characterId);
        if (status.isEmpty()) {
            return;
        }
        TradeUpdatedEvent event =
                TradeUpdatedEvent.newBuilder().setSessionId(sessionId).build();
        worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        status.get().counterpartCharacterId(),
                        Opcode.OPCODE_TRADE_UPDATED_EVENT_VALUE,
                        0,
                        event.toByteArray());
    }

    private void handleGetTradeStatusRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetTradeStatusRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleGetTradeStatusBlocking(ctx, sequenceId, characterId));
    }

    private void handleGetTradeStatusBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        Optional<TradeFacade.TradeStatusView> statusOpt = tradeFacade.getStatus(characterId);
        GetTradeStatusResponse.Builder responseBuilder =
                GetTradeStatusResponse.newBuilder().setInTrade(statusOpt.isPresent());
        statusOpt.ifPresent(status -> {
            String counterpartName = characterFacade
                    .findById(status.counterpartCharacterId())
                    .map(Character::name)
                    .orElse("");
            responseBuilder
                    .setSessionId(status.sessionId())
                    .setPhase(
                            status.phase() == TradeFacade.TradePhase.PENDING_ACCEPT
                                    ? TradePhase.TRADE_PHASE_PENDING_ACCEPT
                                    : TradePhase.TRADE_PHASE_OPEN)
                    .setCounterpartCharacterId(status.counterpartCharacterId())
                    .setCounterpartName(counterpartName)
                    .setIsRequester(status.isRequester())
                    .setMyGoldOffer(status.myGoldOffer())
                    .setTheirGoldOffer(status.theirGoldOffer())
                    .setMyConfirmed(status.myConfirmed())
                    .setTheirConfirmed(status.theirConfirmed());
            for (TradeFacade.OfferItemView item : status.myItems()) {
                responseBuilder.addMyItems(toProtoOfferItemView(item));
            }
            for (TradeFacade.OfferItemView item : status.theirItems()) {
                responseBuilder.addTheirItems(toProtoOfferItemView(item));
            }
        });
        send(
                ctx,
                Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private static TradeOfferItemView toProtoOfferItemView(TradeFacade.OfferItemView item) {
        return TradeOfferItemView.newBuilder()
                .setItemId(item.itemId())
                .setItemTemplateId(item.itemTemplateId())
                .setQuantity(item.quantity())
                .setEnhanceLevel(item.enhanceLevel())
                .build();
    }

    private void handleSendMailCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendMailCommand command = SendMailCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleSendMailBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleSendMailBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SendMailCommand command) {
        Optional<Character> recipient = characterFacade.findByName(command.getRecipientName());
        if (recipient.isEmpty()) {
            SendMailResponse response = SendMailResponse.newBuilder()
                    .setOutcome(SendMailOutcome.SEND_MAIL_OUTCOME_RECIPIENT_NOT_FOUND)
                    .build();
            send(ctx, Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE, sequenceId, response.toByteArray());
            return;
        }
        List<MailFacade.AttachmentRequest> attachments = command.getAttachmentsList().stream()
                .map(a -> new MailFacade.AttachmentRequest(a.getItemTemplateId(), a.getQuantity()))
                .toList();
        MailFacade.SendMailRequest request = new MailFacade.SendMailRequest(
                characterId,
                recipient.get().id(),
                command.getSubject(),
                command.getBody(),
                command.getGoldAmount(),
                attachments);
        // characterId lấy từ SESSION (không phải packet) — nên senderId KHÔNG BAO GIỜ null ở đường
        // này, và ký quỹ vàng/vật phẩm trong SendMailUseCase luôn chạy. Đường "thư hệ thống miễn
        // ký quỹ" chỉ với tới được từ code server.
        MailFacade.SendOutcome outcome = mailFacade.sendMail(request);
        SendMailResponse response = SendMailResponse.newBuilder()
                .setOutcome(toProtoOutcome(outcome.result()))
                .setMailId(outcome.mailId())
                .build();
        send(ctx, Opcode.OPCODE_SEND_MAIL_RESPONSE_VALUE, sequenceId, response.toByteArray());
        if (outcome.result() != MailFacade.SendResult.OK) {
            // Không có thư nào được tạo — báo "có thư mới" lúc này sẽ khiến người nhận gọi
            // ListMailRequest và không thấy gì.
            return;
        }
        NewMailEvent event =
                NewMailEvent.newBuilder().setMailId(outcome.mailId()).build();
        worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        recipient.get().id(), Opcode.OPCODE_NEW_MAIL_EVENT_VALUE, 0, event.toByteArray());
    }

    private static final Map<MailFacade.SendResult, SendMailOutcome> SEND_MAIL_OUTCOME_MAPPING = Map.of(
            MailFacade.SendResult.OK, SendMailOutcome.SEND_MAIL_OUTCOME_OK,
            MailFacade.SendResult.INSUFFICIENT_GOLD, SendMailOutcome.SEND_MAIL_OUTCOME_INSUFFICIENT_GOLD,
            MailFacade.SendResult.ATTACHMENT_NOT_OWNED, SendMailOutcome.SEND_MAIL_OUTCOME_ATTACHMENT_NOT_OWNED,
            MailFacade.SendResult.INVALID_GOLD_AMOUNT, SendMailOutcome.SEND_MAIL_OUTCOME_INVALID_GOLD_AMOUNT,
            MailFacade.SendResult.INVALID_ATTACHMENT, SendMailOutcome.SEND_MAIL_OUTCOME_INVALID_ATTACHMENT,
            MailFacade.SendResult.SUBJECT_TOO_LONG, SendMailOutcome.SEND_MAIL_OUTCOME_SUBJECT_TOO_LONG,
            MailFacade.SendResult.BODY_TOO_LONG, SendMailOutcome.SEND_MAIL_OUTCOME_BODY_TOO_LONG);

    private static SendMailOutcome toProtoOutcome(MailFacade.SendResult result) {
        return SEND_MAIL_OUTCOME_MAPPING.get(result);
    }

    private void handleListMailRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListMailRequest.parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleListMailBlocking(ctx, sequenceId, characterId));
    }

    private void handleListMailBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        ListMailResponse.Builder responseBuilder = ListMailResponse.newBuilder();
        for (MailFacade.MailView mail : mailFacade.listMail(characterId)) {
            MailView.Builder viewBuilder = MailView.newBuilder()
                    .setMailId(mail.mailId())
                    .setHasSender(mail.senderId() != null)
                    .setSenderId(mail.senderId() == null ? 0 : mail.senderId())
                    .setSubject(mail.subject())
                    .setBody(mail.body())
                    .setGoldAmount(mail.goldAmount())
                    .setClaimed(mail.claimed());
            for (MailFacade.AttachmentView attachment : mail.attachments()) {
                viewBuilder.addAttachments(MailAttachmentView.newBuilder()
                        .setItemTemplateId(attachment.itemTemplateId())
                        .setQuantity(attachment.quantity())
                        .build());
            }
            responseBuilder.addMails(viewBuilder.build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_MAIL_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleClaimMailCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ClaimMailCommand command = ClaimMailCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleClaimMailBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleClaimMailBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, ClaimMailCommand command) {
        MailFacade.ClaimResult result =
                mailFacade.claimMail(command.getMailId(), characterId, command.getIdempotencyKey());
        ClaimMailResponse response = ClaimMailResponse.newBuilder()
                .setOutcome(toProtoOutcome(result))
                .build();
        send(ctx, Opcode.OPCODE_CLAIM_MAIL_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<MailFacade.ClaimResult, ClaimMailOutcome> CLAIM_MAIL_OUTCOME_MAPPING = Map.of(
            MailFacade.ClaimResult.OK, ClaimMailOutcome.CLAIM_MAIL_OUTCOME_OK,
            MailFacade.ClaimResult.NOT_FOUND, ClaimMailOutcome.CLAIM_MAIL_OUTCOME_NOT_FOUND,
            MailFacade.ClaimResult.ALREADY_CLAIMED, ClaimMailOutcome.CLAIM_MAIL_OUTCOME_ALREADY_CLAIMED,
            MailFacade.ClaimResult.INVENTORY_FULL, ClaimMailOutcome.CLAIM_MAIL_OUTCOME_INVENTORY_FULL,
            MailFacade.ClaimResult.DUPLICATE_REQUEST, ClaimMailOutcome.CLAIM_MAIL_OUTCOME_DUPLICATE_REQUEST);

    private static ClaimMailOutcome toProtoOutcome(MailFacade.ClaimResult result) {
        return CLAIM_MAIL_OUTCOME_MAPPING.get(result);
    }

    private void handleDeleteMailCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        DeleteMailCommand command = DeleteMailCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleDeleteMailBlocking(ctx, sequenceId, characterId, command));
    }

    private void handleDeleteMailBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, DeleteMailCommand command) {
        MailFacade.DeleteResult result = mailFacade.deleteMail(command.getMailId(), characterId);
        DeleteMailResponse response = DeleteMailResponse.newBuilder()
                .setOutcome(toProtoOutcome(result))
                .build();
        send(ctx, Opcode.OPCODE_DELETE_MAIL_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static final Map<MailFacade.DeleteResult, DeleteMailOutcome> DELETE_MAIL_OUTCOME_MAPPING = Map.of(
            MailFacade.DeleteResult.OK, DeleteMailOutcome.DELETE_MAIL_OUTCOME_OK,
            MailFacade.DeleteResult.NOT_FOUND, DeleteMailOutcome.DELETE_MAIL_OUTCOME_NOT_FOUND,
            MailFacade.DeleteResult.MUST_CLAIM_FIRST, DeleteMailOutcome.DELETE_MAIL_OUTCOME_MUST_CLAIM_FIRST);

    private static DeleteMailOutcome toProtoOutcome(MailFacade.DeleteResult result) {
        return DELETE_MAIL_OUTCOME_MAPPING.get(result);
    }

    private void handleSendChatMessageCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendChatMessageCommand command = SendChatMessageCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        RateLimiter rateLimiter = chatRateLimiterFor(command.getChannel());
        if (!rateLimiter.tryAcquire()) {
            networkMetrics.recordRateLimitRejected(
                    "chat_" + command.getChannel().name());
            SendChatMessageResponse response = SendChatMessageResponse.newBuilder()
                    .setOutcome(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED)
                    .build();
            send(ctx, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE, sequenceId, response.toByteArray());
            if (rateLimiter.shouldDisconnect()) {
                LOG.warn("Chat vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
                ctx.close();
            }
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleSendChatMessageBlocking(ctx, sequenceId, characterId, command));
    }

    private RateLimiter chatRateLimiterFor(ChatChannel channel) {
        return switch (channel) {
            case CHAT_CHANNEL_PRIVATE -> chatPrivateRateLimiter;
            case CHAT_CHANNEL_AREA -> chatAreaRateLimiter;
            case CHAT_CHANNEL_GUILD -> chatGuildRateLimiter;
            case CHAT_CHANNEL_PARTY -> chatPartyRateLimiter;
            default -> chatWorldRateLimiter; // WORLD/UNSPECIFIED/UNRECOGNIZED — nghiêm ngặt nhất.
        };
    }

    private void handleSendChatMessageBlocking(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SendChatMessageCommand command) {
        Optional<dev.kage.chat.domain.ChatChannel> domainChannelOpt = toDomainChannel(command.getChannel());
        if (domainChannelOpt.isEmpty()) {
            sendError(ctx, sequenceId, 400, "error.invalid_chat_channel");
            return;
        }
        dev.kage.chat.domain.ChatChannel domainChannel = domainChannelOpt.get();
        Character target = null;
        if (domainChannel == dev.kage.chat.domain.ChatChannel.PRIVATE) {
            Optional<Character> found = characterFacade.findByName(command.getTargetName());
            if (found.isEmpty()) {
                sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND);
                return;
            }
            target = found.get();
            if (!worldGateway.registry().isOnline(target.id())) {
                sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_OFFLINE);
                return;
            }
        }
        if (!isChannelMembershipValid(characterId, domainChannel, ctx, sequenceId)) {
            return;
        }
        ChatFacade.SendResult result = chatFacade.sendMessage(characterId, domainChannel, command.getBody());
        sendChatOutcome(ctx, sequenceId, toProtoOutcome(result));
        if (result != ChatFacade.SendResult.OK) {
            return;
        }
        String senderName =
                characterFacade.findById(characterId).map(Character::name).orElse("");
        routeChatMessage(domainChannel, new ChatRouteContext(characterId, senderName, target, command.getBody()));
    }

    /** GUILD/PARTY chỉ gửi được nếu đang ở trong guild/party đó — tách khỏi
     * {@code handleSendChatMessageBlocking} chỉ để giữ độ phức tạp method dưới ngưỡng CyclomaticComplexity. */
    private boolean isChannelMembershipValid(
            long characterId,
            dev.kage.chat.domain.ChatChannel domainChannel,
            ChannelHandlerContext ctx,
            int sequenceId) {
        if (domainChannel == dev.kage.chat.domain.ChatChannel.GUILD
                && guildFacade.findMyGuild(characterId).isEmpty()) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_GUILD);
            return false;
        }
        if (domainChannel == dev.kage.chat.domain.ChatChannel.PARTY
                && partyFacade.findMyParty(characterId).isEmpty()) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_PARTY);
            return false;
        }
        return true;
    }

    /** Gói tham số cho {@link #routeChatMessage} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record ChatRouteContext(long characterId, String senderName, Character target, String body) {}

    /** Định tuyến người nhận SAU KHI {@code ChatFacade.sendMessage} đã trả OK — tách khỏi
     * {@code handleSendChatMessageBlocking} để giữ method đó dưới giới hạn Checkstyle MethodLength. */
    private void routeChatMessage(dev.kage.chat.domain.ChatChannel domainChannel, ChatRouteContext ctx) {
        switch (domainChannel) {
            case WORLD -> {
                ChatMessageEvent event = ChatMessageEvent.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                        .setSenderId(ctx.characterId())
                        .setSenderName(ctx.senderName())
                        .setBody(ctx.body())
                        .build();
                worldGateway.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
            }
            case PRIVATE -> {
                ChatMessageEvent event = ChatMessageEvent.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                        .setSenderId(ctx.characterId())
                        .setSenderName(ctx.senderName())
                        .setBody(ctx.body())
                        .build();
                worldGateway
                        .registry()
                        .sendToCharacterIfOnline(
                                ctx.target().id(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
            }
            case AREA ->
                currentWorldFacade(ctx.characterId())
                        .enqueueAreaChatMessage(ctx.characterId(), ctx.senderName(), ctx.body());
            // GUILD/PARTY: broadcast cho MỌI thành viên đang online (gồm cả người gửi, cùng cách
            // AREA tự thấy tin của mình) — không cần AoI/tick thread, guild/party membership
            // không phải dữ liệu world-tick (feature-guild, feature-party/CLAUDE.md).
            case GUILD ->
                broadcastToGroup(
                        ChatChannel.CHAT_CHANNEL_GUILD,
                        ctx,
                        guildFacade.listMembers(ctx.characterId()).stream()
                                .map(m -> m.characterId())
                                .toList());
            case PARTY ->
                broadcastToGroup(
                        ChatChannel.CHAT_CHANNEL_PARTY,
                        ctx,
                        partyFacade.listMembers(ctx.characterId()).stream()
                                .map(m -> m.characterId())
                                .toList());
            default -> throw new IllegalStateException("ChatChannel không xử lý: " + domainChannel);
        }
    }

    private void broadcastToGroup(ChatChannel protoChannel, ChatRouteContext ctx, List<Long> memberCharacterIds) {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(protoChannel)
                .setSenderId(ctx.characterId())
                .setSenderName(ctx.senderName())
                .setBody(ctx.body())
                .build();
        for (long memberCharacterId : memberCharacterIds) {
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            memberCharacterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
        }
    }

    private void sendChatOutcome(ChannelHandlerContext ctx, int sequenceId, SendChatMessageOutcome outcome) {
        SendChatMessageResponse response =
                SendChatMessageResponse.newBuilder().setOutcome(outcome).build();
        send(ctx, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static Optional<dev.kage.chat.domain.ChatChannel> toDomainChannel(ChatChannel channel) {
        return switch (channel) {
            case CHAT_CHANNEL_WORLD -> Optional.of(dev.kage.chat.domain.ChatChannel.WORLD);
            case CHAT_CHANNEL_PRIVATE -> Optional.of(dev.kage.chat.domain.ChatChannel.PRIVATE);
            case CHAT_CHANNEL_AREA -> Optional.of(dev.kage.chat.domain.ChatChannel.AREA);
            case CHAT_CHANNEL_GUILD -> Optional.of(dev.kage.chat.domain.ChatChannel.GUILD);
            case CHAT_CHANNEL_PARTY -> Optional.of(dev.kage.chat.domain.ChatChannel.PARTY);
            default -> Optional.empty();
        };
    }

    private static final Map<ChatFacade.SendResult, SendChatMessageOutcome> SEND_CHAT_MESSAGE_OUTCOME_MAPPING = Map.of(
            ChatFacade.SendResult.OK,
            SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK,
            ChatFacade.SendResult.MESSAGE_BLANK,
            SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_BLANK,
            ChatFacade.SendResult.MESSAGE_TOO_LONG,
            SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_TOO_LONG,
            ChatFacade.SendResult.CONTAINS_BANNED_WORD,
            SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_CONTAINS_BANNED_WORD);

    private static SendChatMessageOutcome toProtoOutcome(ChatFacade.SendResult result) {
        return SEND_CHAT_MESSAGE_OUTCOME_MAPPING.get(result);
    }

    private static DialogueNodeResponse toResponse(DialogueNode node) {
        DialogueNodeResponse.Builder builder =
                DialogueNodeResponse.newBuilder().setNodeId(node.nodeId()).setTextKey(node.textKey());
        for (DialogueOption option : node.options()) {
            builder.addOptions(DialogueOptionView.newBuilder()
                    .setLabelKey(option.labelKey())
                    .setAction(option.action())
                    .build());
        }
        return builder.build();
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

    // Chiều ngược của SLOT_MAPPING (ADR-035: ListInventoryResponse.equipped + ItemCatalogEntry cần
    // đi từ domain ra proto). Dựng từ CHÍNH bảng trên thay vì gõ lại 7 dòng đối xứng — hai bảng
    // viết tay là hai bảng có thể lệch nhau.
    private static final Map<EquipmentSlotType, EquipmentSlot> PROTO_SLOT_MAPPING = SLOT_MAPPING.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    /** {@code null} (item không mặc được, {@code equip_slot} NULL trong DB) -> {@code UNSPECIFIED}. */
    private static EquipmentSlot toProtoSlot(EquipmentSlotType slot) {
        return slot == null ? EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED : PROTO_SLOT_MAPPING.get(slot);
    }

    private static String toErrorKey(InventoryFacade.EquipResult result) {
        return switch (result) {
            case OK -> "";
            case ITEM_NOT_FOUND -> "error.item_not_found";
            case WRONG_SLOT -> "error.wrong_equipment_slot";
            // KHÔNG dùng lại "error.level_too_low": khoá đó đã thuộc về CHỌN HỆ PHÁI ("chưa đủ
            // cấp 10 để chọn hệ phái") — dùng chung sẽ hiện sai câu cho một trong hai luồng.
            case LEVEL_TOO_LOW -> "error.equip_level_too_low";
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
            case THUY -> Faction.FACTION_THUY;
            case PHONG -> Faction.FACTION_PHONG;
            case LOI -> Faction.FACTION_LOI;
            case THO -> Faction.FACTION_THO;
        };
    }

    private static dev.kage.character.domain.Faction toDomainFaction(Faction faction) {
        return switch (faction) {
            case FACTION_HOA, FACTION_UNSPECIFIED, UNRECOGNIZED -> dev.kage.character.domain.Faction.HOA;
            case FACTION_THUY -> dev.kage.character.domain.Faction.THUY;
            case FACTION_PHONG -> dev.kage.character.domain.Faction.PHONG;
            case FACTION_LOI -> dev.kage.character.domain.Faction.LOI;
            case FACTION_THO -> dev.kage.character.domain.Faction.THO;
        };
    }

    /**
     * Đẩy một handler chạm DB sang {@code authExecutor}, kèm lưới an toàn cuối cùng: exception nào
     * lọt ra khỏi handler cũng thành {@code error.server_internal} gửi về đúng {@code sequenceId}.
     *
     * <p><b>Vì sao cần</b> (2026-09-24): handler chạy trên Virtual Thread của {@code authExecutor},
     * nên exception ở đó KHÔNG tới {@code exceptionCaught} của pipeline — nó chết lặng lẽ trong
     * executor và client chờ một response không bao giờ tới. Rà ngày 2026-09-24 thấy 56/61 handler
     * không tự bắt gì cả: một lần DB chập chờn là túi đồ, cửa hàng, bang, nhóm, giao dịch, thư...
     * đều treo. Handler nào cần thông báo RIÊNG (tên trùng, vượt giới hạn...) vẫn tự bắt trước —
     * lưới này chỉ đỡ phần còn lại.
     *
     * <p>Client đã sẵn đường cho lỗi này: panel đang chờ đúng {@code sequenceId} tự nhận, không ai
     * nhận thì {@code WorldScreen#onUnclaimedError} hiện ra cho người chơi.
     *
     * <p><b>Rate limit</b> (2026-09-24, NETWORK.md §13): chạy TRÊN EventLoop, TRƯỚC khi đẩy sang
     * {@code authExecutor} — lệnh bị từ chối không tốn một Virtual Thread hay một câu query nào. Vượt
     * ngưỡng một cửa sổ ⇒ trả {@code error.rate_limited} về đúng {@code sequenceId} (KHÁC MoveCommand
     * vốn bỏ im lặng: ở đây có panel đang chờ câu trả lời, im lặng là để nó treo); vượt LIÊN TIẾP ⇒
     * ngắt kết nối, cùng cơ chế {@link #moveCommandRateLimiter}.
     */
    // PMD AvoidCatchingGenericException: đúng ý — ranh giới I/O bất đồng bộ, PHẢI trả lời client dù
    // lỗi gì xảy ra (cùng lý do handleConnectBlocking).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void executeBlocking(ChannelHandlerContext ctx, int sequenceId, Runnable handler) {
        if (!dbCommandRateLimiter.tryAcquire()) {
            networkMetrics.recordRateLimitRejected("db");
            if (dbCommandRateLimiter.shouldDisconnect()) {
                LOG.warn("Lệnh chạm DB vượt rate limit liên tục — ngắt kết nối: characterId={}", boundCharacterId);
                ctx.close();
                return;
            }
            sendError(ctx, sequenceId, 429, "error.rate_limited");
            return;
        }
        authExecutor.execute(() -> {
            try {
                handler.run();
            } catch (RuntimeException e) {
                LOG.error("Handler lỗi ngoài dự kiến: sequenceId={}", sequenceId, e);
                sendError(ctx, sequenceId, 500, "error.server_internal");
            }
        });
    }

    private void sendError(ChannelHandlerContext ctx, int relatedSequenceId, int errorCode, String errorKey) {
        ErrorResponse error = ErrorResponse.newBuilder()
                .setErrorCode(errorCode)
                .setErrorKey(errorKey)
                .setRelatedSequenceId(relatedSequenceId)
                .build();
        send(ctx, Opcode.OPCODE_ERROR_VALUE, relatedSequenceId, error.toByteArray());
    }

    private void send(ChannelHandlerContext ctx, int opcode, int sequenceId, byte[] payload) {
        networkMetrics.recordPacketSent(opcode, payload.length);
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        networkMetrics.connectionClosed();
        // Mất kết nối (đóng chủ động, timeout, hay lỗi — exceptionCaught cũng dẫn tới sự kiện
        // này) — dọn entity ECS + AoI của nhân vật này (SERVER.md §9: 1 kết nối chết không được
        // để bia đỡ đạn đứng lại trong world).
        Long characterId = boundCharacterId;
        if (characterId != null) {
            releaseCharacter(characterId);
        }
    }

    /**
     * Dọn nhân vật khỏi world khi kết nối chết — gọi từ {@code channelInactive} (EventLoop) HOẶC từ
     * {@link #bindOrReleaseIfClosed} (Virtual Thread) nếu kết nối chết giữa lúc chọn nhân vật. Chạy
     * ĐÚNG MỘT LẦN mỗi kết nối: hai phía có thể cùng thấy kết nối chết.
     */
    private void releaseCharacter(long characterId) {
        if (!characterReleased.compareAndSet(false, true)) {
            return;
        }
        // Đọc vị trí cuối TRƯỚC unregister() (unregister xoá luôn khỏi lastKnownPosition —
        // đọc ở đây trên EventLoop, rẻ, không phải I/O, không tra lại sau trên authExecutor
        // vì lúc đó có thể đã bị unregister() xoá mất — xem javadoc CharacterPositionPersister#save).
        PositionSnapshot lastPosition = worldGateway.registry().lastKnownPosition(characterId);
        worldGateway.registry().unregister(characterId);
        // Phase 8 lát cắt 1 (ADR-026): nếu đang ở dungeon, detach khỏi ĐÚNG dungeon World +
        // dọn bookkeeping instance (dừng luôn World nếu rỗng) — KHÔNG re-attach world chính
        // (đã ngắt kết nối). Lát cắt 4: tương tự cho duel Arena — người mất kết nối tính THUA
        // (forfeit ngầm, ArenaInstanceRegistry tự chốt rating). false ở CẢ 2 = không ở dungeon
        // lẫn duel nào, detach world chính như trước giờ.
        boolean handledElsewhere = worldGateway.dungeonInstanceRegistry().handleDisconnectIfInDungeon(characterId)
                || worldGateway.arenaInstanceRegistry().handleDisconnectIfInDuel(characterId);
        if (!handledElsewhere) {
            worldGateway.mainWorldChannels().detachCharacter(characterId);
        }
        // SERVER.md §8: logout là 1 trong 3 mốc bắt buộc ghi vị trí. savePosition chạm DB
        // (blocking) — không được chạy trên EventLoop, đẩy sang authExecutor.
        if (lastPosition != null) {
            authExecutor.execute(
                    () -> worldGateway.positionPersister().save(characterId, lastPosition.x(), lastPosition.y()));
        }
        // channelInactive chạy trên EventLoop — friendFacade.listFriends chạm DB (blocking),
        // PHẢI đẩy sang authExecutor, không được gọi thẳng ở đây (server/CLAUDE.md: EventLoop
        // cấm query DB), cùng lý do savePosition ở trên.
        authExecutor.execute(() -> notifyFriendsPresenceChanged(characterId, false));
    }

    /** Báo cho bạn bè ĐANG online của {@code characterId} rằng người này vừa vào/thoát world. */
    private void notifyFriendsPresenceChanged(long characterId, boolean online) {
        List<Long> friendIds = friendFacade.listFriends(characterId);
        if (friendIds.isEmpty()) {
            return;
        }
        int opcode = online ? Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE : Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE;
        byte[] payload = online
                ? FriendOnlineEvent.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray()
                : FriendOfflineEvent.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray();
        for (long friendId : friendIds) {
            worldGateway.registry().sendToCharacterIfOnline(friendId, opcode, 0, payload);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("Lỗi không mong đợi trên kết nối {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
