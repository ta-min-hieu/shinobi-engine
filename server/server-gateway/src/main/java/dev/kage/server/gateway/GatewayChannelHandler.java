package dev.kage.server.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.account.api.AccountFacade;
import dev.kage.account.application.LoginResult;
import dev.kage.achievement.domain.ReputationRules;
import dev.kage.character.api.CharacterFacade;
import dev.kage.character.application.CharacterLimitExceededException;
import dev.kage.character.application.CharacterNotFoundException;
import dev.kage.character.application.DuplicateCharacterNameException;
import dev.kage.character.application.FactionChangePillRequiredException;
import dev.kage.character.application.FactionSelectionLevelTooLowException;
import dev.kage.character.application.FactionUnchangedException;
import dev.kage.character.application.SkillRankUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.IdempotencyKeyReusedException;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.SkillRankPolicy;
import dev.kage.chat.api.ChatFacade;
import dev.kage.checkin.api.ActivityFacade;
import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;
import dev.kage.friend.api.FriendFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildProgressionFacade;
import dev.kage.guild.domain.DonationOutcome;
import dev.kage.guild.domain.GuildProgressionPort;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.SocketGemUseCase;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.GemPolicy;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemSetProgress;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.mail.api.MailFacade;
import dev.kage.monster.api.WorldBossFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.AbandonBountyCommand;
import dev.kage.protocol.AbandonQuestCommand;
import dev.kage.protocol.AbandonQuestOutcome;
import dev.kage.protocol.AbandonQuestResponse;
import dev.kage.protocol.AcceptBountyCommand;
import dev.kage.protocol.AcceptDaoLuCommand;
import dev.kage.protocol.AcceptDiscipleCommand;
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
import dev.kage.protocol.AcceptSwornCommand;
import dev.kage.protocol.AcceptTradeRequestCommand;
import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.ApproveGuildJoinRequestCommand;
import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.ApproveGuildJoinRequestResponse;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.BidGuildAuctionCommand;
import dev.kage.protocol.BlockCharacterCommand;
import dev.kage.protocol.BuyGuildShopItemCommand;
import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyListingCommand;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.BuyReputationItemCommand;
import dev.kage.protocol.CancelFriendRequestCommand;
import dev.kage.protocol.CancelFriendRequestOutcome;
import dev.kage.protocol.CancelFriendRequestResponse;
import dev.kage.protocol.CancelListingCommand;
import dev.kage.protocol.CancelTradeCommand;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ChallengeDuelCommand;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.ClaimActivityChestCommand;
import dev.kage.protocol.ClaimMailCommand;
import dev.kage.protocol.ClaimMailOutcome;
import dev.kage.protocol.ClaimMailResponse;
import dev.kage.protocol.ClaimOnlineRewardCommand;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ClaimSeclusionCommand;
import dev.kage.protocol.ConfirmTradeCommand;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CraftItemCommand;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.CreateGuildCommand;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.CreateGuildResponse;
import dev.kage.protocol.CreateListingCommand;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.DeclareGuildWarCommand;
import dev.kage.protocol.DeclineDuelCommand;
import dev.kage.protocol.DeclineDuelOutcome;
import dev.kage.protocol.DeclineDuelResponse;
import dev.kage.protocol.DeleteMailCommand;
import dev.kage.protocol.DeleteMailOutcome;
import dev.kage.protocol.DeleteMailResponse;
import dev.kage.protocol.DepositItemCommand;
import dev.kage.protocol.DialogueEndedResponse;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.DialogueOptionView;
import dev.kage.protocol.DisbandGuildCommand;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.DisbandGuildResponse;
import dev.kage.protocol.DismantleItemCommand;
import dev.kage.protocol.DissolveDaoLuCommand;
import dev.kage.protocol.DonateToGuildCommand;
import dev.kage.protocol.DonateToGuildOutcome;
import dev.kage.protocol.DonateToGuildResponse;
import dev.kage.protocol.DuelChallengeDeclinedEvent;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.EnhanceItemCommand;
import dev.kage.protocol.EnhanceItemResponse;
import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.EnterTowerCommand;
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
import dev.kage.protocol.GatherHerbCommand;
import dev.kage.protocol.GetActivityRequest;
import dev.kage.protocol.GetBestiaryRequest;
import dev.kage.protocol.GetBountyBoardRequest;
import dev.kage.protocol.GetCharacterStatsRequest;
import dev.kage.protocol.GetCharacterStatsResponse;
import dev.kage.protocol.GetGuildWarStatusRequest;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.GetLeaderboardRequest;
import dev.kage.protocol.GetLeaderboardResponse;
import dev.kage.protocol.GetMyGuildRequest;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GetMyPartyRequest;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.GetOnlineRewardsRequest;
import dev.kage.protocol.GetPvpRatingRequest;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.GetRelationshipsRequest;
import dev.kage.protocol.GetReputationRequest;
import dev.kage.protocol.GetSeclusionStatusRequest;
import dev.kage.protocol.GetSpiritVeinStatusRequest;
import dev.kage.protocol.GetTowerStatusRequest;
import dev.kage.protocol.GetTradeStatusRequest;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.InvitePartyMemberCommand;
import dev.kage.protocol.InvitePartyMemberOutcome;
import dev.kage.protocol.InvitePartyMemberResponse;
import dev.kage.protocol.InviteSwornCommand;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.ItemSetProgressView;
import dev.kage.protocol.JoinPartyFinderCommand;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.KickGuildMemberCommand;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.KickGuildMemberResponse;
import dev.kage.protocol.KickPartyMemberCommand;
import dev.kage.protocol.KickPartyMemberOutcome;
import dev.kage.protocol.KickPartyMemberResponse;
import dev.kage.protocol.LeaderboardEntry;
import dev.kage.protocol.LeaderboardKind;
import dev.kage.protocol.LeaveGuildCommand;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.LeaveGuildResponse;
import dev.kage.protocol.LeaveMentorshipCommand;
import dev.kage.protocol.LeavePartyCommand;
import dev.kage.protocol.LeavePartyOutcome;
import dev.kage.protocol.LeavePartyResponse;
import dev.kage.protocol.LeaveSwornCommand;
import dev.kage.protocol.LinkedItemView;
import dev.kage.protocol.ListAchievementsRequest;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.ListFriendsRequest;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListGuildAuctionsRequest;
import dev.kage.protocol.ListGuildMembersRequest;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListGuildShopRequest;
import dev.kage.protocol.ListGuildSkillsRequest;
import dev.kage.protocol.ListHerbNodesRequest;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListMailRequest;
import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.ListMarketRequest;
import dev.kage.protocol.ListPartyFinderRequest;
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
import dev.kage.protocol.ListRecipesRequest;
import dev.kage.protocol.ListRefinementsRequest;
import dev.kage.protocol.ListShopItemsRequest;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.ListSkillRanksRequest;
import dev.kage.protocol.ListSkillRanksResponse;
import dev.kage.protocol.ListStorageRequest;
import dev.kage.protocol.ListWaypointsRequest;
import dev.kage.protocol.ListZoneMapRequest;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MailView;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveSpeedChangedEvent;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingFriendRequestView;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.protocol.PetView;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.PongResponse;
import dev.kage.protocol.PostPartyFinderCommand;
import dev.kage.protocol.ProposeDaoLuCommand;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.RebirthCommand;
import dev.kage.protocol.RebirthResponse;
import dev.kage.protocol.RedeemGiftCodeCommand;
import dev.kage.protocol.RefineItemCommand;
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
import dev.kage.protocol.RemovePartyFinderCommand;
import dev.kage.protocol.ReportPlayerCommand;
import dev.kage.protocol.RequestMentorCommand;
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
import dev.kage.protocol.SendEmoteCommand;
import dev.kage.protocol.SendFriendGiftCommand;
import dev.kage.protocol.SendFriendRequestCommand;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.SendGuildJoinRequestCommand;
import dev.kage.protocol.SendGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SendMailOutcome;
import dev.kage.protocol.SendMailResponse;
import dev.kage.protocol.SetActiveTitleCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.SetTradeOfferOutcome;
import dev.kage.protocol.SetTradeOfferResponse;
import dev.kage.protocol.ShopListingView;
import dev.kage.protocol.SkillRankView;
import dev.kage.protocol.SocketGemCommand;
import dev.kage.protocol.SocketGemOutcome;
import dev.kage.protocol.SocketGemResponse;
import dev.kage.protocol.TalkToNpcCommand;
import dev.kage.protocol.TeleportCommand;
import dev.kage.protocol.TradeCloseReason;
import dev.kage.protocol.TradeClosedEvent;
import dev.kage.protocol.TradeOfferItemView;
import dev.kage.protocol.TradePhase;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.TradeUpdatedEvent;
import dev.kage.protocol.UnblockCharacterCommand;
import dev.kage.protocol.UnequipItemCommand;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.UpgradeGuildSkillCommand;
import dev.kage.protocol.UpgradeSkillCommand;
import dev.kage.protocol.UpgradeSkillOutcome;
import dev.kage.protocol.UpgradeSkillResponse;
import dev.kage.protocol.UseItemCommand;
import dev.kage.protocol.UseItemResponse;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.WithdrawItemCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.pvp.api.PvpFacade;
import dev.kage.pvp.application.GetPvpRatingUseCase;
import dev.kage.quest.api.QuestFacade;
import dev.kage.server.world.CharacterBaseStats;
import dev.kage.server.world.CharacterStatSheet;
import dev.kage.server.world.Element;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.SkillRankSnapshot;
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

    // Cùng trần với MoveCommand — lý do ở combatCommandRateLimiter bên dưới.
    private static final int MAX_COMBAT_COMMANDS_PER_SECOND = 30;

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

    /**
     * Số gói vi phạm giao thức (opcode lạ, payload hỏng) một kết nối được gửi trong CẢ đời nó trước
     * khi bị đóng (quyết định chủ dự án 2026-10-11). Client thật không bao giờ gửi gói như thế, nên
     * con số chỉ cần đủ rộng cho một bản client lệch phiên bản lỡ gửi vài opcode server chưa biết.
     * Trước đó không có trần: một kết nối bơm gói hỏng vô hạn, mỗi gói một câu trả lời và một dòng log.
     */
    static final int MAX_PROTOCOL_VIOLATIONS = 10;

    /** Trần độ dài message của lỗi giải mã khi ghi log — đủ đọc ra loại lỗi và vài byte đầu. */
    private static final int MAX_LOGGED_DECODER_MESSAGE = 200;

    // Ghi trên EventLoop của kết nối này (channelRead0); AtomicInteger vì test đọc từ luồng khác và nó
    // chỉ được chạm khi có gói hỏng — không nằm trên đường nóng.
    private final java.util.concurrent.atomic.AtomicInteger protocolViolations =
            new java.util.concurrent.atomic.AtomicInteger();

    // Khoá i18n cho GoldChangedEvent lúc vừa vào world (ADR-035 Decision-3) — KHÔNG phải câu tiếng
    // Việt cứng. Các lý do còn lại (mua/bán/cường hoá/thư/giao dịch) do adapter server-database đặt,
    // xem GoldLedger javadoc vì sao chúng nằm ở đó chứ không ở đây.
    private static final String REASON_ENTER_WORLD = "gold.reason.enter_world";

    // Quyết định chủ dự án 2026-09-25: một nhân vật chỉ một phiên sống, phiên mới ĐÁ phiên cũ. Khoá
    // i18n gửi cho phiên bị đá (client về màn đăng nhập và hiện câu này), mã 409 = xung đột phiên.
    private static final String ERROR_LOGGED_IN_ELSEWHERE = "error.logged_in_elsewhere";
    private static final int ERROR_CODE_SESSION_CONFLICT = 409;

    // Trần chờ phiên cũ dọn xong. Bình thường chỉ vài chục mili giây (một lượt ghi vị trí + một tick
    // world); vượt trần nghĩa là có thứ kẹt (DB treo) — vẫn cho phiên mới vào thay vì khoá người chơi
    // ngoài cửa, vì mọi lần dọn/đăng ký đều đã có điều kiện theo kết nối nên không đè được phiên mới.
    private static final Duration EVICTION_TIMEOUT = Duration.ofSeconds(5);

    private final AccountFacade accountFacade;
    private final CharacterFacade characterFacade;
    // ADR-052 — quyền + audit GM; trần lệnh GM riêng theo kết nối (D8: 5 lệnh / 10 giây).
    private final dev.kage.gm.api.GmFacade gmFacade;
    private final dev.kage.gm.api.GameEventFacade gameEvents;
    private final GuildProgressionFacade guildProgression;
    private final dev.kage.checkin.api.CheckInFacade checkInFacade;
    private final RateLimiter gmRateLimiter = new RateLimiter(5, java.time.Duration.ofSeconds(10));
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
    private final WorldRouteLookup worldRoutes;
    private final NpcProximity npcProximity;
    private final SectStorageCommands storage;
    private final MarketCommands market;
    private final CraftingCommands crafting;
    private final GuildShopCommands guildShop;
    private final WorldBossFacade worldBoss;
    private final PartyFinderCommands partyFinder;
    private final FriendGiftCommands friendGifts;
    private final RelationshipCommands relations;
    private final DailyCommands daily;
    private final AdventureCommands adventure;
    private final GuildSkillCommands guildSkills;
    private final dev.kage.character.api.RebirthFacade rebirth;
    private final SpiritVeinCommands spiritVeins;
    private final GuildWarCommands guildWars;
    private final ServiceDeskCommands serviceDesk;
    private final CollectionCommands collections;
    private final ExpansionCommands expansion;
    /** ADR-115 — khoá an toàn của CHÍNH kết nối này (nạp lúc đăng nhập). */
    private final SecurityPinGate pinGate = new SecurityPinGate();

    private final ItemUseCooldown itemUseCooldown = new ItemUseCooldown();
    private final Executor authExecutor;
    // Tin chat của kết nối này chạy lần lượt (dòng 71(f)) — xem SerialExecutor.
    private final Executor chatExecutor;
    private final GatewayNetworkMetrics networkMetrics;
    private final GatewayTracing tracing;
    // Per-connection (handler đã là state per-connection, xem javadoc lớp) — không cần đồng bộ
    // hoá, chỉ được đọc/ghi trên Netty EventLoop của CHÍNH connection này.
    private final RateLimiter moveCommandRateLimiter =
            new RateLimiter(MAX_MOVE_COMMANDS_PER_SECOND, Duration.ofSeconds(1));
    // Chỉ chạm trong executeBlocking, mà executeBlocking chỉ được gọi từ các handle*Command/Request
    // do channelRead0 phát — tức trên EventLoop của CHÍNH kết nối này, cùng quy ước moveCommandRateLimiter.
    private final RateLimiter dbCommandRateLimiter = new RateLimiter(MAX_DB_COMMANDS_PER_SECOND, Duration.ofSeconds(1));
    // SetTarget/Attack/UseSkill (2026-10-02). Bản cũ không giới hạn vì "cooldown tự chặn tần suất
    // thật" — đúng về GAMEPLAY nhưng sai về TÀI NGUYÊN: mỗi gói vẫn là một WorldCommand trong inbox
    // KHÔNG giới hạn của WorldFacade, và tick thread (một luồng, chung cả khu vực) phải rút HẾT mỗi
    // tick. Một client spam gói 7 byte ở tốc độ đường truyền làm cả khu vực giật. Dùng chung trần với
    // MoveCommand: client thật gửi vài lệnh chiến đấu mỗi giây (đánh 1 đòn/giây kèm vài lần gửi lại,
    // đổi mục tiêu khi click, skill theo hồi chiêu) — cách trần cả chục lần, không đổi cảm giác chơi.
    private final RateLimiter combatCommandRateLimiter =
            new RateLimiter(MAX_COMBAT_COMMANDS_PER_SECOND, Duration.ofSeconds(1));
    // handleConnect/handleSelectCharacter set giá trị này từ Virtual Thread; channelRead0 của
    // packet TIẾP THEO có thể chạy trên EventLoop trước khi Virtual Thread đó xong (Netty không
    // chờ) — cần volatile để đảm bảo visibility giữa 2 thread.
    private volatile Long boundAccountId;

    /** Session token của lượt đăng nhập gần nhất trên kết nối này; {@code null} nếu chưa đăng nhập. */
    private volatile String sessionToken;

    private volatile Long boundCharacterId;

    // Lần gắn nhân vật HIỆN TẠI của kết nối (null = chưa/không gắn). Mỗi lần gắn có cờ dọn + mốc hoàn
    // tất riêng — một kết nối có thể rời C rồi gắn D (SERVER.md §5.1), nên cờ dọn không được là "một
    // lần mỗi kết nối" như bản cũ (bản đó để C ở lại world mãi mãi). Xem CharacterBinding.
    private volatile CharacterBinding binding;

    // Tuần tự hoá mọi lượt chọn nhân vật của CHÍNH kết nối này (chỉ giữ trên Virtual Thread) — hai
    // lượt chọn C và D gửi cùng lúc không được cùng vào world rồi ghi đè nhau. Thứ tự khoá luôn là
    // khoá này TRƯỚC, khoá nhân vật SAU, và không bao giờ giữ hai khoá nhân vật cùng lúc.
    private final ReentrantLock selectLock = new ReentrantLock();

    // Danh tính của kết nối này trong CharacterSessionRegistry — MỘT đối tượng cố định, vì sổ so
    // chủ phiên bằng danh tính (method reference tạo mới mỗi lần sẽ không bao giờ bằng nhau).
    private final CharacterSessionRegistry.Session session = new CharacterSessionRegistry.Session() {
        @Override
        public CompletableFuture<Void> evict(long characterId) {
            return evictCharacter(characterId, ERROR_LOGGED_IN_ELSEWHERE);
        }

        @Override
        public CompletableFuture<Void> evict(long characterId, String errorKey) {
            return evictCharacter(characterId, errorKey);
        }
    };

    // Gán cùng boundCharacterId — cần để đá kết nối này từ luồng của kết nối KHÁC.
    private volatile ChannelHandlerContext boundContext;

    // Bind sau khi field khác đã gán xong (field initializer chạy tuần tự trong <init>) — method
    // reference chỉ CHỤP this, không gọi ngay, nên thứ tự khai báo so với constructor không quan trọng.
    // Map.ofEntries thay Map.of (11 cặp vượt quá 10 overload cố định của Map.of) — cùng ngữ nghĩa.
    private final Map<Integer, PacketHandler> handlers = Map.ofEntries(
            Map.entry(Opcode.OPCODE_PING_VALUE, this::handlePing),
            Map.entry(Opcode.OPCODE_CONNECT_REQUEST_VALUE, this::handleConnect),
            Map.entry(Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE, this::handleListCharacters),
            Map.entry(Opcode.OPCODE_DELETE_CHARACTER_REQUEST_VALUE, this::handleDeleteCharacter), // ADR-205
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
            Map.entry(Opcode.OPCODE_LIST_STORAGE_REQUEST_VALUE, this::handleListStorageRequest),
            Map.entry(Opcode.OPCODE_LIST_ACHIEVEMENTS_REQUEST_VALUE, this::handleListAchievementsRequest),
            Map.entry(Opcode.OPCODE_LIST_MARKET_REQUEST_VALUE, this::handleListMarketRequest),
            Map.entry(Opcode.OPCODE_SOCKET_GEM_COMMAND_VALUE, this::handleSocketGemCommand),
            Map.entry(Opcode.OPCODE_CREATE_LISTING_COMMAND_VALUE, pinGuarded(this::handleCreateListingCommand)),
            Map.entry(Opcode.OPCODE_DONATE_TO_GUILD_COMMAND_VALUE, this::handleDonateToGuildCommand),
            Map.entry(Opcode.OPCODE_LIST_SKILL_RANKS_REQUEST_VALUE, this::handleListSkillRanksRequest),
            Map.entry(Opcode.OPCODE_UPGRADE_SKILL_COMMAND_VALUE, this::handleUpgradeSkillCommand),
            Map.entry(Opcode.OPCODE_BUY_LISTING_COMMAND_VALUE, pinGuarded(this::handleBuyListingCommand)),
            Map.entry(Opcode.OPCODE_CANCEL_LISTING_COMMAND_VALUE, this::handleCancelListingCommand),
            Map.entry(Opcode.OPCODE_SET_ACTIVE_TITLE_COMMAND_VALUE, this::handleSetActiveTitleCommand),
            Map.entry(Opcode.OPCODE_DEPOSIT_ITEM_COMMAND_VALUE, this::handleDepositItemCommand),
            Map.entry(Opcode.OPCODE_WITHDRAW_ITEM_COMMAND_VALUE, this::handleWithdrawItemCommand),
            Map.entry(Opcode.OPCODE_LIST_RECIPES_REQUEST_VALUE, this::handleListRecipesRequest),
            Map.entry(Opcode.OPCODE_CRAFT_ITEM_COMMAND_VALUE, this::handleCraftItemCommand),
            Map.entry(Opcode.OPCODE_LIST_GUILD_SHOP_REQUEST_VALUE, this::handleListGuildShopRequest),
            Map.entry(Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_COMMAND_VALUE, this::handleBuyGuildShopItemCommand),
            Map.entry(Opcode.OPCODE_LIST_GUILD_SKILLS_REQUEST_VALUE, this::handleListGuildSkillsRequest),
            Map.entry(Opcode.OPCODE_REBIRTH_COMMAND_VALUE, this::handleRebirthCommand),
            Map.entry(Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_REQUEST_VALUE, this::handleGetSpiritVeinStatusRequest),
            Map.entry(Opcode.OPCODE_UPGRADE_GUILD_SKILL_COMMAND_VALUE, this::handleUpgradeGuildSkillCommand),
            Map.entry(Opcode.OPCODE_LIST_PARTY_FINDER_REQUEST_VALUE, this::handleListPartyFinderRequest),
            Map.entry(Opcode.OPCODE_POST_PARTY_FINDER_COMMAND_VALUE, this::handlePostPartyFinderCommand),
            Map.entry(Opcode.OPCODE_JOIN_PARTY_FINDER_COMMAND_VALUE, this::handleJoinPartyFinderCommand),
            Map.entry(Opcode.OPCODE_REMOVE_PARTY_FINDER_COMMAND_VALUE, this::handleRemovePartyFinderCommand),
            Map.entry(Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE, this::handleGetItemCatalogRequest),
            Map.entry(Opcode.OPCODE_LIST_QUESTS_REQUEST_VALUE, this::handleListQuestsRequest),
            Map.entry(Opcode.OPCODE_ABANDON_QUEST_COMMAND_VALUE, this::handleAbandonQuestCommand),
            Map.entry(Opcode.OPCODE_GET_CHARACTER_STATS_REQUEST_VALUE, this::handleGetCharacterStatsRequest),
            Map.entry(Opcode.OPCODE_GET_LEADERBOARD_REQUEST_VALUE, this::handleGetLeaderboardRequest),
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
            Map.entry(Opcode.OPCODE_CANCEL_FRIEND_REQUEST_COMMAND_VALUE, this::handleCancelFriendRequestCommand),
            Map.entry(Opcode.OPCODE_SEND_FRIEND_GIFT_COMMAND_VALUE, this::handleSendFriendGiftCommand),
            // Lô 21 phần 1 — chặn (ADR-091), sư đồ (ADR-093), đạo lữ (ADR-094), biểu cảm (ADR-092).
            Map.entry(
                    Opcode.OPCODE_BLOCK_CHARACTER_COMMAND_VALUE,
                    storageCommand(
                            BlockCharacterCommand.parser(),
                            Opcode.OPCODE_BLOCK_CHARACTER_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.blocks().block(id, c.getTargetName()))),
            Map.entry(
                    Opcode.OPCODE_UNBLOCK_CHARACTER_COMMAND_VALUE,
                    storageCommand(
                            UnblockCharacterCommand.parser(),
                            Opcode.OPCODE_UNBLOCK_CHARACTER_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.blocks().unblock(id, c.getTargetName()))),
            Map.entry(
                    Opcode.OPCODE_GET_RELATIONSHIPS_REQUEST_VALUE,
                    storageCommand(
                            GetRelationshipsRequest.parser(),
                            Opcode.OPCODE_GET_RELATIONSHIPS_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.query().of(id))),
            Map.entry(
                    Opcode.OPCODE_REQUEST_MENTOR_COMMAND_VALUE,
                    storageCommand(
                            RequestMentorCommand.parser(),
                            Opcode.OPCODE_REQUEST_MENTOR_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.mentorship().request(id, c.getMentorName()))),
            Map.entry(
                    Opcode.OPCODE_ACCEPT_DISCIPLE_COMMAND_VALUE,
                    storageCommand(
                            AcceptDiscipleCommand.parser(),
                            Opcode.OPCODE_ACCEPT_DISCIPLE_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.mentorship().accept(id, c.getDiscipleName()))),
            Map.entry(
                    Opcode.OPCODE_LEAVE_MENTORSHIP_COMMAND_VALUE,
                    storageCommand(
                            LeaveMentorshipCommand.parser(),
                            Opcode.OPCODE_LEAVE_MENTORSHIP_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.mentorship().leave(id, c.getOtherName()))),
            Map.entry(
                    Opcode.OPCODE_PROPOSE_DAO_LU_COMMAND_VALUE,
                    storageCommand(
                            ProposeDaoLuCommand.parser(),
                            Opcode.OPCODE_PROPOSE_DAO_LU_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.daoLu().propose(id, c.getTargetName()))),
            Map.entry(
                    Opcode.OPCODE_ACCEPT_DAO_LU_COMMAND_VALUE,
                    storageCommand(
                            AcceptDaoLuCommand.parser(),
                            Opcode.OPCODE_ACCEPT_DAO_LU_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.daoLu().accept(id, c.getProposerName()))),
            Map.entry(
                    Opcode.OPCODE_DISSOLVE_DAO_LU_COMMAND_VALUE,
                    storageCommand(
                            DissolveDaoLuCommand.parser(),
                            Opcode.OPCODE_DISSOLVE_DAO_LU_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.daoLu().dissolve(id))),
            Map.entry(Opcode.OPCODE_SEND_EMOTE_COMMAND_VALUE, this::handleSendEmoteCommand),
            // Lô 22 phần 3 — tẩy luyện (ADR-111), đấu giá bang (ADR-112), Trấn Yêu Tháp (ADR-113).
            Map.entry(
                    Opcode.OPCODE_ENTER_TOWER_COMMAND_VALUE,
                    storageCommand(
                            EnterTowerCommand.parser(),
                            Opcode.OPCODE_ENTER_TOWER_RESPONSE_VALUE,
                            (h, id, c) -> h.tower().enter(id))),
            Map.entry(
                    Opcode.OPCODE_GET_TOWER_STATUS_REQUEST_VALUE,
                    storageCommand(
                            GetTowerStatusRequest.parser(),
                            Opcode.OPCODE_GET_TOWER_STATUS_RESPONSE_VALUE,
                            (h, id, c) -> h.tower().status(id))),
            Map.entry(
                    Opcode.OPCODE_LIST_GUILD_AUCTIONS_REQUEST_VALUE,
                    storageCommand(
                            ListGuildAuctionsRequest.parser(),
                            Opcode.OPCODE_LIST_GUILD_AUCTIONS_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.guildAuctions(id))),
            Map.entry(
                    Opcode.OPCODE_BID_GUILD_AUCTION_COMMAND_VALUE,
                    storageCommand(
                            BidGuildAuctionCommand.parser(),
                            Opcode.OPCODE_BID_GUILD_AUCTION_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.bidGuildAuction(id, c.getLotId(), c.getAmount()))),
            Map.entry(
                    Opcode.OPCODE_LIST_REFINEMENTS_REQUEST_VALUE,
                    storageCommand(
                            ListRefinementsRequest.parser(),
                            Opcode.OPCODE_LIST_REFINEMENTS_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.refinements(id))),
            Map.entry(
                    Opcode.OPCODE_REFINE_ITEM_COMMAND_VALUE,
                    storageCommand(
                            RefineItemCommand.parser(),
                            Opcode.OPCODE_REFINE_ITEM_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.refine(
                                    id, c.getItemId(), c.getLockedLine(), h::applyEquipmentBonusAndComputeMaxHp))),
            // Lô 22 phần 2 — sổ sưu tầm (ADR-108), danh vọng (ADR-109), kết nghĩa (ADR-110).
            Map.entry(
                    Opcode.OPCODE_GET_BESTIARY_REQUEST_VALUE,
                    storageCommand(
                            GetBestiaryRequest.parser(),
                            Opcode.OPCODE_GET_BESTIARY_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.bestiary(id))),
            Map.entry(
                    Opcode.OPCODE_GET_REPUTATION_REQUEST_VALUE,
                    storageCommand(
                            GetReputationRequest.parser(),
                            Opcode.OPCODE_GET_REPUTATION_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.reputation(id))),
            Map.entry(
                    Opcode.OPCODE_BUY_REPUTATION_ITEM_COMMAND_VALUE,
                    storageCommand(
                            BuyReputationItemCommand.parser(),
                            Opcode.OPCODE_BUY_REPUTATION_ITEM_RESPONSE_VALUE,
                            (h, id, c) -> h.collections.buy(id, c.getItemTemplateId()))),
            Map.entry(
                    Opcode.OPCODE_INVITE_SWORN_COMMAND_VALUE,
                    storageCommand(
                            InviteSwornCommand.parser(),
                            Opcode.OPCODE_INVITE_SWORN_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.sworn().invite(id, c.getTargetName()))),
            Map.entry(
                    Opcode.OPCODE_ACCEPT_SWORN_COMMAND_VALUE,
                    storageCommand(
                            AcceptSwornCommand.parser(),
                            Opcode.OPCODE_ACCEPT_SWORN_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.sworn().accept(id, c.getInviterName()))),
            Map.entry(
                    Opcode.OPCODE_LEAVE_SWORN_COMMAND_VALUE,
                    storageCommand(
                            LeaveSwornCommand.parser(),
                            Opcode.OPCODE_LEAVE_SWORN_RESPONSE_VALUE,
                            (h, id, c) -> h.relations.sworn().leave(id))),
            // Lô 22 phần 1 — mã quà (ADR-104), báo cáo (ADR-105), lịch hoạt động (ADR-106).
            Map.entry(
                    Opcode.OPCODE_REDEEM_GIFT_CODE_COMMAND_VALUE,
                    storageCommand(
                            RedeemGiftCodeCommand.parser(),
                            Opcode.OPCODE_REDEEM_GIFT_CODE_RESPONSE_VALUE,
                            (h, id, c) -> h.serviceDesk.redeem(id, c.getCode()))),
            Map.entry(
                    Opcode.OPCODE_REPORT_PLAYER_COMMAND_VALUE,
                    storageCommand(
                            ReportPlayerCommand.parser(),
                            Opcode.OPCODE_REPORT_PLAYER_RESPONSE_VALUE,
                            (h, id, c) -> h.serviceDesk.report(id, c))),
            Map.entry(
                    Opcode.OPCODE_GET_ACTIVITY_REQUEST_VALUE,
                    storageCommand(
                            GetActivityRequest.parser(),
                            Opcode.OPCODE_GET_ACTIVITY_RESPONSE_VALUE,
                            (h, id, c) -> h.serviceDesk.activity(id))),
            Map.entry(
                    Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_COMMAND_VALUE,
                    storageCommand(
                            ClaimActivityChestCommand.parser(),
                            Opcode.OPCODE_CLAIM_ACTIVITY_CHEST_RESPONSE_VALUE,
                            (h, id, c) -> h.serviceDesk.claimChest(id, c.getMilestone()))),
            // Lô 21 phần 4 — bang chiến (ADR-102).
            Map.entry(
                    Opcode.OPCODE_DECLARE_GUILD_WAR_COMMAND_VALUE,
                    storageCommand(
                            DeclareGuildWarCommand.parser(),
                            Opcode.OPCODE_DECLARE_GUILD_WAR_RESPONSE_VALUE,
                            (h, id, c) -> h.guildWars.declare(id, c.getTargetGuildName()))),
            Map.entry(
                    Opcode.OPCODE_GET_GUILD_WAR_STATUS_REQUEST_VALUE,
                    storageCommand(
                            GetGuildWarStatusRequest.parser(),
                            Opcode.OPCODE_GET_GUILD_WAR_STATUS_RESPONSE_VALUE,
                            (h, id, c) -> h.guildWars.status(id))),
            // Lô 21 phần 3 — truyền tống (ADR-100), truy nã (ADR-101).
            Map.entry(
                    Opcode.OPCODE_LIST_WAYPOINTS_REQUEST_VALUE,
                    storageCommand(
                            ListWaypointsRequest.parser(),
                            Opcode.OPCODE_LIST_WAYPOINTS_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.waypoints(id))),
            Map.entry( // ADR-141 — bản đồ vùng PvP/an toàn cho HUD
                    Opcode.OPCODE_LIST_ZONE_MAP_REQUEST_VALUE,
                    storageCommand(
                            ListZoneMapRequest.parser(),
                            Opcode.OPCODE_LIST_ZONE_MAP_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.zoneMap())),
            Map.entry(
                    Opcode.OPCODE_TELEPORT_COMMAND_VALUE,
                    storageCommand(
                            TeleportCommand.parser(),
                            Opcode.OPCODE_TELEPORT_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.teleport(id, c.getWaypointCode()))),
            Map.entry(
                    Opcode.OPCODE_GET_BOUNTY_BOARD_REQUEST_VALUE,
                    storageCommand(
                            GetBountyBoardRequest.parser(),
                            Opcode.OPCODE_GET_BOUNTY_BOARD_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.bountyBoard(id))),
            Map.entry(
                    Opcode.OPCODE_ACCEPT_BOUNTY_COMMAND_VALUE,
                    storageCommand(
                            AcceptBountyCommand.parser(),
                            Opcode.OPCODE_ACCEPT_BOUNTY_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.acceptBounty(id, c.getBountyCode()))),
            Map.entry(
                    Opcode.OPCODE_ABANDON_BOUNTY_COMMAND_VALUE,
                    storageCommand(
                            AbandonBountyCommand.parser(),
                            Opcode.OPCODE_ABANDON_BOUNTY_RESPONSE_VALUE,
                            (h, id, c) -> h.adventure.abandonBounty(id))),
            // Lô 21 phần 2 — bế quan (ADR-095), online (ADR-096), phân giải (ADR-097), linh thảo (ADR-098).
            Map.entry(
                    Opcode.OPCODE_GET_SECLUSION_STATUS_REQUEST_VALUE,
                    storageCommand(
                            GetSeclusionStatusRequest.parser(),
                            Opcode.OPCODE_GET_SECLUSION_STATUS_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.seclusionStatus(id))),
            Map.entry(
                    Opcode.OPCODE_CLAIM_SECLUSION_COMMAND_VALUE,
                    storageCommand(
                            ClaimSeclusionCommand.parser(),
                            Opcode.OPCODE_CLAIM_SECLUSION_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.claimSeclusion(id, c.getDoubled()))),
            Map.entry(
                    Opcode.OPCODE_GET_ONLINE_REWARDS_REQUEST_VALUE,
                    storageCommand(
                            GetOnlineRewardsRequest.parser(),
                            Opcode.OPCODE_GET_ONLINE_REWARDS_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.onlineRewards(id))),
            Map.entry(
                    Opcode.OPCODE_CLAIM_ONLINE_REWARD_COMMAND_VALUE,
                    storageCommand(
                            ClaimOnlineRewardCommand.parser(),
                            Opcode.OPCODE_CLAIM_ONLINE_REWARD_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.claimOnlineReward(id, c.getMilestoneMinutes()))),
            Map.entry(
                    Opcode.OPCODE_DISMANTLE_ITEM_COMMAND_VALUE,
                    pinGuarded(storageCommand(
                            DismantleItemCommand.parser(),
                            Opcode.OPCODE_DISMANTLE_ITEM_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.dismantle(id, c.getItemId())))),
            // Lô 23 — khoá an toàn (ADR-115). Theo TÀI KHOẢN, dùng được trước khi chọn nhân vật.
            Map.entry(
                    Opcode.OPCODE_GET_SECURITY_PIN_STATUS_REQUEST_VALUE,
                    accountCommand(
                            dev.kage.protocol.GetSecurityPinStatusRequest.parser(),
                            Opcode.OPCODE_GET_SECURITY_PIN_STATUS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.securityPin().status(id, h.pinGate))),
            Map.entry(
                    Opcode.OPCODE_SET_SECURITY_PIN_COMMAND_VALUE,
                    accountCommand(
                            dev.kage.protocol.SetSecurityPinCommand.parser(),
                            Opcode.OPCODE_SET_SECURITY_PIN_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.securityPin().set(id, c, h.pinGate))),
            Map.entry(
                    Opcode.OPCODE_VERIFY_SECURITY_PIN_COMMAND_VALUE,
                    accountCommand(
                            dev.kage.protocol.VerifySecurityPinCommand.parser(),
                            Opcode.OPCODE_VERIFY_SECURITY_PIN_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.securityPin().verify(id, c.getPin(), h.pinGate))),
            // Lô 24 — động phủ (ADR-129).
            Map.entry(
                    Opcode.OPCODE_CAVE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.CaveCommand.parser(),
                            Opcode.OPCODE_CAVE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.cave().handle(id, c))),
            // Lô 24 — pháp bảo (ADR-127).
            Map.entry(
                    Opcode.OPCODE_USE_ARTIFACT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.UseArtifactCommand.parser(),
                            Opcode.OPCODE_USE_ARTIFACT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.artifacts().use(id, h.currentWorldFacadeIfPresent(id)))),
            // Lô 23 — Thiên Kiếp (ADR-126).
            Map.entry(
                    Opcode.OPCODE_START_TRIBULATION_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.StartTribulationCommand.parser(),
                            Opcode.OPCODE_START_TRIBULATION_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().tribulation.start(id))),
            // Lô 23 — luận võ đại hội (ADR-125).
            Map.entry(
                    Opcode.OPCODE_REGISTER_TOURNAMENT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.RegisterTournamentCommand.parser(),
                            Opcode.OPCODE_REGISTER_TOURNAMENT_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().tournament.register(id))),
            // Lô 23 — thu phục yêu thú (ADR-124).
            Map.entry(
                    Opcode.OPCODE_TAME_MONSTER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.TameMonsterCommand.parser(),
                            Opcode.OPCODE_TAME_MONSTER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.taming().tame(id, c, h.currentWorldFacadeIfPresent(id)))),
            // Lô 23 — kỳ ngộ (ADR-121).
            Map.entry(
                    Opcode.OPCODE_CHOOSE_ENCOUNTER_OPTION_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ChooseEncounterOptionCommand.parser(),
                            Opcode.OPCODE_CHOOSE_ENCOUNTER_OPTION_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().encounters.choose(id, c))),
            // Lô 29 — khoá vật phẩm (ADR-159); cổng Mật Khẩu Bảo Hộ như các đường làm đồ rời túi.
            Map.entry(
                    Opcode.OPCODE_SET_ITEM_LOCK_COMMAND_VALUE,
                    pinGuarded(storageCommand(
                            dev.kage.protocol.SetItemLockCommand.parser(),
                            Opcode.OPCODE_SET_ITEM_LOCK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().itemLock().set(id, c)))),
            // Lô 31 — lưu bộ trang bị (ADR-175); mặc bộ đi qua đúng đường mặc đồ thường.
            Map.entry(
                    Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SaveEquipmentPresetCommand.parser(),
                            Opcode.OPCODE_SAVE_EQUIPMENT_PRESET_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().equipmentPresets().save(id, c))),
            Map.entry(
                    Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ApplyEquipmentPresetCommand.parser(),
                            Opcode.OPCODE_APPLY_EQUIPMENT_PRESET_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion
                                    .lot31()
                                    .equipmentPresets()
                                    .apply(id, c, h::applyEquipmentBonusAndComputeMaxHp))),
            Map.entry(
                    Opcode.OPCODE_GET_EQUIPMENT_PRESETS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetEquipmentPresetsRequest.parser(),
                            Opcode.OPCODE_GET_EQUIPMENT_PRESETS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().equipmentPresets().list(id))),
            // Lô 31 — dùng nhiều vật phẩm (ADR-176); lặp đúng đường dùng một món / mở một túi phúc.
            Map.entry(
                    Opcode.OPCODE_USE_ITEMS_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.UseItemsCommand.parser(),
                            Opcode.OPCODE_USE_ITEMS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion
                                    .lot31()
                                    .bulkUse()
                                    .useMany(
                                            id,
                                            c,
                                            new BulkUseCommands.Hooks(
                                                    GatewayChannelHandler::useItemErrorKey,
                                                    h.expansion.lot29().expBuff()::announce)))),
            Map.entry(
                    Opcode.OPCODE_OPEN_LUCKY_BAGS_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.OpenLuckyBagsCommand.parser(),
                            Opcode.OPCODE_OPEN_LUCKY_BAGS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().bulkUse().openMany(id, c))),
            // Lô 31 — lịch sử phường thị (ADR-177); chỉ đọc.
            Map.entry(
                    Opcode.OPCODE_GET_MARKET_HISTORY_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetMarketHistoryRequest.parser(),
                            Opcode.OPCODE_GET_MARKET_HISTORY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().marketHistory(id))),
            // Lô 31 — tự trả lời khi vắng (ADR-178); chỉ bộ nhớ gateway.
            Map.entry(
                    Opcode.OPCODE_SET_AWAY_MESSAGE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetAwayMessageCommand.parser(),
                            Opcode.OPCODE_SET_AWAY_MESSAGE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().away().set(id, c))),
            // Lô 31 — thông báo bang (ADR-179); chỉ bang chủ, báo cả bang.
            Map.entry(
                    Opcode.OPCODE_SET_GUILD_NOTICE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetGuildNoticeCommand.parser(),
                            Opcode.OPCODE_SET_GUILD_NOTICE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().guildNotice().set(id, c))),
            // Lô 31 — nhật ký bang (ADR-180); chỉ đọc, mọi thành viên.
            Map.entry(
                    Opcode.OPCODE_GET_GUILD_LOG_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetGuildLogRequest.parser(),
                            Opcode.OPCODE_GET_GUILD_LOG_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot31().guildLog(id))),
            // Lô 31 — chúc mừng bạn bè đột phá (ADR-182).
            Map.entry(
                    Opcode.OPCODE_CONGRATULATE_FRIEND_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.CongratulateFriendCommand.parser(),
                            Opcode.OPCODE_CONGRATULATE_FRIEND_RESPONSE_VALUE,
                            (h, id, c) ->
                                    h.expansion.lot31().friendBreakthrough().congratulate(id, c))),
            // Lô 32 — nhường chức bang chủ (ADR-183).
            Map.entry(
                    Opcode.OPCODE_TRANSFER_GUILD_LEADER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.TransferGuildLeaderCommand.parser(),
                            Opcode.OPCODE_TRANSFER_GUILD_LEADER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().guildLeadership().transfer(id, c))),
            // Lô 32 — phó bang chủ (ADR-184).
            Map.entry(
                    Opcode.OPCODE_SET_GUILD_VICE_LEADER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetGuildViceLeaderCommand.parser(),
                            Opcode.OPCODE_SET_GUILD_VICE_LEADER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().guildLeadership().setVice(id, c))),
            // Lô 32 — bảng chiêu mộ bang (ADR-185).
            Map.entry(
                    Opcode.OPCODE_POST_GUILD_RECRUITMENT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.PostGuildRecruitmentCommand.parser(),
                            Opcode.OPCODE_POST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().guildRecruitment().post(id, c))),
            Map.entry(
                    Opcode.OPCODE_LIST_GUILD_RECRUITMENT_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListGuildRecruitmentRequest.parser(),
                            Opcode.OPCODE_LIST_GUILD_RECRUITMENT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().guildRecruitment().list())),
            // Lô 32 — điểm danh bang (ADR-186).
            Map.entry(
                    Opcode.OPCODE_GUILD_CHECK_IN_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.GuildCheckInCommand.parser(),
                            Opcode.OPCODE_GUILD_CHECK_IN_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().guildCheckIn(id))),
            // Lô 32 — lịch sử giao dịch (ADR-189); chỉ đọc.
            Map.entry(
                    Opcode.OPCODE_GET_TRADE_HISTORY_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetTradeHistoryRequest.parser(),
                            Opcode.OPCODE_GET_TRADE_HISTORY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot32().tradeHistory(id))),
            // Lô 33 — nhường trưởng nhóm (ADR-191).
            Map.entry(
                    Opcode.OPCODE_TRANSFER_PARTY_LEADER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.TransferPartyLeaderCommand.parser(),
                            Opcode.OPCODE_TRANSFER_PARTY_LEADER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().transferPartyLeader(id, c))),
            // Lô 33 — đổi tên linh thú (ADR-193).
            Map.entry(
                    Opcode.OPCODE_RENAME_PET_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.RenamePetCommand.parser(),
                            Opcode.OPCODE_RENAME_PET_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().renamePet(id, c))),
            // Lô 33 — danh sách cừu nhân (ADR-194).
            Map.entry(
                    Opcode.OPCODE_LIST_ENEMIES_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListEnemiesRequest.parser(),
                            Opcode.OPCODE_LIST_ENEMIES_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().listEnemies(id))),
            Map.entry(
                    Opcode.OPCODE_REMOVE_ENEMY_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.RemoveEnemyCommand.parser(),
                            Opcode.OPCODE_REMOVE_ENEMY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().removeEnemy(id, c))),
            // Lô 33 — theo dõi phường thị (ADR-196).
            Map.entry(
                    Opcode.OPCODE_SET_MARKET_WATCH_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetMarketWatchCommand.parser(),
                            Opcode.OPCODE_SET_MARKET_WATCH_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().setMarketWatch(id, c))),
            Map.entry(
                    Opcode.OPCODE_LIST_MARKET_WATCH_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListMarketWatchRequest.parser(),
                            Opcode.OPCODE_LIST_MARKET_WATCH_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().listMarketWatch(id))),
            // Lô 34 — đếm ngược mốc làm mới (ADR-200).
            Map.entry(
                    Opcode.OPCODE_GET_RESET_TIMERS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetResetTimersRequest.parser(),
                            Opcode.OPCODE_GET_RESET_TIMERS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot33().resetTimers())),
            // Lô 34 — mua lại đồ đã bán (ADR-202).
            Map.entry(
                    Opcode.OPCODE_LIST_BUYBACK_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListBuybackRequest.parser(),
                            Opcode.OPCODE_LIST_BUYBACK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot34().listBuyback(id))),
            Map.entry(
                    Opcode.OPCODE_BUYBACK_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.BuybackCommand.parser(),
                            Opcode.OPCODE_BUYBACK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot34().buyBack(id, c))),
            // Lô 34 — lọc nhặt đồ (ADR-204).
            Map.entry(
                    Opcode.OPCODE_SET_AUTO_SELL_JUNK_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetAutoSellJunkCommand.parser(),
                            Opcode.OPCODE_SET_AUTO_SELL_JUNK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot34().setAutoSellJunk(id, c))),
            // Lô 35 — góp vật phẩm cho bang (ADR-207).
            Map.entry(
                    Opcode.OPCODE_DONATE_ITEM_TO_GUILD_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.DonateItemToGuildCommand.parser(),
                            Opcode.OPCODE_DONATE_ITEM_TO_GUILD_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot35().donateItem(id, c))),
            // Lô 35 — lịch sử bang chiến (ADR-208).
            Map.entry(
                    Opcode.OPCODE_GET_GUILD_WAR_HISTORY_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetGuildWarHistoryRequest.parser(),
                            Opcode.OPCODE_GET_GUILD_WAR_HISTORY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot35().warHistory(id))),
            // Lô 34 — tự tìm đường tới NPC (ADR-206).
            Map.entry(
                    Opcode.OPCODE_LIST_NPC_LOCATIONS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListNpcLocationsRequest.parser(),
                            Opcode.OPCODE_LIST_NPC_LOCATIONS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot34().listNpcLocations(id))),
            // Lô 30 — đổi tên bang hội (ADR-172).
            Map.entry(
                    Opcode.OPCODE_RENAME_GUILD_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.RenameGuildCommand.parser(),
                            Opcode.OPCODE_RENAME_GUILD_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().guildRename().rename(id, c))),
            // Lô 30 — ghi chú bạn bè (ADR-173); hiện kèm tên trong danh sách bạn.
            Map.entry(
                    Opcode.OPCODE_SET_FRIEND_NOTE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetFriendNoteCommand.parser(),
                            Opcode.OPCODE_SET_FRIEND_NOTE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().friendNotes().setNote(id, c))),
            // Lô 30 — soi trang bị người khác (ADR-171); Lực Chiến theo đúng công thức bảng nhân vật.
            Map.entry(
                    Opcode.OPCODE_INSPECT_CHARACTER_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.InspectCharacterRequest.parser(),
                            Opcode.OPCODE_INSPECT_CHARACTER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().inspect().inspect(id, c, target -> h.statSheetOf(target)
                                    .combatPower()))),
            Map.entry(
                    Opcode.OPCODE_SET_INSPECT_PRIVACY_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetInspectPrivacyCommand.parser(),
                            Opcode.OPCODE_SET_INSPECT_PRIVACY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().inspect().setPrivacy(id, c))),
            // Lô 30 — bán nhanh đồ rác (ADR-170).
            Map.entry(
                    Opcode.OPCODE_GET_JUNK_PREVIEW_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetJunkPreviewRequest.parser(),
                            Opcode.OPCODE_GET_JUNK_PREVIEW_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().junkSale().preview(id))),
            Map.entry(
                    Opcode.OPCODE_SELL_JUNK_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SellJunkCommand.parser(),
                            Opcode.OPCODE_SELL_JUNK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().junkSale().sell(id))),
            // Lô 30 — sổ vàng cá nhân (ADR-169).
            Map.entry(
                    Opcode.OPCODE_GET_GOLD_HISTORY_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetGoldHistoryRequest.parser(),
                            Opcode.OPCODE_GET_GOLD_HISTORY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().goldHistory(id))),
            // Lô 30 — tháo Linh Ngọc (ADR-168); áp lại chỉ số nếu món đang mặc.
            Map.entry(
                    Opcode.OPCODE_UNSOCKET_GEM_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.UnsocketGemCommand.parser(),
                            Opcode.OPCODE_UNSOCKET_GEM_RESPONSE_VALUE,
                            (h, id, c) ->
                                    h.expansion.lot30().unsocketGem(id, c, h::applyEquipmentBonusAndComputeMaxHp))),
            // Lô 30 — nhận hết thư (ADR-167).
            Map.entry(
                    Opcode.OPCODE_CLAIM_ALL_MAIL_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ClaimAllMailCommand.parser(),
                            Opcode.OPCODE_CLAIM_ALL_MAIL_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot30().mailBulk().claimAll(id, c))),
            // Lô 29 — huyễn hình trang phục (ADR-166).
            Map.entry(
                    Opcode.OPCODE_SET_COSTUME_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SetCostumeCommand.parser(),
                            Opcode.OPCODE_SET_COSTUME_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().costume().set(id, c))),
            // Lô 29 — túi phúc (ADR-163).
            Map.entry(
                    Opcode.OPCODE_GET_LUCKY_BAGS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetLuckyBagsRequest.parser(),
                            Opcode.OPCODE_GET_LUCKY_BAGS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().luckyBag().bags(id))),
            Map.entry(
                    Opcode.OPCODE_OPEN_LUCKY_BAG_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.OpenLuckyBagCommand.parser(),
                            Opcode.OPCODE_OPEN_LUCKY_BAG_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().luckyBag().open(id, c))),
            // Lô 29 — quét nhanh Bí Cảnh (ADR-162).
            Map.entry(
                    Opcode.OPCODE_SWEEP_DUNGEON_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SweepDungeonCommand.parser(),
                            Opcode.OPCODE_SWEEP_DUNGEON_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().dungeonSweep().sweep(id, c))),
            // Lô 29 — ghép Linh Ngọc (ADR-161).
            Map.entry(
                    Opcode.OPCODE_GET_GEM_RECIPES_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetGemRecipesRequest.parser(),
                            Opcode.OPCODE_GET_GEM_RECIPES_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().gemCombine().recipes(id))),
            Map.entry(
                    Opcode.OPCODE_COMBINE_GEMS_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.CombineGemsCommand.parser(),
                            Opcode.OPCODE_COMBINE_GEMS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().gemCombine().combine(id, c))),
            // Lô 29 — sắp xếp túi đồ (ADR-160).
            Map.entry(
                    Opcode.OPCODE_SORT_INVENTORY_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SortInventoryCommand.parser(),
                            Opcode.OPCODE_SORT_INVENTORY_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.lot29().sortInventory(id))),
            // Lô 28 — cửa hàng điểm vinh dự (ADR-155).
            Map.entry(
                    Opcode.OPCODE_GET_HONOR_SHOP_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetHonorShopRequest.parser(),
                            Opcode.OPCODE_GET_HONOR_SHOP_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.honor().shop(id))),
            Map.entry(
                    Opcode.OPCODE_BUY_HONOR_ITEM_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.BuyHonorItemCommand.parser(),
                            Opcode.OPCODE_BUY_HONOR_ITEM_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.honor().buy(id, c))),
            // Lô 28 — nhiệm vụ tuần cá nhân (ADR-154).
            Map.entry(
                    Opcode.OPCODE_GET_WEEKLY_TASKS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetWeeklyTasksRequest.parser(),
                            Opcode.OPCODE_GET_WEEKLY_TASKS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.weeklyTasks().list(id))),
            Map.entry(
                    Opcode.OPCODE_CLAIM_WEEKLY_TASK_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ClaimWeeklyTaskCommand.parser(),
                            Opcode.OPCODE_CLAIM_WEEKLY_TASK_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.weeklyTasks().claim(id, c))),
            // Lô 28 — quà mốc cảnh giới (ADR-153).
            Map.entry(
                    Opcode.OPCODE_GET_REALM_MILESTONES_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetRealmMilestonesRequest.parser(),
                            Opcode.OPCODE_GET_REALM_MILESTONES_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.milestones().board(id))),
            Map.entry(
                    Opcode.OPCODE_CLAIM_REALM_MILESTONE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ClaimRealmMilestoneCommand.parser(),
                            Opcode.OPCODE_CLAIM_REALM_MILESTONE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.milestones().claim(id, c))),
            // Lô 28 — Truyền Âm Phù (ADR-152).
            Map.entry(
                    Opcode.OPCODE_BROADCAST_MESSAGE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.BroadcastMessageCommand.parser(),
                            Opcode.OPCODE_BROADCAST_MESSAGE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.broadcast().broadcast(id, c))),
            // Lô 27 — vòng quay may mắn (ADR-151).
            Map.entry(
                    Opcode.OPCODE_GET_LUCKY_WHEEL_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetLuckyWheelRequest.parser(),
                            Opcode.OPCODE_GET_LUCKY_WHEEL_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.luckyWheel().wheel(id))),
            Map.entry(
                    Opcode.OPCODE_SPIN_LUCKY_WHEEL_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.SpinLuckyWheelCommand.parser(),
                            Opcode.OPCODE_SPIN_LUCKY_WHEEL_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.luckyWheel().spin(id))),
            // Lô 27 — xúc xắc chia đồ nhóm (ADR-150).
            Map.entry(
                    Opcode.OPCODE_LOOT_ROLL_CHOICE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.LootRollChoiceCommand.parser(),
                            Opcode.OPCODE_LOOT_ROLL_CHOICE_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().lootRolls.choose(id, c))),
            // Lô 27 — quẻ bói hằng ngày (ADR-149).
            Map.entry(
                    Opcode.OPCODE_DRAW_FORTUNE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.DrawFortuneCommand.parser(),
                            Opcode.OPCODE_DRAW_FORTUNE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.fortune().draw(id))),
            // Lô 27 — Dịch Danh Phù (ADR-148).
            Map.entry(
                    Opcode.OPCODE_RENAME_CHARACTER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.RenameCharacterCommand.parser(),
                            Opcode.OPCODE_RENAME_CHARACTER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.rename().rename(id, c))),
            // Lô 27 — Tàng Bảo Đồ (ADR-147), hỏi cuộc tìm chỉ đọc (D9).
            Map.entry(
                    Opcode.OPCODE_GET_TREASURE_HUNT_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetTreasureHuntRequest.parser(),
                            Opcode.OPCODE_GET_TREASURE_HUNT_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().treasure.view(id))),
            Map.entry(
                    Opcode.OPCODE_TREASURE_HUNT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.TreasureHuntCommand.parser(),
                            Opcode.OPCODE_TREASURE_HUNT_RESPONSE_VALUE,
                            (h, id, c) -> h.worldGateway.registry().treasure.handle(id, c))),
            // Lô 23 — luyện khí (ADR-120), cùng lò với luyện đan.
            Map.entry(
                    Opcode.OPCODE_LIST_FORGE_RECIPES_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListForgeRecipesRequest.parser(),
                            Opcode.OPCODE_LIST_FORGE_RECIPES_RESPONSE_VALUE,
                            (h, id, c) -> h.crafting.listForge())),
            Map.entry(
                    Opcode.OPCODE_FORGE_ITEM_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ForgeItemCommand.parser(),
                            Opcode.OPCODE_FORGE_ITEM_RESPONSE_VALUE,
                            (h, id, c) -> h.crafting.forge(id, c))),
            // Lô 23 — linh điền (ADR-119).
            Map.entry(
                    Opcode.OPCODE_GET_SPIRIT_FIELD_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetSpiritFieldRequest.parser(),
                            Opcode.OPCODE_GET_SPIRIT_FIELD_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.spiritField().view(id, c.getOwnerName()))),
            Map.entry(
                    Opcode.OPCODE_PLANT_SEED_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.PlantSeedCommand.parser(),
                            Opcode.OPCODE_PLANT_SEED_RESPONSE_VALUE,
                            (h, id, c) ->
                                    h.expansion.spiritField().plant(id, c.getPlotIndex(), c.getSeedItemTemplateId()))),
            Map.entry(
                    Opcode.OPCODE_HARVEST_PLOT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.HarvestPlotCommand.parser(),
                            Opcode.OPCODE_HARVEST_PLOT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.spiritField().harvest(id, c.getPlotIndex()))),
            Map.entry(
                    Opcode.OPCODE_WATER_PLOT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.WaterPlotCommand.parser(),
                            Opcode.OPCODE_WATER_PLOT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.spiritField().water(id, c.getOwnerName(), c.getPlotIndex()))),
            Map.entry(
                    Opcode.OPCODE_UNLOCK_PLOT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.UnlockPlotCommand.parser(),
                            Opcode.OPCODE_UNLOCK_PLOT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.spiritField().unlock(id))),
            // Lô 23 — thương nhân du hành (ADR-118).
            Map.entry(
                    Opcode.OPCODE_GET_TRAVELING_MERCHANT_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.GetTravelingMerchantRequest.parser(),
                            Opcode.OPCODE_GET_TRAVELING_MERCHANT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.merchant().status())),
            Map.entry(
                    Opcode.OPCODE_BUY_FROM_MERCHANT_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.BuyFromMerchantCommand.parser(),
                            Opcode.OPCODE_BUY_FROM_MERCHANT_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.merchant().buy(id, c.getItemTemplateId()))),
            // Lô 23 — hồi sinh tại chỗ (ADR-117).
            Map.entry(
                    Opcode.OPCODE_REVIVE_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.ReviveCommand.parser(),
                            Opcode.OPCODE_REVIVE_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion
                                    .revival()
                                    .revive(id, c.getInPlace(), h.currentWorldFacadeIfPresent(id)))),
            // Lô 23 — cầu mua (ADR-116). Đặt lệnh và bán vào lệnh qua khoá an toàn như treo/mua ở phường thị.
            Map.entry(
                    Opcode.OPCODE_LIST_BUY_ORDERS_REQUEST_VALUE,
                    storageCommand(
                            dev.kage.protocol.ListBuyOrdersRequest.parser(),
                            Opcode.OPCODE_LIST_BUY_ORDERS_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.buyOrders().list(id, c.getItemTemplateId()))),
            Map.entry(
                    Opcode.OPCODE_CREATE_BUY_ORDER_COMMAND_VALUE,
                    pinGuarded(storageCommand(
                            dev.kage.protocol.CreateBuyOrderCommand.parser(),
                            Opcode.OPCODE_CREATE_BUY_ORDER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.buyOrders().create(id, c)))),
            Map.entry(
                    Opcode.OPCODE_SELL_TO_BUY_ORDER_COMMAND_VALUE,
                    pinGuarded(storageCommand(
                            dev.kage.protocol.SellToBuyOrderCommand.parser(),
                            Opcode.OPCODE_SELL_TO_BUY_ORDER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.buyOrders().sell(id, c)))),
            Map.entry(
                    Opcode.OPCODE_CANCEL_BUY_ORDER_COMMAND_VALUE,
                    storageCommand(
                            dev.kage.protocol.CancelBuyOrderCommand.parser(),
                            Opcode.OPCODE_CANCEL_BUY_ORDER_RESPONSE_VALUE,
                            (h, id, c) -> h.expansion.buyOrders().cancel(id, c.getOrderId()))),
            Map.entry(
                    Opcode.OPCODE_LIST_HERB_NODES_REQUEST_VALUE,
                    storageCommand(
                            ListHerbNodesRequest.parser(),
                            Opcode.OPCODE_LIST_HERB_NODES_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.herbs().list(id))),
            Map.entry(
                    Opcode.OPCODE_GATHER_HERB_COMMAND_VALUE,
                    storageCommand(
                            GatherHerbCommand.parser(),
                            Opcode.OPCODE_GATHER_HERB_RESPONSE_VALUE,
                            (h, id, c) -> h.daily.herbs().gather(id, c.getNodeId()))),
            Map.entry(Opcode.OPCODE_LIST_FRIENDS_REQUEST_VALUE, this::handleListFriendsRequest),
            Map.entry(
                    Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_REQUEST_VALUE,
                    this::handleListPendingFriendRequestsRequest),
            Map.entry(
                    Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE,
                    pinGuardedWhen(
                            SendMailCommand.parser(),
                            GatewayChannelHandler::carriesValue,
                            this::handleSendMailCommand)),
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
            Map.entry(Opcode.OPCODE_REQUEST_TRADE_COMMAND_VALUE, pinGuarded(this::handleRequestTradeCommand)),
            Map.entry(
                    Opcode.OPCODE_ACCEPT_TRADE_REQUEST_COMMAND_VALUE,
                    pinGuarded(this::handleAcceptTradeRequestCommand)),
            Map.entry(Opcode.OPCODE_CANCEL_TRADE_COMMAND_VALUE, this::handleCancelTradeCommand),
            Map.entry(Opcode.OPCODE_SET_TRADE_OFFER_COMMAND_VALUE, pinGuarded(this::handleSetTradeOfferCommand)),
            Map.entry(Opcode.OPCODE_CONFIRM_TRADE_COMMAND_VALUE, pinGuarded(this::handleConfirmTradeCommand)),
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
        this.gmFacade = facades.gmFacade();
        this.checkInFacade = facades.progression().checkInFacade();
        this.gameEvents = facades.progression().gameEvents();
        this.guildProgression = facades.progression().guildProgression();
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
        this.worldRoutes = WorldRouteLookup.of(worldGateway);
        this.npcProximity = new NpcProximity(
                worldGateway.mainWorldChannels().worldContent().npcs(),
                this::mainWorldPositionOf,
                worldGateway.mainWorldChannels()::dynamicNpcs);
        this.storage = SectStorageCommands.of(inventoryFacade, npcProximity, facades);
        this.market = MarketCommands.of(facades, worldGateway);
        this.crafting = new CraftingCommands(facades.progression().crafting(), characterFacade, npcProximity);
        this.guildShop = new GuildShopCommands(facades.progression().guildShop());
        this.guildSkills = new GuildSkillCommands(facades.progression().guildSkills());
        this.rebirth = facades.progression().rebirth();
        this.spiritVeins = new SpiritVeinCommands(facades.progression().spiritVeins(), java.time.Clock.systemUTC());
        this.guildWars = GuildWarCommands.of(facades, worldGateway.registry());
        this.serviceDesk = ServiceDeskCommands.of(facades, worldGateway.registry());
        this.collections = new CollectionCommands(facades.progression().collections(), inventoryFacade);
        this.expansion = ExpansionCommands.of(facades, worldGateway, npcProximity);
        this.friendGifts = new FriendGiftCommands(
                facades.progression().friendGifts(), characterFacade, mailFacade, worldGateway.registry());
        this.worldBoss = facades.progression().worldBoss();
        this.partyFinder = new PartyFinderCommands(
                facades.contentFacades().socialFacades().partyFacade(),
                characterFacade,
                worldGateway.registry().partyFinder);
        this.relations = RelationshipCommands.of(facades, characterFacade, worldGateway);
        this.daily = DailyCommands.of(facades, worldGateway);
        this.adventure = AdventureCommands.of(facades, worldGateway);
        this.authExecutor = authExecutor;
        this.chatExecutor = new SerialExecutor(authExecutor);
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
                closeIfTooManyProtocolViolations(ctx);
                return;
            }
            handler.handle(ctx, header, frame.payload());
        } catch (InvalidProtocolBufferException e) {
            // Fail fast ở MỨC PACKET (Điều 16) — không làm sập kết nối vì 1 payload hỏng. KHÔNG kèm
            // stack trace (2026-10-11): payload do client gửi, mỗi gói hỏng là ~40 dòng log, và chỗ
            // ném luôn là parser protobuf — không có gì để lần.
            LOG.warn(
                    "Payload protobuf không hợp lệ: opcode={}, sequenceId={}, lỗi={}",
                    header.opcode(),
                    header.sequenceId(),
                    e.getClass().getSimpleName());
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            sendError(ctx, header.sequenceId(), 400, "error.invalid_payload");
            closeIfTooManyProtocolViolations(ctx);
        } finally {
            span.end();
        }
    }

    /**
     * Đếm một gói vi phạm giao thức (opcode lạ, payload không parse được) và đóng kết nối khi vượt
     * {@link #MAX_PROTOCOL_VIOLATIONS}. Gọi SAU khi đã gửi {@code ErrorResponse} của gói đó.
     */
    // FutureReturnValueIgnored: ChannelFuture của close() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void closeIfTooManyProtocolViolations(ChannelHandlerContext ctx) {
        int violations = protocolViolations.incrementAndGet();
        if (violations > MAX_PROTOCOL_VIOLATIONS) {
            LOG.warn(
                    "Đóng kết nối {} sau {} gói vi phạm giao thức",
                    ctx.channel().remoteAddress(),
                    violations);
            ctx.close();
        }
    }

    /** {@code true} nếu kết nối này đã vượt {@link #MAX_PROTOCOL_VIOLATIONS} — tức đã (hoặc đang) bị đóng vì thế. */
    boolean exceededProtocolViolationLimit() {
        return protocolViolations.get() > MAX_PROTOCOL_VIOLATIONS;
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
        // Quyết định chủ dự án 2026-10-11: client và server phải cùng phiên bản giao thức. Trước đó
        // byte phiên bản trong header không ai đọc — client lệch phiên bản vẫn vào được rồi chạy sai âm
        // thầm ở gói đầu tiên hai bên hiểu khác nhau. Kiểm ở ConnectRequest: gói đầu tiên của mọi phiên.
        if (header.protocolVersion() != ProtocolVersion.CURRENT) {
            LOG.warn(
                    "Từ chối đăng nhập: phiên bản giao thức client={} khác server={}",
                    header.protocolVersion(),
                    ProtocolVersion.CURRENT);
            sendError(ctx, sequenceId, 426, "error.client_outdated");
            return;
        }
        // Quyết định chủ dự án 2026-10-11: kết nối ĐANG Ở WORLD không được đăng nhập lần nữa. Trước đó
        // lệnh này ghi đè boundAccountId sang tài khoản mới trong khi kết nối vẫn điều khiển nhân vật
        // của tài khoản cũ — hai nửa của một phiên thuộc hai tài khoản khác nhau.
        if (boundCharacterId != null) {
            sendError(ctx, sequenceId, 409, "error.already_in_world");
            return;
        }
        // Trần đăng nhập theo IP (dòng 71(a), 2026-10-17): đăng nhập tự tạo tài khoản, nên đây là trần
        // duy nhất mà một username mới không làm trắng lại — xem IpAdmission.
        if (!worldGateway.registry().ipAdmission().tryLogin(ctx.channel().remoteAddress())) {
            LOG.warn(
                    "Từ chối đăng nhập: IP {} vượt trần đăng nhập mỗi phút",
                    ctx.channel().remoteAddress());
            sendError(ctx, sequenceId, 429, "error.too_many_logins");
            return;
        }

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
            pinGate.reset(expansion.securityPin().hasPin(result.accountId()));
            // Đăng nhập lại trên cùng kết nối (chưa vào world): phiên trước không còn ai dùng — thu hồi.
            String previousToken = sessionToken;
            sessionToken = result.sessionToken();
            if (!result.sessionToken().equals(previousToken)) {
                accountFacade.logout(previousToken);
            }
            ConnectResponse response = ConnectResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionId(result.sessionToken())
                    .setAccountId(result.accountId())
                    .build();
            send(ctx, Opcode.OPCODE_CONNECT_RESPONSE_VALUE, sequenceId, response.toByteArray());
            networkMetrics.recordLoginSuccess();
        } catch (dev.kage.account.domain.AccountBlockedException e) {
            // Tài khoản bị khoá (README dòng 68(e)) — một dòng INFO, không stack trace: đây là luật chạy
            // đúng, không phải sự cố.
            networkMetrics.recordLoginFailed();
            LOG.info("Đăng nhập bị từ chối: {}", e.getMessage());
            sendError(ctx, sequenceId, 403, "error.account_banned");
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
        ListCharactersRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
            ListCharactersResponse response = ListCharactersResponse.newBuilder()
                    .addAllCharacters(characterSummaries(accountId))
                    .build();
            send(ctx, Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (RuntimeException e) {
            LOG.error("Liệt kê nhân vật thất bại", e);
            sendError(ctx, sequenceId, 500, "error.list_characters_failed");
        }
    }

    /** ADR-205 — yêu cầu xoá nhân vật (màn chọn nhân vật). accountId từ session; không xoá nhân vật đang chơi. */
    private void handleDeleteCharacter(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        dev.kage.protocol.DeleteCharacterRequest request = dev.kage.protocol.DeleteCharacterRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        Long playing = boundCharacterId;
        executeBlocking(
                ctx,
                sequenceId,
                () -> send(
                        ctx,
                        Opcode.OPCODE_DELETE_CHARACTER_RESPONSE_VALUE,
                        sequenceId,
                        expansion.lot34().deleteCharacter(accountId, playing, request.getCharacterId()).toBuilder()
                                .addAllCharacters(characterSummaries(accountId))
                                .build()
                                .toByteArray()));
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, request.getIdempotencyKey())) {
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
        executeBlocking(ctx, sequenceId, () -> selectCharacterSerialized(ctx, sequenceId, accountId, characterId));
    }

    /** Một lượt chọn nhân vật mỗi lúc trên kết nối này — xem {@link #selectLock}. */
    private void selectCharacterSerialized(
            ChannelHandlerContext ctx, int sequenceId, long accountId, long characterId) {
        selectLock.lock();
        try {
            handleSelectCharacterBlocking(ctx, sequenceId, accountId, characterId);
        } finally {
            selectLock.unlock();
        }
    }

    // PMD AvoidCatchingGenericException: ranh giới I/O bất đồng bộ, phải trả lời dù lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void handleSelectCharacterBlocking(
            ChannelHandlerContext ctx, int sequenceId, long accountId, long characterId) {
        try {
            Character character = characterFacade.selectCharacter(accountId, characterId);
            // ADR-205 — đang chờ xoá ⇒ vào lại là khôi phục; quá hạn ⇒ như không tồn tại.
            if (expansion.lot34().facades().deletion().restoreOnSelect(accountId, characterId, java.time.Instant.now())
                    == dev.kage.character.domain.CharacterDeletionPort.State.EXPIRED) {
                throw new CharacterNotFoundException(characterId);
            }
            // Kiểm quyền sở hữu XONG mới rời nhân vật cũ — chọn nhầm id thì vẫn giữ nhân vật đang chơi.
            releasePreviousCharacterIfSwitching(ctx, characterId);
            if (!enterWorldAsOnlySession(ctx, sequenceId, character)) {
                return;
            }
            sendInitialSelfState(characterId);
            announceOnlineToFriends(characterId);
            claimDailyCheckIn(characterId);
            // ADR-062 D4 — mở bù mốc đã đạt + nạp danh hiệu cho chat; việc phụ, lỗi dừng bên trong.
            worldGateway.registry().achievements.onEnteredWorld(characterId, character.level());
            // ADR-095/096 — quỹ bế quan + giờ vào world; việc phụ, lỗi dừng bên trong.
            daily.onEnteredWorld(characterId);
            // ADR-164 — x2 EXP còn hạn thì báo HUD; việc phụ, lỗi dừng bên trong.
            expansion.lot29().expBuff().announce(characterId);
            // ADR-103 — thông báo server (RAM, không chạm DB).
            serviceDesk.sendNoticeOnEnter(characterId);
            // ADR-187 — tin nhắn riêng gửi lúc mình offline; việc phụ, lỗi dừng bên trong.
            expansion.lot32().offlineWhispers().deliverOnEnter(characterId);
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
     * Báo cho bạn bè đang online rằng nhân vật vừa vào world. Việc PHỤ của lần vào world: lúc này
     * {@code SelectCharacterResponse(success)} ĐÃ gửi, nên lỗi ở đây (đọc bảng {@code friendship}
     * hỏng) không được nổi lên thành {@code error.select_character_failed} — người chơi đang đứng
     * trong world mà nhận câu "chọn nhân vật thất bại" cho cùng {@code sequenceId} (sửa 2026-10-10,
     * {@code FriendPresenceFailureTest}). Hậu quả của việc nuốt: bạn bè thấy người này offline cho
     * tới lần họ mở lại danh sách bạn — {@code ListFriendsResponse} luôn tra trạng thái thật.
     *
     * <p>Đang trên authExecutor (Virtual Thread) rồi — friendFacade.listFriends chạm DB, an toàn gọi
     * thẳng, không cần đẩy thêm 1 lớp executor nữa (khác channelInactive bên dưới).
     */
    // PMD AvoidCatchingGenericException: đúng ý — mọi lỗi của việc phụ này đều dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void announceOnlineToFriends(long characterId) {
        try {
            notifyFriendsPresenceChanged(characterId, true);
            correctStaleOnlineAnnouncement(characterId);
        } catch (RuntimeException e) {
            LOG.error("Không báo được trạng thái online cho bạn bè: characterId={}", characterId, e);
        }
    }

    /**
     * Điểm danh hôm nay (ADR-060) — việc PHỤ của lần vào world, cùng luật với {@link
     * #announceOnlineToFriends}: lỗi dừng ở đây, không thành "chọn nhân vật thất bại". Quà vừa gửi thì
     * báo thư mới như mọi thư khác; lỗi thì lần vào world sau thử lại (trạng thái chưa đổi).
     */
    // PMD AvoidCatchingGenericException: đúng ý — mọi lỗi của việc phụ này đều dừng ở đây.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void claimDailyCheckIn(long characterId) {
        try {
            if (checkInFacade.claimToday(characterId)
                            instanceof dev.kage.checkin.application.ClaimDailyCheckInUseCase.Result.Claimed claimed
                    && claimed.mailId() != 0L) {
                worldGateway
                        .registry()
                        .sendToCharacterIfOnline(
                                characterId,
                                Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                                0,
                                NewMailEvent.newBuilder()
                                        .setMailId(claimed.mailId())
                                        .build()
                                        .toByteArray());
                worldGateway.registry().achievements.onCheckedIn(characterId);
                worldGateway.registry().activity.record(characterId, ActivityFacade.Activity.CHECK_IN);
            }
        } catch (RuntimeException e) {
            LOG.error("Điểm danh lỗi: characterId={}", characterId, e);
        }
    }

    /**
     * Vào world với tư cách phiên DUY NHẤT của nhân vật (quyết định chủ dự án 2026-09-25, SERVER.md
     * §8): nhân vật đang online ở kết nối khác thì kết nối đó bị ĐÁ — báo lý do, dọn y như ngắt kết
     * nối thường, đóng — rồi mới tới lượt kết nối này.
     *
     * <p><b>Thứ tự là cả điểm mấu chốt.</b> Cả đoạn chạy dưới khoá của nhân vật, nên hai lượt chọn
     * không đan xen. Và kết nối này chỉ ĐỌC vị trí (trong {@link #enterWorld}) SAU khi phiên cũ đã dọn
     * xong trọn vẹn: vị trí lúc thoát đã ghi xuống DB (không thì đăng nhập lại bị lùi về chỗ lưu lần
     * trước), world đã xử lý hết lệnh cũ (không thì gói xác nhận di chuyển muộn của phiên cũ rơi vào
     * kết nối mới, và hai khu vực khác nhau có thể cùng giữ thực thể trong một tick).
     *
     * <p>Chọn lại đúng nhân vật đang chơi trên CÙNG kết nối (bấm hai lần) không tự đá mình — sổ trả
     * rỗng, còn {@code register}/{@code attachCharacter} vốn đã chịu được gọi lặp.
     *
     * @return {@code false} nếu kết nối này chết giữa chừng và nhân vật đã được dọn
     */
    private boolean enterWorldAsOnlySession(ChannelHandlerContext ctx, int sequenceId, Character character) {
        long characterId = character.id();
        CharacterSessionRegistry sessions = worldGateway.registry().sessions();
        // Thứ tự khoá cố định: tài khoản → nhân vật (xem CharacterSessionRegistry#lockForAccount).
        ReentrantLock accountLock = sessions.lockForAccount(character.accountId());
        accountLock.lock();
        try {
            ReentrantLock lock = sessions.lockFor(characterId);
            lock.lock();
            try {
                return claimAndEnterWorld(ctx, sequenceId, character, sessions);
            } finally {
                lock.unlock();
            }
        } finally {
            accountLock.unlock();
        }
    }

    /** Thân của {@link #enterWorldAsOnlySession} — chỉ gọi khi ĐANG giữ khoá của nhân vật. */
    private boolean claimAndEnterWorld(
            ChannelHandlerContext ctx, int sequenceId, Character character, CharacterSessionRegistry sessions) {
        long characterId = character.id();
        boolean entered = false;
        try {
            sessions.claim(characterId, session).ifPresent(previous -> awaitEviction(previous, characterId));
            // Một tài khoản chỉ MỘT nhân vật online (quyết định chủ dự án 2026-10-11): nhân vật KHÁC của
            // tài khoản này đang chơi ở kết nối khác thì kết nối đó bị đá, cùng cách và cùng câu báo với
            // việc chọn lại chính nhân vật đó.
            sessions.claimAccount(character.accountId(), characterId, session)
                    .ifPresent(other -> awaitEviction(other.session(), other.characterId()));
            entered = enterWorld(ctx, sequenceId, character);
            return entered;
        } finally {
            Long bound = boundCharacterId;
            if (!entered && (bound == null || bound != characterId)) {
                // Hỏng trước khi vào được world: trả quyền giữ phiên, không thì lượt sau sẽ đi "đá" một
                // kết nối chưa từng giữ nhân vật này.
                sessions.release(characterId, session);
            }
        }
    }

    /** Chờ phiên cũ dọn xong — có trần, xem {@link #EVICTION_TIMEOUT}. */
    private static void awaitEviction(CharacterSessionRegistry.Session previous, long characterId) {
        awaitRelease(previous.evict(characterId), characterId);
    }

    /**
     * Kết nối này đang gắn nhân vật KHÁC {@code nextCharacterId} (cùng account) thì rời nhân vật đó
     * TRƯỚC — dọn y như ngắt kết nối thường: ghi vị trí, rời world sau mốc chặn hàng đợi, báo bạn bè
     * offline, thôi đăng ký + thôi giữ phiên (có điều kiện). Client hiện tại không bao giờ làm vậy,
     * nhưng server không tin client: bản cũ ghi đè {@code boundCharacterId} và để nhân vật cũ đứng
     * mãi trong world (SERVER.md §5.1).
     *
     * <p>Chạy dưới khoá của nhân vật CŨ (nhả trước khi lấy khoá nhân vật mới) nên một kết nối khác
     * đang giành đúng nhân vật đó hoặc chạy trước — đá luôn kết nối này, cũng dọn đủ — hoặc chạy sau
     * và thấy nhân vật đã không còn ai giữ. Chọn lại ĐÚNG nhân vật đang chơi thì không làm gì (vẫn
     * idempotent như trước).
     */
    private void releasePreviousCharacterIfSwitching(ChannelHandlerContext ctx, long nextCharacterId) {
        CharacterBinding previous = binding;
        if (previous == null || previous.characterId() == nextCharacterId) {
            return;
        }
        long previousId = previous.characterId();
        ReentrantLock lock = worldGateway.registry().sessions().lockFor(previousId);
        lock.lock();
        try {
            LOG.info("Kết nối đổi nhân vật — rời nhân vật cũ trước: {} -> {}", previousId, nextCharacterId);
            // Trên EventLoop của kết nối này, cùng lý do evictCharacter: không lệnh gameplay nào của
            // nhân vật cũ còn lọt vào world sau lệnh detach.
            runOnEventLoop(ctx, () -> unbindAndRelease(ctx, previous));
            awaitRelease(previous.completed(), previousId);
        } finally {
            lock.unlock();
        }
    }

    /** Chạy {@code task} trên EventLoop của kết nối; EventLoop đã tắt (server đang dừng) thì chạy tại chỗ. */
    private static void runOnEventLoop(ChannelHandlerContext ctx, Runnable task) {
        try {
            ctx.executor().execute(task);
        } catch (RejectedExecutionException e) {
            LOG.warn("EventLoop đã dừng — dọn nhân vật tại chỗ", e);
            task.run();
        }
    }

    /** Chờ một lần dọn chạy trọn — có trần, xem {@link #EVICTION_TIMEOUT}. */
    private static void awaitRelease(CompletableFuture<Void> released, long characterId) {
        try {
            released.get(EVICTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn(
                    "Phiên cũ chưa dọn xong sau {} — vẫn cho phiên mới vào: characterId={}",
                    EVICTION_TIMEOUT,
                    characterId,
                    e);
        } catch (ExecutionException e) {
            LOG.warn("Dọn phiên cũ lỗi — vẫn cho phiên mới vào: characterId={}", characterId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ dọn phiên cũ: characterId=" + characterId, e);
        }
    }

    /** Phần "vào world" của chọn nhân vật — gọi dưới khoá của nhân vật, xem {@link #enterWorldAsOnlySession}. */
    private boolean enterWorld(ChannelHandlerContext ctx, int sequenceId, Character character) {
        long characterId = character.id();
        // THỨ TỰ (chủ dự án chốt 2026-10-14, docs/README.md dòng 73(c)): đọc DB → đăng ký + attach + gắn
        // → RỒI MỚI gửi response. Response vì thế là mốc "sẵn sàng" thật: lệnh đầu tiên client gửi ngay
        // sau nó đã có World để đi tới (trước đó nhận error.not_authenticated, đo được thật). Gói world
        // (spawn, chat) có thể tới TRƯỚC response — client GIỮ LẠI chúng cho màn world (HeldFrames ở
        // client-core); bản 2026-09-24 đặt response lên trước chính vì client hồi đó vứt các gói này.
        PositionSnapshot spawn = characterFacade.loadPosition(characterId);
        CharacterStats stats = characterFacade.loadStats(characterId);
        // Phase 9 lát cắt 6: định tuyến vào khu vực (channel) còn chỗ thay vì thẳng 1 World
        // duy nhất — MainWorldChannelRegistry tự mở thêm khu vực nếu cần (giới hạn người/khu
        // vực chặn AoI phình theo TỔNG N toàn map, xem javadoc class đó).
        // GAMEPLAY.md §2 "tính lại chỉ số dẫn xuất khi đổi trang bị" — đọc TRƯỚC khi attach và
        // gửi CÙNG một lệnh world. Tách làm hai lệnh (attach rồi applyEquipmentBonus) để lại một
        // tick mà entity đã ở trong world với chỉ số NỀN: đo thật 2026-09-18, đăng nhập cạnh quái
        // ăn 37 sát thương trên thanh máu 100 thay vì 27 trên thanh 206.
        CharacterBaseStats loadoutBase = CharacterLoadout.baseStatsOf(character, stats);
        CharacterLoadout loadout = new CharacterLoadout(loadoutBase, currentEquipmentBonus(characterId))
                .withSkillRanks(worldGateway.registry().skillRanks.snapshotFor(characterId, loadoutBase.element()));
        SelectCharacterResponse response = enterWorldResponse(loadout, spawn);
        if (!isLiveOnThisConnection(characterId)) {
            // Đăng ký connection NÀY để world outbound (spawn/move/despawn — SERVER.md §6) tìm
            // đúng channel; attachCharacter chỉ enqueue, entity ECS thật tạo ở tick kế tiếp. Hai bước
            // trong một khoá — lượt kéo vào hầm ngục/đấu trường không chen được vào giữa (dòng 73(f)).
            CharacterAppearance appearance =
                    CharacterAppearance.of(toProtoFaction(character.faction()), character.level());
            worldGateway
                    .mainWorldChannels()
                    .registerAndAttach(
                            () -> worldGateway.registry().register(characterId, ctx, appearance),
                            loadout.entryAt(characterId, spawn));
        }
        boolean bound = bindOrReleaseIfClosed(ctx, characterId);
        if (bound) {
            send(ctx, Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, sequenceId, response.toByteArray());
        }
        return bound;
    }

    /**
     * Kết nối này đang chơi ĐÚNG {@code characterId} và chưa dọn nó — tức lượt chọn này là chọn LẠI
     * nhân vật đang ở trong world (bấm hai lần). Khi đó nhân vật đã thuộc đúng một World, không được
     * gắn thêm: nếu nó đang ở hầm ngục/Arena thì world chính KHÔNG biết nó, nên gắn vào world chính sẽ
     * đẻ thực thể thứ hai — và lần dọn lúc ngắt kết nối chỉ dọn hầm ngục/trận, để thực thể đó đứng mãi
     * ({@code CharacterSwitchOnSameConnectionTest}, 2026-10-02).
     */
    private boolean isLiveOnThisConnection(long characterId) {
        CharacterBinding current = binding;
        return current != null
                && current.characterId() == characterId
                && !current.released().get();
    }

    /**
     * Gán {@code boundCharacterId} SAU khi đăng ký + attach, rồi dọn ngay nếu kết nối đã chết.
     *
     * <p><b>Vì sao gán SAU attach</b>: lệnh gameplay chạy trên EventLoop và gọi {@code currentWorldFacade},
     * vốn NÉM nếu nhân vật chưa thuộc World nào — tức {@code exceptionCaught} đóng luôn kết nối. Gán sau
     * khi attach đã định tuyến xong thì lệnh nào thấy nhân vật cũng thấy World của nó. Từ 2026-10-14
     * response đi SAU bước này (README dòng 73(c)), nên client không còn khe gửi lệnh mà nhận
     * {@code error.not_authenticated}.
     *
     * <p><b>Vì sao phải tự dọn</b>: kết nối chết TRƯỚC khi gán thì {@code channelInactive} thấy
     * {@code boundCharacterId == null} và không dọn gì — bản cũ còn tệ hơn: nó dọn TRƯỚC khi attach
     * rồi handler attach tiếp, để lại một thực thể "bóng ma" đứng mãi trong world. Nay kiểm lại sau
     * khi gán; {@link #releaseCharacter} chỉ chạy đúng một lần dù hai phía cùng thấy.
     *
     * @return {@code false} nếu kết nối đã chết và nhân vật vừa được dọn
     */
    private boolean bindOrReleaseIfClosed(ChannelHandlerContext ctx, long characterId) {
        // Mỗi lần gắn một CharacterBinding MỚI (cờ dọn riêng). Chọn lại đúng nhân vật đang gắn thì bản
        // cũ bị thay khi chưa dọn — đúng, vì cùng một nhân vật chỉ cần dọn một lần, qua bản mới.
        CharacterBinding bound = new CharacterBinding(characterId);
        boundContext = ctx;
        binding = bound;
        boundCharacterId = characterId;
        if (ctx.channel().isActive()) {
            return true;
        }
        releaseCharacter(ctx, bound);
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
    private static SelectCharacterResponse enterWorldResponse(CharacterLoadout loadout, PositionSnapshot spawn) {
        int maxHp = loadout.maxHp();
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
     *
     * <p><b>Nhân vật không còn ở World nào thì bỏ qua bước World (sửa 2026-10-10).</b> Lệnh này chạy
     * trên Virtual Thread, SAU lời gọi DB: kết nối rớt trong lúc đó thì lần dọn đã gỡ nhân vật khỏi
     * world trước khi ta tới đây. Bản cũ dùng {@code currentWorldFacade} (ném) — mặc đồ đã ghi DB xong
     * mà vẫn thành một dòng ERROR "Handler lỗi ngoài dự kiến" kèm stack trace cho mỗi lần rớt mạng
     * trúng lúc. Không có entity thì không có chỉ số nào để tính lại; lần vào world sau đọc trang bị
     * từ DB. Cùng cách {@code handleUseItemBlocking} đã làm cho đan dược.
     */
    private int applyEquipmentBonusAndComputeMaxHp(long characterId) {
        EquipmentBonusSnapshot bonus = currentEquipmentBonus(characterId);
        currentWorldFacadeIfPresent(characterId).ifPresent(facade -> facade.applyEquipmentBonus(characterId, bonus));
        sendMoveSpeed(characterId, bonus.moveSpeedPercent());
        // Máu tối đa nay theo cấp (dòng 65(d)) — đọc cấp đã ghi DB; đã ở Virtual Thread sau lời gọi DB.
        int level = characterFacade.findById(characterId).map(Character::level).orElse(1);
        return WorldFacade.characterMaxHp(level, bonus);
    }

    /** ADR-070 D4 — tốc độ phi kiếm cho dự đoán di chuyển của client. */
    private void sendMoveSpeed(long characterId, int moveSpeedPercent) {
        worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_MOVE_SPEED_CHANGED_EVENT_VALUE,
                        0,
                        MoveSpeedChangedEvent.newBuilder()
                                .setMoveSpeedPercent(moveSpeedPercent)
                                .build()
                                .toByteArray());
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
        // Cùng đường với attach — kèm tâm pháp bị động (ADR-077).
        return worldGateway.registry().equipmentBonusOf(characterId);
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
        sendMoveSpeed(characterId, inventoryFacade.moveSpeedPercent(characterId));
        // ADR-067 — sự kiện nhân đôi đang chạy (nếu có) để client hiện banner ngay từ đầu.
        List<dev.kage.gm.domain.GameEvent> events = gameEvents.active();
        if (!events.isEmpty()) {
            worldGateway
                    .registry()
                    .sendToCharacterIfOnline(
                            characterId,
                            Opcode.OPCODE_GAME_EVENTS_CHANGED_EVENT_VALUE,
                            0,
                            GameEventNotices.of(events).toByteArray());
        }
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
            CharacterAppearance changed = CharacterAppearance.of(request.getFaction(), levelOf(characterId));
            worldGateway.registry().updateAppearance(characterId, changed);
            // ADR-045 — người ĐANG nhìn thấy nhân vật này (và chính họ) mới biết model đã đổi. Phải
            // đi qua hàng đợi world vì AoI chỉ đọc được trên tick thread. Chưa vào World nào thì
            // không có ai đang nhìn — bỏ qua, lần vào world sau CharacterSpawnedEvent đã mang hệ
            // phái mới nhờ updateAppearance ở dòng trên.
            // Quyết định chủ dự án 2026-10-11: phần CHIẾN ĐẤU của hệ mới (nguyên tố, passive, chiêu được
            // phép) cũng có hiệu lực ngay, không chờ đăng nhập lại — trước lệnh ngoại hình, để không tick
            // nào người khác thấy hệ mới mà world còn tính hệ cũ.
            dev.kage.server.world.Element element = FactionMapping.toElement(toDomainFaction(request.getFaction()));
            SkillRankSnapshot newRanks = worldGateway.registry().skillRanks.snapshotFor(characterId, element);
            // Tâm pháp (ADR-077) chỉ tính trên linh căn hiện tại — DB đã ghi hệ mới ở trên.
            EquipmentBonusSnapshot newBonus = currentEquipmentBonus(characterId);
            currentWorldFacadeIfPresent(characterId).ifPresent(facade -> {
                facade.enqueueCharacterElement(characterId, element);
                facade.enqueueSkillRanks(characterId, newRanks);
                facade.applyEquipmentBonus(characterId, newBonus);
                facade.enqueueAppearanceChanged(characterId);
            });
            SelectFactionResponse response =
                    SelectFactionResponse.newBuilder().setSuccess(true).build();
            send(ctx, Opcode.OPCODE_SELECT_FACTION_RESPONSE_VALUE, sequenceId, response.toByteArray());
        } catch (CharacterNotFoundException e) {
            LOG.warn("Chọn hệ phái thất bại: không tìm thấy nhân vật", e);
            sendError(ctx, sequenceId, 404, "error.character_not_found");
        } catch (FactionSelectionLevelTooLowException e) {
            LOG.warn("Chọn hệ phái thất bại: chưa đủ cấp", e);
            sendError(ctx, sequenceId, 400, "error.level_too_low");
        } catch (FactionUnchangedException e) {
            sendError(ctx, sequenceId, 400, "error.faction_unchanged");
        } catch (FactionChangePillRequiredException e) {
            sendError(ctx, sequenceId, 400, "error.faction_change_pill_required");
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
        return worldRoutes.find(characterId);
    }

    // MoveCommand KHÔNG chạm DB/Redis — chỉ enqueue vào server-world (rẻ, thread-safe), nên xử
    // lý thẳng trên EventLoop, không cần authExecutor (SERVER.md §2: "EventLoop được: decode,
    // validate rẻ, enqueue").
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
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
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void handleJumpCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        JumpCommand.parser().parseFrom(payload); // message rỗng — parse chỉ để validate wire format.
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
    // "đánh 1 nhát". Tần suất thật thấp, nhưng vẫn qua trần chiến đấu chung (tryAcquireCombatCommand).
    private void handleSetTargetCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SetTargetCommand command = SetTargetCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        if (!tryAcquireCombatCommand(ctx, characterId)) {
            return;
        }
        currentWorldFacade(characterId).setTarget(characterId, command.getTargetEntityId());
    }

    /**
     * Trần chung cho SetTarget/Attack/UseSkill — xem {@link #combatCommandRateLimiter}. Vượt một cửa
     * sổ ⇒ bỏ IM LẶNG như MoveCommand (ba lệnh này không có response nào để báo); vượt liên tiếp ⇒
     * ngắt kết nối.
     *
     * @return {@code false} nếu caller phải bỏ lệnh
     */
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private boolean tryAcquireCombatCommand(ChannelHandlerContext ctx, long characterId) {
        if (combatCommandRateLimiter.tryAcquire()) {
            return true;
        }
        networkMetrics.recordRateLimitRejected("combat");
        if (combatCommandRateLimiter.shouldDisconnect()) {
            LOG.warn("Lệnh chiến đấu vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
            ctx.close();
        }
        return false;
    }

    // AttackCommand KHÔNG chạm DB — chỉ enqueue vào server-world, xử lý thẳng trên EventLoop giống
    // handleSetTargetCommand. Cooldown đánh thường (CombatSystem, 1 đòn/giây) chặn tần suất GAMEPLAY,
    // nhưng không chặn chi phí mỗi gói trên tick thread — nên vẫn qua tryAcquireCombatCommand.
    private void handleAttackCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AttackCommand.parser().parseFrom(payload); // message rỗng — parse chỉ để validate wire format.
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        if (!tryAcquireCombatCommand(ctx, characterId)) {
            return;
        }
        currentWorldFacade(characterId).attack(characterId);
    }

    // UseSkillCommand KHÔNG chạm DB — chỉ enqueue vào server-world, xử lý thẳng trên EventLoop
    // giống handleSetTargetCommand. Cooldown skill (SkillSystem) giới hạn tần suất GAMEPLAY; trần
    // chi phí gói là tryAcquireCombatCommand, cùng lý do AttackCommand.
    private void handleUseSkillCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UseSkillCommand command = UseSkillCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        if (!tryAcquireCombatCommand(ctx, characterId)) {
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
                .setErrorKey(unequipErrorKey(result))
                .setMaxHp(maxHp)
                .build();
        send(ctx, Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE, sequenceId, response.toByteArray());
    }

    private static String unequipErrorKey(InventoryFacade.UnequipResult result) {
        return switch (result) {
            case OK -> "";
            case NOT_EQUIPPED -> "error.slot_not_equipped";
            // Đồ đang mặc không chiếm ô túi (2026-10-11), nên gỡ ra cần một ô trống.
            case INVENTORY_FULL -> "error.inventory_full";
        };
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())) {
            return;
        }
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
            if (result == InventoryFacade.EnhanceResult.ITEM_IN_TRADE) {
                // Quyết định chủ dự án 2026-09-29: món đang chào trong giao dịch không được cường
                // hoá. KHÔNG thêm giá trị EnhanceOutcome (đổi protocol) — đi đường ErrorResponse +
                // error_key như mọi từ chối nghiệp vụ khác; client khớp theo sequenceId.
                sendError(ctx, request.sequenceId(), 409, "error.item_in_trade");
                return;
            }
            if (result == InventoryFacade.EnhanceResult.NOT_EQUIPMENT) {
                // Quyết định chủ dự án 2026-10-11: chỉ trang bị mới cường hoá được — cùng đường
                // ErrorResponse với nhánh trên, không thêm giá trị EnhanceOutcome.
                sendError(ctx, request.sequenceId(), 409, "error.item_not_enhanceable");
                return;
            }
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
        // Hồi chiêu dùng vật phẩm (quyết định chủ dự án 2026-10-11) — chiếm NGAY ở đây, trên EventLoop,
        // trước khi lệnh sang luồng chạm DB: xem ItemUseCooldown.
        long cooldownTicket = itemUseCooldown.tryBegin();
        if (cooldownTicket == ItemUseCooldown.ON_COOLDOWN) {
            UseItemResponse onCooldown = UseItemResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorKey("error.item_on_cooldown")
                    .build();
            send(ctx, Opcode.OPCODE_USE_ITEM_RESPONSE_VALUE, sequenceId, onCooldown.toByteArray());
            return;
        }
        executeBlocking(
                ctx,
                sequenceId,
                () -> handleUseItemBlocking(
                        ctx, sequenceId, new UseItemRequest(characterId, inventoryItemId, cooldownTicket)));
    }

    /** Một lệnh dùng vật phẩm đã qua hồi chiêu — {@code cooldownTicket} để hoàn lại nếu lệnh thất bại. */
    private record UseItemRequest(long characterId, long inventoryItemId, long cooldownTicket) {}

    /**
     * Hai nửa của một lần uống đan: {@code feature-inventory} trừ món (transaction + audit) rồi trả
     * về hiệu ứng, {@code server-world} cộng máu. Gateway là chỗ DUY NHẤT nối được hai bên — cùng
     * hình dạng {@code onMonsterKilled} → {@code grantLoot}.
     *
     * <p>Máu mới KHÔNG đi kèm response: hàng đợi world là bất đồng bộ nên lúc này lệnh còn chưa
     * chạy (bẫy đã trả giá ở ADR-043). Client nhận số mới qua {@code HealthChangedEvent} (408).
     */
    private void handleUseItemBlocking(ChannelHandlerContext ctx, int sequenceId, UseItemRequest request) {
        long characterId = request.characterId();
        UseItemUseCase.UseItemResult result = inventoryFacade.useItem(characterId, request.inventoryItemId());
        if (result.outcome() != UseItemUseCase.Outcome.OK) {
            // Không có món / món không dùng được: chưa uống gì, không phạt hồi chiêu.
            itemUseCooldown.cancel(request.cooldownTicket());
        }
        if (result.outcome() == UseItemUseCase.Outcome.OK && result.effect() == ItemUseEffect.HEAL_HP) {
            // RỖNG nếu nhân vật vừa rớt mạng giữa chừng — món đã trừ rồi, nhưng không có entity
            // nào để cộng máu và cũng không còn ai để gửi gói. Không phải lỗi, đừng ném.
            currentWorldFacadeIfPresent(characterId)
                    .ifPresent(facade -> facade.enqueueHealCharacter(characterId, result.value()));
        }
        if (result.outcome() == UseItemUseCase.Outcome.OK && result.effect() == ItemUseEffect.DOUBLE_EXP) {
            expansion.lot29().expBuff().announce(characterId); // ADR-164 — đọc lại hạn + báo HUD
        }
        if (result.outcome() == UseItemUseCase.Outcome.OK
                && (result.effect() == ItemUseEffect.EVOLVE_PET
                        || result.effect() == ItemUseEffect.FEED_PET
                        || result.effect() == ItemUseEffect.UPGRADE_MOUNT)) { // ADR-203 — gửi lại tốc độ mới
            // ADR-078 — chỉ số linh thú vừa nhân bậc 2: tính lại như sau khi mặc đồ.
            applyEquipmentBonusAndComputeMaxHp(characterId);
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
            case EVOLVE_PET -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_EVOLVE_PET;
            case EXPAND_BAG -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_EXPAND_BAG;
            case DOUBLE_EXP -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_DOUBLE_EXP;
            case FEED_PET -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_FEED_PET;
            case UPGRADE_MOUNT -> dev.kage.protocol.ItemUseEffect.ITEM_USE_EFFECT_UPGRADE_MOUNT;
        };
    }

    private static String useItemErrorKey(UseItemUseCase.Outcome outcome) {
        return switch (outcome) {
            case OK -> "";
            case ITEM_NOT_FOUND -> "error.item_not_found";
            case NOT_USABLE -> "error.item_not_usable";
            case ITEM_IN_TRADE -> "error.item_offered_in_trade";
            case NO_PET_EQUIPPED -> "error.no_pet_equipped";
            case PET_LEVEL_TOO_LOW -> "error.pet_level_too_low";
            case PET_ALREADY_EVOLVED -> "error.pet_already_evolved";
            case BAG_AT_MAX -> "error.bag_at_max";
            case EXP_BUFF_AT_MAX -> "error.exp_buff_at_max";
            case PET_AT_MAX_LEVEL -> "error.pet_at_max_level"; // ADR-201
            case NO_MOUNT_EQUIPPED -> "error.no_mount_equipped"; // ADR-203
            case MOUNT_AT_MAX_LEVEL -> "error.mount_at_max_level";
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

    /** ADR-061 — liệt kê kho đồ. Mở bằng phím không có; client hỏi lại sau khi đã mở qua hội thoại. */
    private void handleListStorageRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListStorageRequest request = ListStorageRequest.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_LIST_STORAGE_RESPONSE_VALUE,
                characterId -> storage.list(characterId, request));
    }

    private void handleDepositItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        DepositItemCommand command = DepositItemCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_DEPOSIT_ITEM_RESPONSE_VALUE,
                characterId -> storage.deposit(characterId, command));
    }

    /** ADR-079 — danh sách công thức lò đan (xem được ở bất cứ đâu; LUYỆN mới cần đứng gần lò). */
    private void handleListRecipesRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListRecipesRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_LIST_RECIPES_RESPONSE_VALUE, characterId -> crafting.list());
    }

    /** ADR-080 — cửa hàng bang (không cần đứng gần ai: hàng của bang, không phải của một nơi). */
    private void handleListGuildShopRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListGuildShopRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_LIST_GUILD_SHOP_RESPONSE_VALUE, guildShop::list);
    }

    private void handleBuyGuildShopItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        BuyGuildShopItemCommand command = BuyGuildShopItemCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_BUY_GUILD_SHOP_ITEM_RESPONSE_VALUE,
                characterId -> guildShop.buy(characterId, command));
    }

    /** ADR-082 — bảng tìm nhóm (RAM). */
    private void handleListPartyFinderRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListPartyFinderRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_LIST_PARTY_FINDER_RESPONSE_VALUE, id -> partyFinder.list());
    }

    private void handlePostPartyFinderCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        PostPartyFinderCommand command = PostPartyFinderCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_POST_PARTY_FINDER_RESPONSE_VALUE,
                characterId -> partyFinder.post(characterId, command));
    }

    private void handleJoinPartyFinderCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        JoinPartyFinderCommand command = JoinPartyFinderCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_JOIN_PARTY_FINDER_RESPONSE_VALUE,
                characterId -> partyFinder.join(characterId, command));
    }

    private void handleRemovePartyFinderCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RemovePartyFinderCommand.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_REMOVE_PARTY_FINDER_RESPONSE_VALUE, partyFinder::remove);
    }

    /**
     * ADR-089 — Độ Kiếp Chuyển Thế. Thành công ⇒ world đặt cấp 1, chỉ số tính lại (kèm Đạo Vận), vạch EXP về 0. Đăng ký
     * lại kết nối (cùng ngoại hình) để mốc "EXP đã gửi" về rỗng — không thì mọi gói EXP sau chuyển thế bị coi là gói
     * đến muộn (tổng EXP nhỏ hơn mốc cũ) và bị bỏ tới lần đăng nhập sau.
     */
    private void handleRebirthCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        RebirthCommand.parser().parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> {
            dev.kage.character.application.RebirthUseCase.Result result = rebirth.rebirth(characterId);
            if (result.outcome() == dev.kage.character.domain.RebirthOutcome.OK) {
                currentWorldFacadeIfPresent(characterId).ifPresent(world -> world.resetCharacterLevel(characterId, 1));
                characterFacade.findById(characterId).ifPresent(character -> worldGateway
                        .registry()
                        .register(
                                characterId,
                                ctx,
                                CharacterAppearance.of(toProtoFaction(character.faction()), character.level())));
                applyEquipmentBonusAndComputeMaxHp(characterId);
                send(
                        ctx,
                        Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE,
                        0,
                        dev.kage.protocol.ExperienceChangedEvent.newBuilder()
                                .setLevel(1)
                                .setTotalExp(0)
                                .setExpIntoLevel(0)
                                .setExpForNextLevel(dev.kage.character.domain.ExperienceCurve.expForNextLevel(1))
                                .build()
                                .toByteArray());
            }
            send(
                    ctx,
                    Opcode.OPCODE_REBIRTH_RESPONSE_VALUE,
                    sequenceId,
                    RebirthResponse.newBuilder()
                            .setOutcome(dev.kage.protocol.RebirthOutcome.valueOf(
                                    "REBIRTH_OUTCOME_" + result.outcome().name()))
                            .setRebirthCount(result.rebirthCount())
                            .build()
                            .toByteArray());
        });
    }

    /** ADR-090 — tình hình Linh Mạch. */
    private void handleGetSpiritVeinStatusRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetSpiritVeinStatusRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_GET_SPIRIT_VEIN_STATUS_RESPONSE_VALUE, spiritVeins::status);
    }

    /** ADR-085 — kỹ năng bang. */
    private void handleListGuildSkillsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListGuildSkillsRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_LIST_GUILD_SKILLS_RESPONSE_VALUE, guildSkills::list);
    }

    private void handleUpgradeGuildSkillCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UpgradeGuildSkillCommand command = UpgradeGuildSkillCommand.parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_UPGRADE_GUILD_SKILL_RESPONSE_VALUE, characterId -> {
            GuildSkillCommands.Upgrade upgrade = guildSkills.upgrade(characterId, command);
            if (upgrade.changed()) {
                // Buff bang hiệu lực ngay cho mọi thành viên đang online — không chờ đăng nhập lại.
                guildMemberIds(characterId).forEach(this::applyEquipmentBonusAndComputeMaxHp);
            }
            return upgrade.response();
        });
    }

    /**
     * Phần việc của một lệnh chạm DB: handler (để đọc field final mà bảng {@code handlers} — khởi tạo TRƯỚC
     * constructor — chưa thấy được) + nhân vật từ session + lệnh đã giải mã → gói trả lời.
     */
    @FunctionalInterface
    private interface CommandWork<C> {
        com.google.protobuf.MessageLite apply(GatewayChannelHandler handler, long characterId, C command);
    }

    /**
     * Handler chung cho lệnh "giải mã → chạy trên Virtual Thread → trả MỘT gói" (lô 21) — thay cho cặp
     * {@code handleXxxCommand}/{@code handleXxxBlocking} chép tay mỗi opcode. Từ chối giữa chừng đi qua
     * {@link CommandRefusedException}.
     */
    private <C> PacketHandler storageCommand(
            com.google.protobuf.Parser<C> parser, int responseOpcode, CommandWork<C> work) {
        return (ctx, header, payload) -> {
            C command = parser.parseFrom(payload);
            runStorage(ctx, header.sequenceId(), responseOpcode, characterId -> work.apply(this, characterId, command));
        };
    }

    /**
     * ADR-115 — lệnh theo TÀI KHOẢN (khoá an toàn): như {@link #storageCommand} nhưng lấy {@code accountId} từ
     * session, dùng được cả trước khi chọn nhân vật.
     */
    private <C> PacketHandler accountCommand(
            com.google.protobuf.Parser<C> parser, int responseOpcode, CommandWork<C> work) {
        return (ctx, header, payload) -> {
            C command = parser.parseFrom(payload);
            Long accountId = boundAccountId;
            if (accountId == null) {
                sendError(ctx, header.sequenceId(), 401, "error.not_authenticated");
                return;
            }
            executeBlocking(
                    ctx,
                    header.sequenceId(),
                    () -> send(
                            ctx,
                            responseOpcode,
                            header.sequenceId(),
                            work.apply(this, accountId, command).toByteArray()));
        };
    }

    /**
     * ADR-115 — chặn lệnh bằng khoá an toàn: tài khoản có PIN mà phiên chưa kiểm đúng ⇒ 403
     * {@code error.security_pin_required}. Chỉ đọc bộ nhớ ({@link SecurityPinGate}) — an toàn trên EventLoop.
     */
    private PacketHandler pinGuarded(PacketHandler inner) {
        return (ctx, header, payload) -> {
            if (pinGate.blocks()) {
                sendError(ctx, header.sequenceId(), 403, "error.security_pin_required");
                return;
            }
            inner.handle(ctx, header, payload);
        };
    }

    /** Như {@link #pinGuarded} nhưng chỉ chặn khi lệnh thoả {@code guarded} (vd. thư CÓ kèm đồ/linh thạch). */
    private <C> PacketHandler pinGuardedWhen(
            com.google.protobuf.Parser<C> parser, java.util.function.Predicate<C> guarded, PacketHandler inner) {
        return (ctx, header, payload) -> {
            if (pinGate.blocks() && guarded.test(parser.parseFrom(payload))) {
                sendError(ctx, header.sequenceId(), 403, "error.security_pin_required");
                return;
            }
            inner.handle(ctx, header, payload);
        };
    }

    /** Thư mang giá trị (linh thạch hoặc vật phẩm) — thứ khoá an toàn bảo vệ; thư chữ thì không. */
    private static boolean carriesValue(SendMailCommand command) {
        return command.getGoldAmount() != 0 || command.getAttachmentsCount() > 0;
    }

    /** ADR-092 — biểu cảm: không DB, chạy thẳng trên EventLoop (chỉ xếp lệnh vào World). */
    private void handleSendEmoteCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendEmoteCommand command = SendEmoteCommand.parseFrom(payload);
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, header.sequenceId(), 401, "error.not_authenticated");
            return;
        }
        send(
                ctx,
                Opcode.OPCODE_SEND_EMOTE_RESPONSE_VALUE,
                header.sequenceId(),
                relations
                        .emotes()
                        .send(characterId, command.getEmote(), currentWorldFacadeIfPresent(characterId))
                        .toByteArray());
    }

    /** ADR-084 — tặng quà bạn bè theo tên. */
    private void handleSendFriendGiftCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendFriendGiftCommand command = SendFriendGiftCommand.parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_SEND_FRIEND_GIFT_RESPONSE_VALUE, characterId -> {
            characterFacade
                    .findByName(command.getTargetName().strip())
                    .ifPresent(target -> relations.blocks().requireNotBlocked(characterId, target.id()));
            return friendGifts.gift(characterId, command.getTargetName());
        });
    }

    private void handleCraftItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CraftItemCommand command = CraftItemCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_CRAFT_ITEM_RESPONSE_VALUE,
                characterId -> crafting.craft(characterId, command));
    }

    private void handleWithdrawItemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        WithdrawItemCommand command = WithdrawItemCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_WITHDRAW_ITEM_RESPONSE_VALUE,
                characterId -> storage.withdraw(characterId, command));
    }

    /** ADR-113 — lệnh tháp; dựng mỗi lệnh (rẻ, không phải đường game loop) để constructor không vượt MethodLength. */
    private TowerCommands tower() {
        return new TowerCommands(worldGateway.registry(), worldGateway.dungeonInstanceRegistry());
    }

    /** ADR-064 — khảm Linh Ngọc. Trừ vàng + random ⇒ khoá chống trùng bắt buộc. */
    private void handleSocketGemCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SocketGemCommand command = SocketGemCommand.parseFrom(payload);
        if (rejectMalformedIdempotencyKey(ctx, header.sequenceId(), command.getIdempotencyKey())) {
            return;
        }
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> socketGem(ctx, sequenceId, characterId, command));
    }

    /**
     * Thành công thì tính lại chỉ số trang bị + máu tối đa (ADR-064 D4) — món đích có thể đang mặc; tính lại
     * khi nó nằm trong túi thì chỉ số không đổi, vô hại. Cùng đường cường hoá (ADR-043).
     *
     * <p>Món/ngọc đang chào trong giao dịch (docs/README.md dòng 82): KHÔNG thêm giá trị {@code SocketGemOutcome}
     * (đổi protocol) — đi đường {@code ErrorResponse} + error_key như cường hoá; client khớp theo sequenceId.
     */
    private void socketGem(ChannelHandlerContext ctx, int sequenceId, long characterId, SocketGemCommand command) {
        SocketGemUseCase.Result result = inventoryFacade.socketGem(
                characterId, command.getTargetItemId(), command.getGemItemId(), command.getIdempotencyKey());
        if (result.outcome() == SocketGemUseCase.Outcome.ITEM_IN_TRADE) {
            sendError(ctx, sequenceId, 409, "error.item_in_trade");
            return;
        }
        send(
                ctx,
                Opcode.OPCODE_SOCKET_GEM_RESPONSE_VALUE,
                sequenceId,
                socketGemResponse(characterId, result).toByteArray());
    }

    private SocketGemResponse socketGemResponse(long characterId, SocketGemUseCase.Result result) {
        boolean success = result.outcome() == SocketGemUseCase.Outcome.SUCCESS;
        return SocketGemResponse.newBuilder()
                .setOutcome(SocketGemOutcome.valueOf(
                        "SOCKET_GEM_OUTCOME_" + result.outcome().name()))
                .setFailuresInARow(result.failuresAfter())
                .setPityAfter(GemPolicy.PITY_AFTER_FAILURES)
                .setMaxHp(success ? applyEquipmentBonusAndComputeMaxHp(characterId) : 0)
                .build();
    }

    /** ADR-068 — góp linh thạch cho bang. Trừ vàng ⇒ khoá chống trùng bắt buộc. */
    private void handleDonateToGuildCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        DonateToGuildCommand command = DonateToGuildCommand.parseFrom(payload);
        if (rejectMalformedIdempotencyKey(ctx, header.sequenceId(), command.getIdempotencyKey())) {
            return;
        }
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_DONATE_TO_GUILD_RESPONSE_VALUE,
                characterId -> donateToGuild(characterId, command));
    }

    private DonateToGuildResponse donateToGuild(long characterId, DonateToGuildCommand command) {
        GuildProgressionPort.Donation donation =
                guildProgression.donate(characterId, command.getAmount(), command.getIdempotencyKey());
        DonateToGuildResponse.Builder response = DonateToGuildResponse.newBuilder()
                .setOutcome(DonateToGuildOutcome.valueOf(
                        "DONATE_TO_GUILD_OUTCOME_" + donation.outcome().name()));
        if (donation.outcome() == DonationOutcome.OK) {
            guildProgression.standingOf(characterId).map(GuildProgressViews::of).ifPresent(response::setProgress);
            if (donation.leveledUp()) {
                worldGateway.registry().guildProgress.announceLevelUp(donation.guildId());
            }
        }
        return response.build();
    }

    /** ADR-071 — cửa sổ Công pháp. Cấp + linh căn đọc từ DB, không từ gói. */
    private void handleListSkillRanksRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListSkillRanksRequest.parser().parseFrom(payload);
        runStorage(ctx, header.sequenceId(), Opcode.OPCODE_LIST_SKILL_RANKS_RESPONSE_VALUE, this::skillRankSheet);
    }

    private void handleUpgradeSkillCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        UpgradeSkillCommand command = UpgradeSkillCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_UPGRADE_SKILL_RESPONSE_VALUE,
                characterId -> upgradeSkill(characterId, command.getSkillCode()));
    }

    private UpgradeSkillResponse upgradeSkill(long characterId, String skillCode) {
        Optional<Character> character = characterFacade.findById(characterId);
        int level = character.map(Character::level).orElse(1);
        Element element =
                character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
        SkillRankSource ranks = worldGateway.registry().skillRanks;
        SkillRankUseCase.Outcome outcome =
                ranks.facade().upgrade(characterId, skillCode, level, SkillRankSource.factionSkills(element));
        if (outcome == SkillRankUseCase.Outcome.OK) {
            // Hiệu lực ngay trong World đang đứng — không chờ đăng nhập lại. Tâm pháp (ADR-077) đi đường chỉ số.
            SkillRankSnapshot snapshot = ranks.snapshotFor(characterId, element);
            currentWorldFacadeIfPresent(characterId).ifPresent(world -> world.enqueueSkillRanks(characterId, snapshot));
            applyEquipmentBonusAndComputeMaxHp(characterId);
        }
        return UpgradeSkillResponse.newBuilder()
                .setOutcome(UpgradeSkillOutcome.valueOf("UPGRADE_SKILL_OUTCOME_" + outcome.name()))
                .setSheet(skillRankSheet(characterId))
                .build();
    }

    private ListSkillRanksResponse skillRankSheet(long characterId) {
        Optional<Character> character = characterFacade.findById(characterId);
        int level = character.map(Character::level).orElse(1);
        Element element =
                character.map(c -> FactionMapping.toElement(c.faction())).orElse(Element.NONE);
        SkillRankUseCase.Sheet sheet = worldGateway
                .registry()
                .skillRanks
                .facade()
                .sheet(characterId, level, SkillRankSource.factionSkills(element));
        ListSkillRanksResponse.Builder response = ListSkillRanksResponse.newBuilder()
                .setPointsTotal(sheet.pointsTotal())
                .setPointsAvailable(sheet.pointsAvailable());
        int slot = 0;
        for (SkillRankUseCase.SkillRankView skill : sheet.skills()) {
            response.addSkills(SkillRankView.newBuilder()
                    .setSkillCode(skill.code())
                    .setSlotIndex(slot++)
                    .setRank(skill.rank())
                    .setMaxRank(SkillRankPolicy.MAX_RANK)
                    .setRequiredLevel(skill.requiredLevel())
                    .setUnlocked(level >= skill.requiredLevel())
                    .setPassive(skill.passive()));
        }
        return response.build();
    }

    /** ADR-063 — bảng tin phường thị. */
    private void handleListMarketRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListMarketRequest request = ListMarketRequest.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_LIST_MARKET_RESPONSE_VALUE,
                characterId -> market.list(characterId, request));
    }

    private void handleCreateListingCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CreateListingCommand command = CreateListingCommand.parseFrom(payload);
        if (rejectMalformedIdempotencyKey(ctx, header.sequenceId(), command.getIdempotencyKey())) {
            return;
        }
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_CREATE_LISTING_RESPONSE_VALUE,
                characterId -> market.create(characterId, command));
    }

    private void handleBuyListingCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        BuyListingCommand command = BuyListingCommand.parseFrom(payload);
        if (rejectMalformedIdempotencyKey(ctx, header.sequenceId(), command.getIdempotencyKey())) {
            return;
        }
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_BUY_LISTING_RESPONSE_VALUE,
                characterId -> market.buy(characterId, command));
    }

    private void handleCancelListingCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CancelListingCommand command = CancelListingCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_CANCEL_LISTING_RESPONSE_VALUE,
                characterId -> market.cancel(characterId, command));
    }

    /** ADR-062 — cửa sổ thành tựu. Cấp đọc từ DB (mốc cấp so thẳng với cấp, không có bộ đếm). */
    private void handleListAchievementsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListAchievementsRequest.parser().parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_LIST_ACHIEVEMENTS_RESPONSE_VALUE,
                characterId -> AchievementViews.sheet(
                        worldGateway
                                .registry()
                                .achievements
                                .facade()
                                .sheet(
                                        characterId,
                                        characterFacade
                                                .findById(characterId)
                                                .map(Character::level)
                                                .orElse(1)),
                        expansion.lot35().facades().titleBonus()));
    }

    /** ADR-062 D2 — đeo/bỏ danh hiệu; chỉ đeo được cái đã mở (server kiểm trong câu SQL). */
    private void handleSetActiveTitleCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SetActiveTitleCommand command = SetActiveTitleCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_SET_ACTIVE_TITLE_RESPONSE_VALUE,
                characterId -> AchievementViews.titleResponse(
                        wearTitle(characterId, command.getAchievementCode()),
                        worldGateway.registry().achievements.facade().activeTitleCode(characterId)));
    }

    /** Đeo/bỏ danh hiệu; đổi được thì áp lại chỉ số ngay — danh hiệu cộng chỉ số (ADR-210). */
    private boolean wearTitle(long characterId, String achievementCode) {
        boolean worn = worldGateway.registry().achievements.wearTitle(characterId, achievementCode);
        if (worn) {
            applyEquipmentBonusAndComputeMaxHp(characterId);
        }
        return worn;
    }

    /**
     * Khuôn chung các lệnh một-response chạm DB (kho, thành tựu, phường thị): cần đăng nhập, chạy trên
     * Virtual Thread.
     */
    private void runStorage(
            ChannelHandlerContext ctx,
            int sequenceId,
            int responseOpcode,
            java.util.function.LongFunction<com.google.protobuf.MessageLite> work) {
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(
                ctx,
                sequenceId,
                () -> send(
                        ctx, responseOpcode, sequenceId, work.apply(characterId).toByteArray()));
    }

    private void handleListInventoryRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListInventoryRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        Map<Long, Long> sockets = inventoryFacade.socketsOf(characterId);
        java.util.Set<Long> locked = expansion.lot29().itemLock().lockedOf(characterId); // ADR-159
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
                    .setSocketGemTemplateId(sockets.getOrDefault(stack.id(), 0L))
                    .setLocked(locked.contains(stack.id()))
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
        ItemCatalogResponse.Builder response = ItemCatalogResponse.newBuilder()
                .setUnchanged(unchanged)
                .setVersion(catalog.version())
                .setEnhanceStepPercent(dev.kage.inventory.domain.EnhancementTable.ENHANCE_STEP_PERCENT); // ADR-199
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
        ListQuestsRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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

    /** ADR-056 — bảng nhân vật: chỉ số nền + trang bị đọc từ DB, công thức của server-world. */
    private void handleGetCharacterStatsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetCharacterStatsRequest.parser().parseFrom(payload); // không field — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> {
            CharacterStatSheet sheet = statSheetOf(characterId);
            GetCharacterStatsResponse.Builder response = GetCharacterStatsResponse.newBuilder()
                    .setLevel(sheet.level())
                    .setStrength(sheet.strength())
                    .setAgility(sheet.agility())
                    .setIntelligence(sheet.intelligence())
                    .setVitality(sheet.vitality())
                    .setMaxHp(sheet.maxHp())
                    .setEquipmentBonusHp(sheet.equipmentBonusHp())
                    .setMaxChi(sheet.maxChi())
                    .setAttack(sheet.attack())
                    .setSkillDamage(sheet.skillDamage())
                    .setDefense(sheet.defense())
                    .setResistance(sheet.resistance())
                    .setHitPermille(sheet.hitPermille())
                    .setDodgePermille(sheet.dodgePermille())
                    .setCritPermille(sheet.critPermille())
                    .setCritDamagePercent(sheet.critDamagePercent())
                    .setCombatPower(sheet.combatPower())
                    .addAllItemSets(itemSetViews(characterId))
                    .setMoveSpeedPercent(inventoryFacade.moveSpeedPercent(characterId))
                    .setRebirthCount(rebirth.rebirthCountOf(characterId));
            inventoryFacade.equippedPet(characterId).ifPresent(pet -> response.setPet(petView(characterId, pet)));
            send(
                    ctx,
                    Opcode.OPCODE_GET_CHARACTER_STATS_RESPONSE_VALUE,
                    sequenceId,
                    response.build().toByteArray());
        });
    }

    /** ADR-072/078/193 — linh thú đang mặc kèm tên riêng. Tên chạm DB — gọi trên Virtual Thread. */
    private PetView petView(long characterId, dev.kage.inventory.domain.PetProgress pet) {
        return PetView.newBuilder()
                .setItemTemplateId(pet.itemTemplateId())
                .setLevel(pet.level())
                .setExpIntoLevel(pet.expIntoLevel())
                .setExpToNext(pet.expToNext())
                .setStage(pet.stage())
                .setCustomName(expansion
                        .lot33()
                        .facades()
                        .petName()
                        .equippedName(characterId)
                        .orElse(""))
                .build();
    }

    /** ADR-065 — bộ pháp khí đang mặc; thưởng đã nằm trong các tổng của bảng. Chạm DB. */
    private List<ItemSetProgressView> itemSetViews(long characterId) {
        List<ItemSetProgressView> views = new ArrayList<>();
        for (ItemSetProgress set : inventoryFacade.itemSetProgress(characterId)) {
            views.add(ItemSetProgressView.newBuilder()
                    .setNameKey(set.nameKey())
                    .setWornPieces(set.wornPieces())
                    .setTotalPieces(set.totalPieces())
                    .setBonusAtk(set.activeBonus().bonusAtk())
                    .setBonusDef(set.activeBonus().bonusDef())
                    .setBonusRes(set.activeBonus().bonusRes())
                    .setBonusHp(set.activeBonus().bonusHp())
                    .build());
        }
        return views;
    }

    /** Chỉ số nền + trang bị HIỆN TẠI trong DB — cùng {@link CharacterLoadout} mà attach dùng. Chạm DB. */
    private CharacterStatSheet statSheetOf(long characterId) {
        CharacterLoadout loadout = CharacterLoadout.load(characterFacade, worldGateway.registry(), characterId);
        return WorldFacade.statSheet(loadout.baseStats(), loadout.equipmentBonus());
    }

    /** Nguồn dữ liệu của mọi bảng xếp hạng — dựng ở lượt hỏi đầu, sau đó {@code shared} trả bản cũ. */
    private LeaderboardCache.Sources leaderboardSources() {
        return new LeaderboardCache.Sources(
                characterFacade,
                pvpFacade,
                character -> statSheetOf(character.id()).combatPower(),
                guildProgression::rankGuilds,
                () -> worldBoss.topDamage(LeaderboardCache.TOP_SIZE),
                expansion.cave()::veinLevels, // ADR-134
                worldGateway.registry().tribulation::passedByCharacter,
                worldGateway.registry().achievements.facade()::unlockedCounts); // ADR-209
    }

    private static LeaderboardCache.Kind toCacheKind(LeaderboardKind kind) {
        return switch (kind) {
            case LEADERBOARD_KIND_ARENA -> LeaderboardCache.Kind.ARENA;
            case LEADERBOARD_KIND_COMBAT_POWER -> LeaderboardCache.Kind.COMBAT_POWER;
            case LEADERBOARD_KIND_GUILD -> LeaderboardCache.Kind.GUILD;
            case LEADERBOARD_KIND_BOSS_DAMAGE -> LeaderboardCache.Kind.BOSS_DAMAGE;
            case LEADERBOARD_KIND_CAVE -> LeaderboardCache.Kind.CAVE;
            case LEADERBOARD_KIND_TRIBULATION -> LeaderboardCache.Kind.TRIBULATION;
            case LEADERBOARD_KIND_ACHIEVEMENT -> LeaderboardCache.Kind.ACHIEVEMENT;
            // UNSPECIFIED/lạ (client mới hơn server) ⇒ bảng cảnh giới, không lỗi.
            default -> LeaderboardCache.Kind.REALM;
        };
    }

    /** ADR-057 — Bia Anh Danh: bảng dùng chung cả server, cache 60 s ({@link LeaderboardCache}). */
    private void handleGetLeaderboardRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        GetLeaderboardRequest request = GetLeaderboardRequest.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        LeaderboardCache.Kind kind = toCacheKind(request.getKind());
        executeBlocking(ctx, sequenceId, () -> {
            LeaderboardCache.Board board =
                    LeaderboardCache.shared(leaderboardSources()).board(kind);
            GetLeaderboardResponse.Builder response = GetLeaderboardResponse.newBuilder()
                    .setKind(toProtoLeaderboardKind(kind))
                    .setComputedAtEpochMs(board.computedAt().toEpochMilli());
            int shown = Math.min(board.rows().size(), LeaderboardCache.TOP_SIZE);
            for (int i = 0; i < shown; i++) {
                LeaderboardCache.Row row = board.rows().get(i);
                response.addEntries(LeaderboardEntry.newBuilder()
                        .setRank(row.rank())
                        .setCharacterId(row.characterId())
                        .setName(row.name())
                        .setFaction(toProtoFaction(row.faction()))
                        .setLevel(row.level())
                        .setValue(row.value())
                        .setMemberCount(row.guild().members()) // ADR-181 — 0 trừ bảng bang
                        .setTotalContribution(row.guild().contribution()));
            }
            LeaderboardCache.Row own = board.byCharacterId().get(characterId);
            if (own != null) {
                response.setOwnRank(own.rank()).setOwnValue(own.value());
            }
            send(
                    ctx,
                    Opcode.OPCODE_GET_LEADERBOARD_RESPONSE_VALUE,
                    sequenceId,
                    response.build().toByteArray());
        });
    }

    private static LeaderboardKind toProtoLeaderboardKind(LeaderboardCache.Kind kind) {
        return switch (kind) {
            case REALM -> LeaderboardKind.LEADERBOARD_KIND_REALM;
            case ARENA -> LeaderboardKind.LEADERBOARD_KIND_ARENA;
            case COMBAT_POWER -> LeaderboardKind.LEADERBOARD_KIND_COMBAT_POWER;
            case GUILD -> LeaderboardKind.LEADERBOARD_KIND_GUILD;
            case BOSS_DAMAGE -> LeaderboardKind.LEADERBOARD_KIND_BOSS_DAMAGE;
            case CAVE -> LeaderboardKind.LEADERBOARD_KIND_CAVE;
            case TRIBULATION -> LeaderboardKind.LEADERBOARD_KIND_TRIBULATION;
            case ACHIEVEMENT -> LeaderboardKind.LEADERBOARD_KIND_ACHIEVEMENT;
        };
    }

    /**
     * ADR-052 — một dòng {@code /gm}: trả OK như một tin chat bình thường (không xác nhận lệnh GM tồn tại),
     * rồi chạy router trên Virtual Thread; phản hồi (nếu có quyền) là dòng HỆ THỐNG chỉ gửi cho người ra lệnh.
     */
    private void handleGmLine(ChannelHandlerContext ctx, int sequenceId, GmCommandRouter.Actor actor, String line) {
        sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK);
        if (!gmRateLimiter.tryAcquire()) {
            LOG.warn("Lệnh GM vượt trần 5/10s — bỏ: accountId={}", actor.accountId());
            return;
        }
        executeBlocking(ctx, sequenceId, () -> {
            for (String reply : gmRouter().execute(actor, line)) {
                ChatMessageEvent event = ChatMessageEvent.newBuilder()
                        .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                        .setSenderName("")
                        .setBody(reply)
                        .build();
                send(ctx, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event.toByteArray());
            }
        });
    }

    private GmCommandRouter gmRouter() {
        return new GmCommandRouter(
                new GmCommandRouter.Deps(
                        gmFacade,
                        characterFacade,
                        chatFacade,
                        mailFacade,
                        inventoryFacade,
                        worldGateway.registry(),
                        this::currentWorldFacadeIfPresent,
                        targetId -> CharacterLoadout.load(characterFacade, worldGateway.registry(), targetId)
                                .maxHp(),
                        new MainWorldRelocation(
                                worldGateway.mainWorldChannels(), worldGateway.registry(), characterFacade)::move,
                        gameEvents,
                        serviceDesk.gmDesk(inventoryFacade)),
                GmConfig.current(),
                java.time.Clock.systemUTC());
    }

    /** ADR-059 — người nhận event nhóm/bang; dựng mỗi lần (rẻ, không trạng thái). */
    private MembershipNotices membershipNotices() {
        return new MembershipNotices(worldGateway.registry());
    }

    private List<Long> partyMemberIds(long characterId) {
        return MembershipNotices.ids(partyFacade.listMembers(characterId), PartyFacade.MemberView::characterId);
    }

    private List<Long> guildMemberIds(long characterId) {
        return MembershipNotices.ids(guildFacade.listMembers(characterId), GuildFacade.MemberView::characterId);
    }

    /** ADR-054 — bỏ quest đang làm; chạm DB nên chạy trên authExecutor. */
    private void handleAbandonQuestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        AbandonQuestCommand command = AbandonQuestCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> {
            QuestFacade.AbandonResult result = questFacade.abandonQuest(characterId, command.getQuestCode());
            if (result == QuestFacade.AbandonResult.OK) { // ADR-114 — bỏ quest hộ tống thì đoàn biến mất
                worldGateway.registry().escorts.onQuestAbandoned(characterId, currentWorldFacadeIfPresent(characterId));
            }
            AbandonQuestResponse response = AbandonQuestResponse.newBuilder()
                    .setOutcome(
                            switch (result) {
                                case OK -> AbandonQuestOutcome.ABANDON_QUEST_OUTCOME_OK;
                                case QUEST_NOT_FOUND -> AbandonQuestOutcome.ABANDON_QUEST_OUTCOME_QUEST_NOT_FOUND;
                                case NOT_IN_PROGRESS -> AbandonQuestOutcome.ABANDON_QUEST_OUTCOME_NOT_IN_PROGRESS;
                            })
                    .setQuestCode(command.getQuestCode())
                    .build();
            send(ctx, Opcode.OPCODE_ABANDON_QUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
        });
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
        ListShopItemsRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())
                || rejectMalformedShape(ctx, sequenceId, EconomyCommandShape.isValidQuantity(command.getQuantity()))) {
            return;
        }
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())
                || rejectMalformedShape(ctx, sequenceId, EconomyCommandShape.isValidQuantity(command.getQuantity()))) {
            return;
        }
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
            ShopFacade.SellResult.ITEM_EQUIPPED, SellOutcome.SELL_OUTCOME_ITEM_NOT_SELLABLE,
            ShopFacade.SellResult.ITEM_IN_TRADE, SellOutcome.SELL_OUTCOME_ITEM_IN_TRADE);

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
        Optional<DialogueNode> node = dialogueFacade.startDialogue(command.getNpcCode(), dialogueFactsOf(characterId));
        if (node.isEmpty()) {
            sendError(ctx, sequenceId, 404, "error.npc_not_found");
            return;
        }
        // SAU phép kiểm "NPC có tồn tại không": mã bịa vẫn nhận 404 như cũ, chỉ NPC có thật mà đứng xa
        // mới nhận câu "lại gần hơn". TRƯỚC khi gửi cây thoại và trước sự kiện quest "đi gặp X".
        if (rejectIfFarFromNpc(ctx, sequenceId, characterId, command.getNpcCode())) {
            return;
        }
        send(
                ctx,
                Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
                sequenceId,
                toResponse(node.get()).toByteArray());
        pushQuestProgressFromTalking(ctx, characterId, command.getNpcCode());
    }

    /** Dữ kiện chỉ-đọc cho script hội thoại (dòng 65(c)) — một bản mỗi lượt, xem {@link CharacterDialogueFacts}. */
    private CharacterDialogueFacts dialogueFactsOf(long characterId) {
        return new CharacterDialogueFacts(characterId, questFacade, inventoryFacade);
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

    /**
     * Từ chối bằng {@code error.npc_too_far} nếu nhân vật không đứng trong tầm của NPC — xem
     * {@link NpcProximity}. Chạy trên luồng blocking: vị trí có thể phải đọc từ DB.
     *
     * @return {@code true} nếu đã từ chối (đã gửi lỗi) — người gọi dừng
     */
    private boolean rejectIfFarFromNpc(ChannelHandlerContext ctx, int sequenceId, long characterId, String npcCode) {
        if (npcProximity.isWithinReach(characterId, npcCode)) {
            return false;
        }
        sendError(ctx, sequenceId, 409, "error.npc_too_far");
        return true;
    }

    /**
     * Vị trí ở world chính của nhân vật, cho {@link NpcProximity}. Đang trong hầm ngục/đấu trường thì
     * rỗng: vị trí gateway còn giữ lúc đó là chỗ họ đứng TRƯỚC khi vào, và NPC không có ở trong đó.
     * Chưa có vị trí nào được báo (vừa vào world, chưa nhúc nhích) thì đọc chỗ đã lưu — cùng cách
     * {@code ArenaInstanceRegistry} lấy vị trí quay về.
     */
    private Optional<PositionSnapshot> mainWorldPositionOf(long characterId) {
        if (worldGateway.dungeonInstanceRegistry().isInDungeon(characterId)
                || worldGateway.arenaInstanceRegistry().isInDuel(characterId)) {
            return Optional.empty();
        }
        PositionSnapshot known = worldGateway.registry().lastKnownPosition(characterId);
        return Optional.of(known != null ? known : characterFacade.loadPosition(characterId));
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
        // Mọi lựa chọn thoại — kể cả nhận quest và mở cửa hàng — đều là nói chuyện với NPC đó: đi khỏi
        // tầm rồi thì cuộc thoại không tiếp tục được (quyết định chủ dự án 2026-10-11).
        if (rejectIfFarFromNpc(ctx, sequenceId, characterId, command.getNpcCode())) {
            return;
        }
        DialogueFacade.SelectOptionResult result = dialogueFacade.selectOption(
                command.getNpcCode(),
                command.getCurrentNodeId(),
                command.getOptionIndex(),
                dialogueFactsOf(characterId));
        switch (result) {
            case DialogueFacade.SelectOptionResult.Node r ->
                send(
                        ctx,
                        Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE,
                        sequenceId,
                        toResponse(r.node()).toByteArray());
            case DialogueFacade.SelectOptionResult.OpenShop ignored -> sendShop(ctx, sequenceId);
            case DialogueFacade.SelectOptionResult.OpenStorage ignored -> sendStorage(ctx, sequenceId, characterId);
            case DialogueFacade.SelectOptionResult.OpenCrafting ignored -> sendRecipes(ctx, sequenceId);
            case DialogueFacade.SelectOptionResult.OpenForge ignored -> sendForge(ctx, sequenceId);
            case DialogueFacade.SelectOptionResult.OpenMerchant ignored -> sendMerchant(ctx, sequenceId);
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
                if (acceptResult == QuestFacade.AcceptResult.OK) { // ADR-114 — quest hộ tống thì xếp đoàn
                    worldGateway
                            .registry()
                            .escorts
                            .onQuestAccepted(characterId, r.questCode(), currentWorldFacadeIfPresent(characterId));
                }
                AcceptQuestResponse response = AcceptQuestResponse.newBuilder()
                        .setOutcome(toProtoOutcome(acceptResult))
                        .build();
                send(ctx, Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE, sequenceId, response.toByteArray());
            }
        }
    }

    /** Lựa chọn thoại "open_storage" (ADR-061). */
    private void sendStorage(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        send(
                ctx,
                Opcode.OPCODE_LIST_STORAGE_RESPONSE_VALUE,
                sequenceId,
                storage.list(characterId).toByteArray());
    }

    /** Lựa chọn thoại "open_shop". */
    private void sendShop(ChannelHandlerContext ctx, int sequenceId) {
        send(
                ctx,
                Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE,
                sequenceId,
                buildShopListingsResponse().toByteArray());
    }

    /** Lựa chọn thoại "open_forge" (ADR-131 — thợ rèn nhận lò luyện khí). */
    private void sendForge(ChannelHandlerContext ctx, int sequenceId) {
        send(
                ctx,
                Opcode.OPCODE_LIST_FORGE_RECIPES_RESPONSE_VALUE,
                sequenceId,
                crafting.listForge().toByteArray());
    }

    /** Lựa chọn thoại "open_merchant" (ADR-131 — NPC thương nhân du hành). */
    private void sendMerchant(ChannelHandlerContext ctx, int sequenceId) {
        send(
                ctx,
                Opcode.OPCODE_GET_TRAVELING_MERCHANT_RESPONSE_VALUE,
                sequenceId,
                expansion.merchant().status().toByteArray());
    }

    /** Lựa chọn thoại "open_crafting" (ADR-079). */
    private void sendRecipes(ChannelHandlerContext ctx, int sequenceId) {
        send(
                ctx,
                Opcode.OPCODE_LIST_RECIPES_RESPONSE_VALUE,
                sequenceId,
                crafting.list().toByteArray());
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())) {
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
                .setExpGranted(result.expGranted())
                .setGoldGranted(result.goldGranted());
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
            // đúng cách sendInitialSelfState làm lúc vào world — không cộng thêm lần nữa. Khác lúc vào
            // world ở đúng một điểm: lần lĩnh thưởng này CÓ THỂ vừa lên cấp, và client chỉ hiện "lên
            // cấp" khi leveledUp=true (kiểm sống chuỗi quest 2026-10-03: 6/6 lần lên cấp nhờ thưởng
            // quest đều mang false).
            worldGateway
                    .registry()
                    .sendExperience(characterId, characterFacade.syncExperienceAfterQuestReward(characterId));
            worldGateway.registry().achievements.onQuestRewardClaimed(characterId);
            if (questFacade.isDailyQuest(command.getQuestCode())) {
                worldGateway.registry().activity.record(characterId, ActivityFacade.Activity.DAILY_QUEST);
                worldGateway
                        .registry()
                        .collections
                        .addReputation(characterId, ReputationRules.DAILY_QUEST_POINTS); // ADR-109
            }
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
            if (relations.blocks().eitherBlocks(characterId, target.get().id())) {
                sendError(ctx, sequenceId, BlockCommands.BLOCKED_CODE, BlockCommands.BLOCKED_KEY);
                return;
            }
            FriendFacade.SendRequestResult result =
                    friendFacade.sendRequest(characterId, target.get().id());
            if (result == FriendFacade.SendRequestResult.FRIEND_LIST_FULL) {
                // Trần bạn bè (quyết định chủ dự án 2026-10-11) — đi đường ErrorResponse như mọi từ chối
                // nghiệp vụ thêm sau khi protocol đã chốt, không thêm giá trị outcome.
                sendError(ctx, sequenceId, 409, "error.friend_list_full");
                return;
            }
            if (result == FriendFacade.SendRequestResult.OTHER_FRIEND_LIST_FULL) {
                sendError(ctx, sequenceId, 409, "error.friend_list_full_other");
                return;
            }
            // Hai trần chống làm phiền (dòng 71(b), 2026-10-17) — cùng đường ErrorResponse.
            if (result == FriendFacade.SendRequestResult.RECENTLY_REJECTED) {
                sendError(ctx, sequenceId, 429, "error.friend_request_cooldown");
                return;
            }
            if (result == FriendFacade.SendRequestResult.TARGET_PENDING_FULL) {
                sendError(ctx, sequenceId, 409, "error.friend_requests_full_other");
                return;
            }
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
                    // Mời ngược chiều vừa kết bạn (dòng 71(c), 2026-10-17): không thêm giá trị outcome
                    // (protocol đã chốt) — "đã là bạn" là câu ĐÚNG ngay lúc trả lời, và client hỏi lại
                    // danh sách khi nhận nó (FriendCoordinator).
                    FriendFacade.SendRequestResult.BECAME_FRIENDS,
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
            if (result == FriendFacade.AcceptResult.FRIEND_LIST_FULL) {
                sendError(ctx, sequenceId, 409, "error.friend_list_full");
                return;
            }
            if (result == FriendFacade.AcceptResult.REQUESTER_FRIEND_LIST_FULL) {
                sendError(ctx, sequenceId, 409, "error.friend_list_full_other");
                return;
            }
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

    /** ADR-074 — rút lời mời kết bạn mình đã gửi, theo tên người nhận. */
    private void handleCancelFriendRequestCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CancelFriendRequestCommand command = CancelFriendRequestCommand.parseFrom(payload);
        runStorage(
                ctx,
                header.sequenceId(),
                Opcode.OPCODE_CANCEL_FRIEND_REQUEST_RESPONSE_VALUE,
                characterId -> cancelFriendRequest(characterId, command));
    }

    private CancelFriendRequestResponse cancelFriendRequest(long characterId, CancelFriendRequestCommand command) {
        CancelFriendRequestOutcome outcome = characterFacade
                .findByName(command.getTargetName())
                .map(target -> friendFacade.withdrawRequest(characterId, target.id()) == FriendFacade.RemoveResult.OK
                        ? CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_OK
                        : CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_NOT_PENDING)
                .orElse(CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND);
        return CancelFriendRequestResponse.newBuilder().setOutcome(outcome).build();
    }

    private void handleListFriendsRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListFriendsRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
            Map<Long, Integer> intimacy = friendGifts.intimacyOf(characterId);
            long daoLuPartner = relations.daoLu().partnerOf(characterId).orElse(0L);
            Map<Long, String> notes = expansion.lot30().facades().friendNotes().notesOf(characterId);
            List<Long> friendIds = friendFacade.listFriends(characterId);
            Map<Long, java.time.Instant> lastOnline =
                    expansion.lot33().facades().lastOnline().lastOnlineOf(friendIds);
            for (long friendId : friendIds) {
                // findByName/findById chạm DB mỗi bạn — chấp nhận N+1 ở quy mô danh sách bạn bè MVP
                // (vài chục), không phải đường nóng mỗi tick (khác AoI/combat).
                String name =
                        characterFacade.findById(friendId).map(Character::name).orElse("");
                responseBuilder.addFriends(FriendView.newBuilder()
                        .setCharacterId(friendId)
                        .setName(name)
                        .setOnline(worldGateway.registry().isOnline(friendId))
                        .setIntimacy(intimacy.getOrDefault(friendId, 0))
                        .setDaoLu(friendId == daoLuPartner)
                        .setNote(notes.getOrDefault(friendId, ""))
                        .setLastOnlineEpochSeconds(lastOnlineSeconds(lastOnline, friendId))
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
        ListPendingFriendRequestsRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        if (result.outcome() == GuildFacade.CreateOutcome.INVALID_NAME) {
            // Quyết định chủ dự án 2026-09-29: đi đường ErrorResponse + error_key, KHÔNG thêm giá trị
            // vào CreateGuildOutcome (đổi protocol).
            sendError(ctx, sequenceId, 400, "error.guild_name_invalid");
            return;
        }
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
        // ADR-185 — bang đang chiêu mộ với cấp tối thiểu: kiểm Ở SERVER, không chỉ hiển thị trên bảng.
        java.util.OptionalInt minLevel = expansion.lot32().guildRecruitment().minLevelOf(command.getGuildName());
        if (minLevel.isPresent()
                && characterFacade.findById(characterId).map(Character::level).orElse(0) < minLevel.getAsInt()) {
            send(
                    ctx,
                    Opcode.OPCODE_SEND_GUILD_JOIN_REQUEST_RESPONSE_VALUE,
                    sequenceId,
                    SendGuildJoinRequestResponse.newBuilder()
                            .setOutcome(SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_LEVEL_TOO_LOW)
                            .build()
                            .toByteArray());
            return;
        }
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
        List<Long> before = guildMemberIds(characterId);
        GuildFacade.ApproveResult result = guildFacade.approveJoinRequest(command.getRequestId(), characterId);
        if (result == GuildFacade.ApproveResult.OK) {
            membershipNotices().guildMembershipChanged(characterId, before, guildMemberIds(characterId));
        }
        if (result == GuildFacade.ApproveResult.GUILD_FULL) {
            // Trần thành viên bang (quyết định chủ dự án 2026-10-11) — cùng đường ErrorResponse.
            sendError(ctx, sequenceId, 409, "error.guild_full");
            return;
        }
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
        LeaveGuildCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleLeaveGuildBlocking(ctx, sequenceId, characterId));
    }

    private void handleLeaveGuildBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        List<Long> before = guildMemberIds(characterId);
        GuildFacade.LeaveResult result = guildFacade.leaveGuild(characterId);
        if (result == GuildFacade.LeaveResult.OK) {
            membershipNotices()
                    .guildMembershipChanged(characterId, before, MembershipNotices.without(before, characterId));
        }
        LeaveGuildOutcome outcome =
                switch (result) {
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
        DisbandGuildCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleDisbandGuildBlocking(ctx, sequenceId, characterId));
    }

    private void handleDisbandGuildBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        List<Long> before = guildMemberIds(characterId);
        boolean disbanded = guildFacade.disbandGuild(characterId) == GuildFacade.DisbandResult.OK;
        if (disbanded) {
            membershipNotices().guildDisbanded(characterId, before);
        }
        DisbandGuildOutcome outcome = disbanded
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
        List<Long> before = guildMemberIds(characterId);
        GuildFacade.KickResult result =
                guildFacade.kickMember(characterId, target.get().id());
        if (result == GuildFacade.KickResult.OK) {
            membershipNotices().guildKick(characterId, target.get().id(), before);
        }
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
        ListGuildMembersRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        // ADR-068 — cống hiến: một câu cho cả bang, không N+1.
        Map<Long, Long> contributions = guildProgression
                .standingOf(characterId)
                .map(standing -> guildProgression.contributionsOf(standing.guildId()))
                .orElse(Map.of());
        List<GuildFacade.MemberView> members = guildFacade.listMembers(characterId);
        Map<Long, java.time.Instant> lastOnline = expansion
                .lot33()
                .facades()
                .lastOnline()
                .lastOnlineOf(members.stream()
                        .map(GuildFacade.MemberView::characterId)
                        .toList());
        for (GuildFacade.MemberView member : members) {
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
                    .setIsViceLeader(member.isViceLeader()) // ADR-184
                    .setOnline(worldGateway.registry().isOnline(member.characterId()))
                    .setContribution(contributions.getOrDefault(member.characterId(), 0L))
                    .setLastOnlineEpochSeconds(lastOnlineSeconds(lastOnline, member.characterId()))
                    .build());
        }
        send(
                ctx,
                Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    /** ADR-192 — giây epoch lần cuối online; 0 khi đang online hoặc chưa từng có mốc. */
    private long lastOnlineSeconds(Map<Long, java.time.Instant> lastOnline, long characterId) {
        if (worldGateway.registry().isOnline(characterId)) {
            return 0L;
        }
        java.time.Instant at = lastOnline.get(characterId);
        return at == null ? 0L : at.getEpochSecond();
    }

    private void handleListPendingGuildJoinRequestsRequest(
            ChannelHandlerContext ctx, PacketHeader header, byte[] payload) throws InvalidProtocolBufferException {
        ListPendingGuildJoinRequestsRequest.parser()
                .parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        GetMyGuildRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
                .setIsLeader(guild.isLeader())
                .setIsViceLeader(guild.isViceLeader()) // ADR-184
                .setNotice(expansion.lot31().guildNotice().noticeOf(characterId))); // ADR-179
        if (myGuild.isPresent()) {
            guildProgression
                    .standingOf(characterId)
                    .map(GuildProgressViews::of)
                    .ifPresent(responseBuilder::setProgress);
        }
        send(
                ctx,
                Opcode.OPCODE_GET_MY_GUILD_RESPONSE_VALUE,
                sequenceId,
                responseBuilder.build().toByteArray());
    }

    private void handleCreatePartyCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        CreatePartyCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        if (relations.blocks().eitherBlocks(characterId, target.get().id())) {
            sendError(ctx, sequenceId, BlockCommands.BLOCKED_CODE, BlockCommands.BLOCKED_KEY);
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
        if (result == PartyFacade.AcceptResult.OK) {
            List<Long> after = partyMemberIds(characterId);
            membershipNotices()
                    .partyMembershipChanged(characterId, MembershipNotices.without(after, characterId), after);
        }
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
        LeavePartyCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        executeBlocking(ctx, sequenceId, () -> handleLeavePartyBlocking(ctx, sequenceId, characterId));
    }

    private void handleLeavePartyBlocking(ChannelHandlerContext ctx, int sequenceId, long characterId) {
        List<Long> before = partyMemberIds(characterId);
        boolean left = partyFacade.leaveParty(characterId) == PartyFacade.LeaveResult.OK;
        if (left) {
            membershipNotices()
                    .partyMembershipChanged(characterId, before, MembershipNotices.without(before, characterId));
        }
        LeavePartyOutcome outcome =
                left ? LeavePartyOutcome.LEAVE_PARTY_OUTCOME_OK : LeavePartyOutcome.LEAVE_PARTY_OUTCOME_NOT_IN_PARTY;
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
        List<Long> before = partyMemberIds(characterId);
        PartyFacade.KickResult result =
                partyFacade.kickMember(characterId, target.get().id());
        if (result == PartyFacade.KickResult.OK) {
            membershipNotices().partyKick(characterId, target.get().id(), before);
        }
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
        ListPartyMembersRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        ListPendingPartyInvitesRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        GetMyPartyRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
                    case IN_DUEL -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_IN_DUEL;
                    case DAILY_LIMIT_REACHED -> EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_DAILY_LIMIT_REACHED;
                };
        EnterDungeonResponse response = EnterDungeonResponse.newBuilder()
                .setOutcome(protoOutcome)
                .setEntriesLeft(worldGateway.dungeonInstanceRegistry().entriesLeft(characterId))
                .build();
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
        ExitDungeonCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        if (relations.blocks().eitherBlocks(characterId, target.get().id())) {
            sendError(ctx, sequenceId, BlockCommands.BLOCKED_CODE, BlockCommands.BLOCKED_KEY);
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
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_OFFLINE,
                    ArenaInstanceRegistry.ChallengeOutcome.IN_DUNGEON,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_IN_DUNGEON,
                    ArenaInstanceRegistry.ChallengeOutcome.TARGET_IN_DUNGEON,
                    ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_IN_DUNGEON);

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
        AcceptDuelOutcome outcome =
                switch (result.outcome()) {
                    case OK -> AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_OK;
                    case NOT_FOUND -> AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND;
                    case IN_DUNGEON -> AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_IN_DUNGEON;
                };
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
        ExitDuelCommand.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        GetPvpRatingRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
        if (relations.blocks().eitherBlocks(characterId, target.get().id())) {
            sendError(ctx, sequenceId, BlockCommands.BLOCKED_CODE, BlockCommands.BLOCKED_KEY);
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
        if (rejectMalformedShape(ctx, sequenceId, EconomyCommandShape.isWellFormed(command))) {
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())) {
            return;
        }
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
            TradeFacade.ConfirmResult result = tradeFacade.confirm(
                    command.getSessionId(), characterId, command.getIdempotencyKey(), command.getOfferVersion());
            span.setAttribute("result", result.name());
            if (result == TradeFacade.ConfirmResult.OFFER_JUST_CHANGED) {
                // Không có outcome trên dây (protocol đã chốt) — cùng khuôn error.friend_request_cooldown.
                sendError(ctx, request.sequenceId(), 409, "error.trade_offer_just_changed");
                return;
            }
            ConfirmTradeOutcome outcome =
                    switch (result) {
                        case OK -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OK;
                        case EXECUTED -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED;
                        case NOT_FOUND -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_NOT_FOUND;
                        case OFFER_INVALID -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OFFER_INVALID;
                        case DUPLICATE_REQUEST -> ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_DUPLICATE_REQUEST;
                        case OFFER_JUST_CHANGED -> throw new IllegalStateException("đã trả lỗi ở trên");
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
        GetTradeStatusRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
                    .setTheirConfirmed(status.theirConfirmed())
                    .setOfferVersion(status.offerVersion());
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
        // ADR-058: khoá rỗng = client cũ (không chống trùng); có khoá thì phải đúng định dạng.
        if (!command.getIdempotencyKey().isEmpty()
                && rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())) {
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
                .map(a -> new MailFacade.AttachmentRequest(a.getItemTemplateId(), a.getQuantity(), a.getEnhanceLevel()))
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
        MailFacade.SendOutcome outcome = mailFacade.sendMail(request, command.getIdempotencyKey());
        if (rejectSendMailAsError(ctx, sequenceId, outcome.result())) {
            return;
        }
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

    /**
     * Hai kết quả gửi thư không có {@code SendMailOutcome} tương ứng (thêm là đổi protocol) — đi đường
     * {@code ErrorResponse}, client khớp theo {@code sequenceId}. Chưa có thư nào được tạo.
     *
     * @return {@code true} nếu đã trả lời bằng lỗi (caller dừng lại)
     */
    private boolean rejectSendMailAsError(ChannelHandlerContext ctx, int sequenceId, MailFacade.SendResult result) {
        if (result == MailFacade.SendResult.INVALID_TEXT) {
            sendError(ctx, sequenceId, 400, "error.invalid_payload");
            return true;
        }
        if (result == MailFacade.SendResult.MAILBOX_FULL) {
            // Trần hộp thư (dòng 41(iv), 2026-10-17) — cùng khuôn error.friend_list_full.
            sendError(ctx, sequenceId, 409, "error.mailbox_full");
            return true;
        }
        return false;
    }

    private static final Map<MailFacade.SendResult, SendMailOutcome> SEND_MAIL_OUTCOME_MAPPING = Map.of(
            MailFacade.SendResult.OK, SendMailOutcome.SEND_MAIL_OUTCOME_OK,
            MailFacade.SendResult.INSUFFICIENT_GOLD, SendMailOutcome.SEND_MAIL_OUTCOME_INSUFFICIENT_GOLD,
            MailFacade.SendResult.ATTACHMENT_NOT_OWNED, SendMailOutcome.SEND_MAIL_OUTCOME_ATTACHMENT_NOT_OWNED,
            MailFacade.SendResult.INVALID_GOLD_AMOUNT, SendMailOutcome.SEND_MAIL_OUTCOME_INVALID_GOLD_AMOUNT,
            MailFacade.SendResult.INVALID_ATTACHMENT, SendMailOutcome.SEND_MAIL_OUTCOME_INVALID_ATTACHMENT,
            MailFacade.SendResult.SUBJECT_TOO_LONG, SendMailOutcome.SEND_MAIL_OUTCOME_SUBJECT_TOO_LONG,
            MailFacade.SendResult.BODY_TOO_LONG, SendMailOutcome.SEND_MAIL_OUTCOME_BODY_TOO_LONG,
            MailFacade.SendResult.DUPLICATE_REQUEST, SendMailOutcome.SEND_MAIL_OUTCOME_DUPLICATE_REQUEST);

    private static SendMailOutcome toProtoOutcome(MailFacade.SendResult result) {
        return SEND_MAIL_OUTCOME_MAPPING.get(result);
    }

    private void handleListMailRequest(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        ListMailRequest.parser().parseFrom(payload); // không field nào — parse để chắc payload hợp lệ
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
                        .setEnhanceLevel(attachment.enhanceLevel())
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
        if (rejectMalformedIdempotencyKey(ctx, sequenceId, command.getIdempotencyKey())) {
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

    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void handleSendChatMessageCommand(ChannelHandlerContext ctx, PacketHeader header, byte[] payload)
            throws InvalidProtocolBufferException {
        SendChatMessageCommand command = SendChatMessageCommand.parseFrom(payload);
        int sequenceId = header.sequenceId();
        Long characterId = boundCharacterId;
        if (characterId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // Trần chat theo TÀI KHOẢN, không theo kết nối — xem ChatRateLimits. Đã có nhân vật thì chắc
        // chắn đã đăng nhập; nhánh null chỉ để không bao giờ unbox null trên EventLoop.
        Long accountId = boundAccountId;
        if (accountId == null) {
            sendError(ctx, sequenceId, 401, "error.not_authenticated");
            return;
        }
        // ADR-052 D1 — dòng "/gm ..." KHÔNG BAO GIỜ đi đường chat: không phát, không ghi CHAT_MESSAGE, không
        // ăn vào hạn mức chat; có trần riêng theo kết nối (D8).
        if (dev.kage.gm.domain.GmCommandParser.isGmLine(command.getBody())) {
            handleGmLine(
                    ctx,
                    sequenceId,
                    new GmCommandRouter.Actor(
                            accountId, characterId, ctx.channel().remoteAddress()),
                    command.getBody());
            return;
        }
        ChatRateLimits.Verdict verdict =
                worldGateway.registry().chatRateLimits().acquire(accountId, command.getChannel());
        if (verdict != ChatRateLimits.Verdict.ALLOWED) {
            networkMetrics.recordRateLimitRejected(
                    "chat_" + command.getChannel().name());
            SendChatMessageResponse response = SendChatMessageResponse.newBuilder()
                    .setOutcome(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED)
                    .build();
            send(ctx, Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE, sequenceId, response.toByteArray());
            if (verdict == ChatRateLimits.Verdict.REJECTED_AND_DISCONNECT) {
                LOG.warn("Chat vượt rate limit liên tục — ngắt kết nối: characterId={}", characterId);
                ctx.close();
            }
            return;
        }
        // Tuần tự theo kết nối (dòng 71(f)) — xem SerialExecutor.
        executeBlockingOn(
                chatExecutor,
                ctx,
                sequenceId,
                () -> handleSendChatMessageBlocking(ctx, sequenceId, characterId, command));
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
            Optional<Character> found = resolvePrivateTarget(ctx, sequenceId, characterId, command.getTargetName());
            if (found.isEmpty()) {
                return;
            }
            target = found.get();
        }
        if (!isChannelMembershipValid(characterId, domainChannel, ctx, sequenceId)) {
            return;
        }
        // Một dòng duy nhất (quyết định chủ dự án 2026-10-11) — và từ đây chỉ dùng `body` này: thứ được
        // kiểm phải đúng là thứ được phát đi.
        String body = dev.kage.chat.domain.ChatMessage.singleLine(command.getBody());
        java.util.OptionalLong recipientId =
                target == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(target.id());
        Optional<LinkedItemView> linked = linkedItemOf(ctx, sequenceId, characterId, command);
        if (linked.isEmpty()) {
            return;
        }
        Long accountId = boundAccountId;
        if (accountId != null && chatFacade.isMuted(accountId, java.time.Instant.now())) {
            // ADR-052 D4/D7 — khoá chat của GM; outcome MUTED (chỉ thêm, protocolVersion không tăng).
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MUTED);
            return;
        }
        ChatFacade.SendResult result = chatFacade.sendMessage(characterId, domainChannel, body, recipientId);
        if (result == ChatFacade.SendResult.OK
                && target != null
                && !worldGateway.registry().isOnline(target.id())) {
            // ADR-187 — người nhận offline: tin đã qua kiểm duyệt + audit ở trên, giữ lại giao khi họ vào game.
            sendChatOutcome(ctx, sequenceId, expansion.lot32().offlineWhispers().store(characterId, target.id(), body));
            return;
        }
        sendChatOutcome(ctx, sequenceId, toProtoOutcome(result));
        if (result != ChatFacade.SendResult.OK) {
            return;
        }
        String senderName =
                characterFacade.findById(characterId).map(Character::name).orElse("");
        routeChatMessage(domainChannel, new ChatRouteContext(characterId, senderName, target, body, linked.get()));
    }

    /**
     * Người nhận tin riêng theo tên — rỗng (và đã báo lỗi cho người gửi) nếu không có tên đó hoặc hai người chặn nhau.
     * Người nhận offline KHÔNG bị từ chối nữa (ADR-187): tin được giữ lại sau khi qua kiểm duyệt.
     */
    private Optional<Character> resolvePrivateTarget(
            ChannelHandlerContext ctx, int sequenceId, long characterId, String targetName) {
        Optional<Character> found = characterFacade.findByName(targetName);
        if (found.isEmpty()) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND);
            return Optional.empty();
        }
        if (relations.blocks().eitherBlocks(characterId, found.get().id())) {
            sendError(ctx, sequenceId, BlockCommands.BLOCKED_CODE, BlockCommands.BLOCKED_KEY);
            return Optional.empty();
        }
        return found;
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
        if (domainChannel == dev.kage.chat.domain.ChatChannel.SWORN
                && relations.swornMembersOf(characterId).isEmpty()) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_SWORN);
            return false;
        }
        return true;
    }

    /**
     * Gói tham số cho {@link #routeChatMessage} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}.
     * {@code linkedItem} = món khoe (ADR-188), {@code LinkedItemView.getDefaultInstance()} khi không khoe.
     */
    private record ChatRouteContext(
            long characterId, String senderName, Character target, String body, LinkedItemView linkedItem) {}

    /**
     * ADR-188 — món khoe của tin: {@code Optional.of(mặc định)} khi không khoe; rỗng (và đã báo người gửi) khi món
     * không có trong túi hoặc khoe ở kênh Khu vực (tin Khu vực đi qua world tick, không mang món).
     */
    private Optional<LinkedItemView> linkedItemOf(
            ChannelHandlerContext ctx, int sequenceId, long characterId, SendChatMessageCommand command) {
        if (command.getLinkedItemId() == 0L) {
            return Optional.of(LinkedItemView.getDefaultInstance());
        }
        if (command.getChannel() == ChatChannel.CHAT_CHANNEL_AREA) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LINKED_ITEM_NOT_ON_AREA);
            return Optional.empty();
        }
        Optional<LinkedItemView> linked = expansion.lot32().itemLinks().resolve(characterId, command.getLinkedItemId());
        if (linked.isEmpty()) {
            sendChatOutcome(ctx, sequenceId, SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LINKED_ITEM_NOT_FOUND);
        }
        return linked;
    }

    /** Định tuyến người nhận SAU KHI {@code ChatFacade.sendMessage} đã trả OK — tách khỏi
     * {@code handleSendChatMessageBlocking} để giữ method đó dưới giới hạn Checkstyle MethodLength. */
    private void routeChatMessage(dev.kage.chat.domain.ChatChannel domainChannel, ChatRouteContext ctx) {
        switch (domainChannel) {
            case WORLD -> {
                worldGateway
                        .registry()
                        .broadcastToAll(
                                Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                                chatEvent(ChatChannel.CHAT_CHANNEL_WORLD, ctx).toByteArray());
                // ADR-122 — chấm SAU khi tin đã phát: câu trả lời đúng vẫn hiện trên kênh như mọi tin khác.
                worldGateway.registry().quiz.onWorldChat(ctx.characterId(), ctx.senderName(), ctx.body());
            }
            case PRIVATE -> {
                worldGateway
                        .registry()
                        .sendToCharacterIfOnline(
                                ctx.target().id(),
                                Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                                0,
                                chatEvent(ChatChannel.CHAT_CHANNEL_PRIVATE, ctx).toByteArray());
                autoReplyIfAway(ctx); // ADR-178
            }
            // RỖNG nếu người gửi vừa rớt mạng trong lúc tin còn đang qua kiểm duyệt (2026-10-10) — không
            // còn đứng ở đâu để ai "gần đó" nghe thấy; bỏ tin, không ném (xem applyEquipmentBonusAndComputeMaxHp).
            case AREA ->
                currentWorldFacadeIfPresent(ctx.characterId())
                        .ifPresent(facade ->
                                facade.enqueueAreaChatMessage(ctx.characterId(), ctx.senderName(), ctx.body()));
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
            // ADR-110 — huynh đệ kết nghĩa đang online (gồm người gửi).
            case SWORN ->
                broadcastToGroup(ChatChannel.CHAT_CHANNEL_SWORN, ctx, relations.swornMembersOf(ctx.characterId()));
            default -> throw new IllegalStateException("ChatChannel không xử lý: " + domainChannel);
        }
    }

    /** ADR-062 D3 — danh hiệu đang đeo của người gửi, từ bộ nhớ (không DB mỗi tin chat). */
    private String titleKeyOf(long characterId) {
        return worldGateway.registry().achievements.titleKeyOf(characterId);
    }

    /** ADR-178 — người nhận tin riêng đang treo "vắng" ⇒ gửi lại lời nhắn của họ cho người gửi (có nhịp chặn lặp). */
    private void autoReplyIfAway(ChatRouteContext ctx) {
        expansion.lot31().away().replyFor(ctx.target().id(), ctx.characterId()).ifPresent(message -> worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        ctx.characterId(),
                        Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                        0,
                        ChatMessageEvent.newBuilder()
                                .setChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)
                                .setSenderId(ctx.target().id())
                                .setSenderName(ctx.target().name())
                                .setBody(message)
                                .setAutoReply(true)
                                .build()
                                .toByteArray()));
    }

    /** Một tin chat của người chơi, kèm danh hiệu đang đeo (ADR-062 D3). */
    private ChatMessageEvent chatEvent(ChatChannel protoChannel, ChatRouteContext ctx) {
        ChatMessageEvent.Builder event = ChatMessageEvent.newBuilder()
                .setChannel(protoChannel)
                .setSenderId(ctx.characterId())
                .setSenderName(ctx.senderName())
                .setSenderTitleKey(titleKeyOf(ctx.characterId()))
                .setBody(ctx.body());
        if (ctx.linkedItem().getItemTemplateId() != 0L) {
            event.setLinkedItem(ctx.linkedItem()); // ADR-188 — chỉ gắn khi thật sự khoe (tin thường không thêm byte)
        }
        return event.build();
    }

    private void broadcastToGroup(ChatChannel protoChannel, ChatRouteContext ctx, List<Long> memberCharacterIds) {
        ChatMessageEvent event = chatEvent(protoChannel, ctx);
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
            case CHAT_CHANNEL_SWORN -> Optional.of(dev.kage.chat.domain.ChatChannel.SWORN);
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
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2, EquipmentSlotType.ACCESSORY_2,
            EquipmentSlot.EQUIPMENT_SLOT_MOUNT, EquipmentSlotType.MOUNT,
            EquipmentSlot.EQUIPMENT_SLOT_PET, EquipmentSlotType.PET,
            EquipmentSlot.EQUIPMENT_SLOT_ARTIFACT, EquipmentSlotType.ARTIFACT);

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

    /** Danh sách nhân vật kèm mốc xoá hẳn của nhân vật đang chờ xoá (ADR-205). Chạm DB. */
    List<CharacterSummary> characterSummaries(long accountId) {
        Map<Long, java.time.Instant> deletions =
                expansion.lot34().facades().deletion().deletionTimes(accountId, java.time.Instant.now());
        return characterFacade.listCharacters(accountId).stream()
                .map(character -> {
                    java.time.Instant at = deletions.get(character.id());
                    return at == null
                            ? toProto(character)
                            : toProto(character).toBuilder()
                                    .setDeletionEpochSeconds(at.getEpochSecond())
                                    .build();
                })
                .toList();
    }

    private static CharacterSummary toProto(Character character) {
        return CharacterSummary.newBuilder()
                .setId(character.id())
                .setName(character.name())
                .setFaction(toProtoFaction(character.faction()))
                .setLevel(character.level())
                .build();
    }

    /** Cấp hiện tại trong DB (ADR-132 — bậc trang phục theo cảnh giới); không tìm thấy thì Cấp 1. */
    private int levelOf(long characterId) {
        return characterFacade
                .findById(characterId)
                .map(dev.kage.character.domain.Character::level)
                .orElse(1);
    }

    private static Faction toProtoFaction(dev.kage.character.domain.Faction faction) {
        return FactionMapping.toProto(faction);
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
    private void executeBlocking(ChannelHandlerContext ctx, int sequenceId, Runnable handler) {
        executeBlockingOn(authExecutor, ctx, sequenceId, handler);
    }

    /** {@link #executeBlocking} trên một executor chọn sẵn — chat dùng {@link #chatExecutor}. */
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "FutureReturnValueIgnored"})
    private void executeBlockingOn(Executor executor, ChannelHandlerContext ctx, int sequenceId, Runnable handler) {
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
        executor.execute(() -> {
            try {
                handler.run();
            } catch (CommandRefusedException e) {
                // Từ chối nghiệp vụ cắt ngang nhiều lệnh (ví dụ đã chặn nhau — ADR-091), không phải sự cố.
                sendError(ctx, sequenceId, e.code(), e.errorKey());
            } catch (dev.kage.server.database.GoldCapExceededException e) {
                // Trần vàng (quyết định chủ dự án 2026-10-11): bán / lĩnh thư / giao dịch sẽ đẩy số dư
                // của một bên vượt trần ⇒ transaction đã rollback trọn, và đây là một từ chối nghiệp vụ
                // có câu riêng, không phải sự cố server.
                LOG.info("Từ chối lệnh vì vượt trần vàng: sequenceId={}, {}", sequenceId, e.getMessage());
                sendError(ctx, sequenceId, 409, "error.gold_cap_reached");
            } catch (RuntimeException e) {
                LOG.error("Handler lỗi ngoài dự kiến: sequenceId={}", sequenceId, e);
                sendError(ctx, sequenceId, 500, "error.server_internal");
            }
        });
    }

    /**
     * {@code idempotencyKey} không phải UUID ⇒ trả 400 {@code error.invalid_payload} ngay trên
     * EventLoop, KHÔNG đẩy sang {@code authExecutor} (sửa 2026-10-02).
     *
     * <p>Trước đó chuỗi bẩn đi thẳng tới {@code UUID.fromString} trong adapter idempotency của
     * {@code server-database}, NỔ {@link IllegalArgumentException} giữa transaction — và mỗi handler
     * hiểu exception đó một kiểu: tạo nhân vật báo {@code error.invalid_character_name} (nhánh bắt
     * IAE vốn dành cho tên), còn cường hoá/mua/bán/nhận thưởng/giao dịch/nhận thư rơi vào lưới cuối
     * {@code error.server_internal} 500. Định dạng khoá là hợp đồng dây (ADR-025: client sinh UUID),
     * nên kiểm ở biên dây — một chỗ cho cả bảy lệnh.
     *
     * @return {@code true} nếu đã từ chối (caller dừng lại)
     */
    private boolean rejectMalformedIdempotencyKey(ChannelHandlerContext ctx, int sequenceId, String idempotencyKey) {
        if (IdempotencyKeyFormat.isWellFormed(idempotencyKey)) {
            return false;
        }
        sendError(ctx, sequenceId, 400, "error.invalid_payload");
        return true;
    }

    /**
     * Lệnh kinh tế sai HÌNH DẠNG (số lượng/vàng/id không dương) ⇒ 400 {@code error.invalid_payload}
     * trên EventLoop, không chạm DB (sửa 2026-10-03) — trước đó use case ném
     * {@link IllegalArgumentException} và lưới cuối của {@link #executeBlocking} báo 500. Xem
     * {@link EconomyCommandShape}.
     *
     * @return {@code true} nếu đã từ chối (caller dừng lại)
     */
    private boolean rejectMalformedShape(ChannelHandlerContext ctx, int sequenceId, boolean wellFormed) {
        if (wellFormed) {
            return false;
        }
        sendError(ctx, sequenceId, 400, "error.invalid_payload");
        return true;
    }

    private void sendError(ChannelHandlerContext ctx, int relatedSequenceId, int errorCode, String errorKey) {
        ErrorResponse error = ErrorResponse.newBuilder()
                .setErrorCode(errorCode)
                .setErrorKey(errorKey)
                .setRelatedSequenceId(relatedSequenceId)
                .build();
        send(ctx, Opcode.OPCODE_ERROR_VALUE, relatedSequenceId, error.toByteArray());
    }

    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
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
        CharacterBinding bound = binding;
        if (bound != null) {
            releaseCharacter(ctx, bound);
        }
        revokeSessionOnClose();
    }

    /**
     * Thu hồi session token khi kết nối đóng (quyết định chủ dự án 2026-10-11) — xem
     * {@code LoginOrCreateAccountUseCase#endSession}. Chạm Redis nên đẩy sang {@code authExecutor}.
     *
     * <p>Thu hồi hỏng không phải sự cố: token vẫn tự hết theo TTL, nên chỉ ghi WARN. Server đang tắt
     * thì executor đã đóng — bỏ qua, cùng lý do.
     */
    // PMD AvoidCatchingGenericException: ranh giới của một task nền chạy sau khi kết nối đã đóng —
    // không còn ai để báo lỗi, và mọi lỗi Redis đều có cùng một cách xử lý (để TTL lo).
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void revokeSessionOnClose() {
        String token = sessionToken;
        if (token == null) {
            return;
        }
        sessionToken = null;
        try {
            authExecutor.execute(() -> {
                try {
                    accountFacade.logout(token);
                } catch (RuntimeException e) {
                    LOG.warn("Thu hồi session token lúc ngắt kết nối thất bại — token sẽ tự hết theo TTL", e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            LOG.debug("Bỏ thu hồi session token: server đang tắt", e);
        }
    }

    /**
     * {@link CharacterSessionRegistry.Session#evict} của kết nối này — gọi từ Virtual Thread của
     * kết nối MỚI vừa chọn cùng nhân vật (quyết định chủ dự án 2026-09-25: phiên mới đá phiên cũ).
     *
     * <p>Việc đá chạy trên EventLoop của CHÍNH kết nối này, không chạy tại chỗ: mọi lệnh gameplay
     * của kết nối này cũng chạy ở đó, nên sau khi lượt đá chạy xong không lệnh nào của nó còn lọt
     * được vào world (lệnh tới sau thấy {@code boundCharacterId == null}). Chạy tại chỗ thì một lệnh
     * di chuyển đang dở trên EventLoop vẫn có thể xếp vào world SAU lệnh detach — tức điều khiển
     * được thực thể của phiên mới.
     */
    private CompletableFuture<Void> evictCharacter(long characterId, String errorKey) {
        ChannelHandlerContext ctx = boundContext;
        CharacterBinding bound = binding;
        if (ctx == null || bound == null || bound.characterId() != characterId) {
            // Kết nối này không còn gắn nhân vật đó: đã rời sang nhân vật khác (lần dọn chạy dưới khoá
            // của nhân vật, xem releasePreviousCharacterIfSwitching) hoặc chưa từng vào world — không
            // có gì để đá, và TUYỆT ĐỐI không đóng một kết nối đang chơi nhân vật khác.
            return CompletableFuture.completedFuture(null);
        }
        if (bound.released().get()) {
            // Đã/đang dọn (kết nối vừa chết) — chỉ cần chờ lần dọn đó, không cần đá.
            return bound.completed();
        }
        runOnEventLoop(ctx, () -> kick(ctx, bound, errorKey));
        return bound.completed();
    }

    /**
     * Thôi gắn {@code bound} (nếu nó vẫn là lần gắn hiện tại) rồi dọn nó — gọi trên EventLoop.
     *
     * @return {@code true} nếu {@code bound} vẫn là lần gắn hiện tại của kết nối
     */
    // Error Prone ReferenceEquality + PMD CompareObjectsWithEquals: đúng ý — "cùng lần gắn" là cùng
    // đối tượng; hai lần gắn cùng một nhân vật là HAI lần khác nhau.
    @SuppressWarnings({"ReferenceEquality", "PMD.CompareObjectsWithEquals"})
    private boolean unbindAndRelease(ChannelHandlerContext ctx, CharacterBinding bound) {
        boolean current = binding == bound;
        if (current) {
            binding = null;
            boundCharacterId = null;
        }
        releaseCharacter(ctx, bound);
        return current;
    }

    /** Lượt đá, trên EventLoop của kết nối bị đá — xem {@link #evictCharacter}. */
    private void kick(ChannelHandlerContext ctx, CharacterBinding bound, String errorKey) {
        long characterId = bound.characterId();
        LOG.info("Nhân vật đăng nhập từ kết nối khác — đá phiên cũ: characterId={}", characterId);
        if (!unbindAndRelease(ctx, bound)) {
            // Kết nối đã rời nhân vật này sang nhân vật khác — dọn là đủ, không đóng kết nối.
            return;
        }
        ErrorResponse error = ErrorResponse.newBuilder()
                .setErrorCode(ERROR_CODE_SESSION_CONFLICT)
                .setErrorKey(errorKey)
                .build();
        byte[] payload = error.toByteArray();
        networkMetrics.recordPacketSent(Opcode.OPCODE_ERROR_VALUE, payload.length);
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ERROR_VALUE, ProtocolVersion.CURRENT, 0);
        // Đóng SAU khi gói lý do đã ghi ra dây — đóng ngay thì client chỉ thấy "mất kết nối".
        ctx.writeAndFlush(new PacketCodec.Frame(header, payload)).addListener(ChannelFutureListener.CLOSE);
    }

    // Error Prone FutureReturnValueIgnored: đúng ý — kết quả của chuỗi allOf(...).whenComplete KHÔNG
    // cần ai đọc; mọi lỗi của các bước đã được finishRelease log và mốc hoàn tất thật là
    // CharacterBinding#completed.
    /**
     * Dọn nhân vật khỏi world khi kết nối chết — gọi từ {@code channelInactive} (EventLoop), từ
     * {@link #kick} (EventLoop, phiên bị kết nối khác đá — 2026-09-25), từ
     * {@link #releasePreviousCharacterIfSwitching} (EventLoop, kết nối đổi sang nhân vật khác) HOẶC từ
     * {@link #bindOrReleaseIfClosed} (Virtual Thread) nếu kết nối chết giữa lúc chọn nhân vật. Chạy
     * ĐÚNG MỘT LẦN mỗi lần gắn: nhiều phía có thể cùng thấy kết nối chết.
     *
     * <p>Các bước chạm DB/world chạy bất đồng bộ; khi TẤT CẢ xong, {@link #finishRelease} trả quyền
     * giữ phiên và hoàn tất {@link CharacterBinding#completed} — mốc mà phiên mới đang chờ.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void releaseCharacter(ChannelHandlerContext ctx, CharacterBinding bound) {
        if (!bound.released().compareAndSet(false, true)) {
            return;
        }
        long characterId = bound.characterId();
        // World nhân vật đang ở — hỏi TRƯỚC khi detach (sau đó nó không thuộc World nào nữa). Mốc chặn
        // hàng đợi của đúng World này là "mọi lệnh cũ đã xử lý", xem WorldFacade#inboxBarrier.
        Optional<WorldFacade> world = currentWorldFacadeIfPresent(characterId);
        // Đọc vị trí cuối TRƯỚC unregister() (unregister xoá luôn khỏi lastKnownPosition —
        // đọc ở đây trên EventLoop, rẻ, không phải I/O, không tra lại sau trên authExecutor
        // vì lúc đó có thể đã bị unregister() xoá mất — xem javadoc CharacterPositionPersister#save).
        PositionSnapshot lastPosition = worldGateway.registry().lastKnownPosition(characterId);
        onLeftWorld(characterId);
        // CÓ ĐIỀU KIỆN theo kết nối: nếu nhân vật đã thuộc kết nối khác thì không đụng tới.
        if (!worldGateway.registry().unregister(characterId, ctx)
                && worldGateway.registry().isOnline(characterId)) {
            // Phiên MỚI đã nhận nhân vật (dòng 73(e), chủ dự án duyệt 2026-10-17): lần dọn này tới muộn —
            // phiên mới chỉ chờ nó tối đa EVICTION_TIMEOUT. Thực thể trong World, vị trí và trạng thái
            // online giờ là của phiên mới: gỡ/lưu/báo offline ở đây là xoá nhân vật đang chơi khỏi world.
            LOG.warn("Dọn phiên cũ tới sau khi phiên mới đã vào — bỏ phần gỡ world: characterId={}", characterId);
            finishRelease(bound, null);
            return;
        }
        Detached detached = detachAfterDisconnect(characterId);
        boolean handledElsewhere = detached.handledElsewhere();
        AtomicReference<PositionSnapshot> finalPosition = detached.finalPosition();
        CompletableFuture<Void> worldDrained =
                world.map(WorldFacade::inboxBarrier).orElseGet(() -> CompletableFuture.completedFuture(null));
        // SERVER.md §8: logout là 1 trong 3 mốc bắt buộc ghi vị trí. savePosition chạm DB
        // (blocking) — không được chạy trên EventLoop, đẩy sang authExecutor.
        // join() trên Virtual Thread của authExecutor: chờ mốc hàng đợi (lệnh gỡ đã xử lý ⇒ finalPosition
        // đã có, hoặc World đã dừng ⇒ dùng mẫu cũ), rồi chờ save() — nó chỉ XẾP lượt ghi vào luồng của
        // persister (xem javadoc của nó về thứ tự với lượt định kỳ). Việc được NỘP ngay bây giờ chứ không
        // nối sau mốc: lúc tắt server authExecutor đóng ngay sau event loop, một việc nộp muộn bị từ chối.
        CompletableFuture<Void> positionSaved = lastPosition == null && handledElsewhere
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.runAsync(
                        () -> saveFinalPosition(characterId, worldDrained, finalPosition, lastPosition), authExecutor);
        // channelInactive chạy trên EventLoop — friendFacade.listFriends chạm DB (blocking),
        // PHẢI đẩy sang authExecutor, không được gọi thẳng ở đây (server/CLAUDE.md: EventLoop
        // cấm query DB), cùng lý do savePosition ở trên.
        CompletableFuture<Void> friendsNotified =
                CompletableFuture.runAsync(() -> notifyFriendsPresenceChanged(characterId, false), authExecutor);
        handOverPartyLeadershipAfterDisconnect(characterId);
        // ADR-095/096 — mốc thoát cho bế quan + giây online của phiên; chạm DB nên lên authExecutor.
        CompletableFuture<Void> dailyRecorded =
                CompletableFuture.runAsync(() -> recordLeftWorld(characterId), authExecutor);
        CompletableFuture.allOf(positionSaved, worldDrained, friendsNotified, dailyRecorded)
                .whenComplete((ignored, error) -> finishRelease(bound, error));
    }

    /** Kết quả của {@link #detachAfterDisconnect}. */
    private record Detached(boolean handledElsewhere, AtomicReference<PositionSnapshot> finalPosition) {}

    /**
     * Gỡ nhân vật vừa mất kết nối khỏi World nó đang đứng — tách khỏi {@link #releaseCharacter} vì
     * Checkstyle {@code MethodLength}.
     */
    private Detached detachAfterDisconnect(long characterId) {
        // Phase 8 lát cắt 1 (ADR-026): nếu đang ở dungeon, detach khỏi ĐÚNG dungeon World +
        // dọn bookkeeping instance (dừng luôn World nếu rỗng) — KHÔNG re-attach world chính
        // (đã ngắt kết nối). Lát cắt 4: tương tự cho duel Arena — người mất kết nối tính THUA
        // (forfeit ngầm, ArenaInstanceRegistry tự chốt rating). false ở CẢ 2 = không ở dungeon
        // lẫn duel nào, detach world chính như trước giờ.
        // Lời thách đấu đang chờ của/tới người này không còn nghĩa khi họ đã mất kết nối (2026-10-11).
        worldGateway.arenaInstanceRegistry().forgetChallengesOf(characterId);
        boolean handledElsewhere = worldGateway.dungeonInstanceRegistry().handleDisconnectIfInDungeon(characterId)
                || worldGateway.arenaInstanceRegistry().handleDisconnectIfInDuel(characterId);
        // Vị trí THẬT lúc World gỡ nhân vật (dòng 76(a), 2026-10-17) — mẫu 1 Hz ở lastPosition có thể cũ
        // tới ~5 m nếu đang chạy. Chỉ world chính: hầm ngục/đấu trường vốn giữ chỗ đứng ở world chính.
        AtomicReference<PositionSnapshot> finalPosition = new AtomicReference<>();
        if (!handledElsewhere) {
            worldGateway
                    .mainWorldChannels()
                    .detachCharacter(characterId, (x, y) -> finalPosition.set(new PositionSnapshot(x, y)));
        }
        return new Detached(handledElsewhere, finalPosition);
    }

    /** Thân việc lưu vị trí của {@link #releaseCharacter} — chạy trên {@code authExecutor}. */
    private void saveFinalPosition(
            long characterId,
            CompletableFuture<Void> worldDrained,
            AtomicReference<PositionSnapshot> finalPosition,
            PositionSnapshot sampledPosition) {
        worldDrained.join();
        PositionSnapshot position = finalPosition.get() != null ? finalPosition.get() : sampledPosition;
        if (position != null) {
            worldGateway
                    .positionPersister()
                    .save(characterId, position.x(), position.y())
                    .join();
        }
    }

    /**
     * Hai việc dọn "quan hệ với người khác" sau khi một nhân vật mất kết nối (quyết định chủ dự án
     * 2026-10-11), đều chạm DB nên chạy trên {@code authExecutor}:
     *
     * <ul>
     *   <li>trưởng nhóm thì thành viên vào sớm nhất đang online lên thay — xem
     *       {@code HandOverPartyLeadershipUseCase};
     *   <li>phiên giao dịch đang mở thì HUỶ, và bên kia được báo. Trước đó phiên treo lại: bên kia ngồi
     *       trước một khung giao dịch không ai trả lời, và phiên sống tới khi một trong hai tự huỷ.
     * </ul>
     *
     * <p>KHÔNG nằm trong mốc hoàn tất của lần dọn: phiên mới của cùng nhân vật không phải chờ việc này.
     * Vì thế hỏi lại "còn online không" ngay trước khi làm — bị phiên mới của CHÍNH nhân vật này đá thì
     * họ vẫn đang chơi, không phải rớt mạng: không mất quyền trưởng nhóm, không mất phiên giao dịch.
     */
    @SuppressWarnings("FutureReturnValueIgnored") // lỗi của từng việc đã được bắt và log ở bên trong
    private void handOverPartyLeadershipAfterDisconnect(long characterId) {
        try {
            CompletableFuture.runAsync(() -> settleSocialStateAfterDisconnect(characterId), authExecutor);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            LOG.debug("Bỏ dọn nhóm/giao dịch sau ngắt kết nối: server đang tắt", e);
        }
    }

    // PMD AvoidCatchingGenericException: ranh giới task nền sau khi kết nối đã đóng — không còn ai để
    // báo lỗi, và việc này hỏng thì mọi thứ chỉ ở lại đúng trạng thái trước 2026-10-11. Hai việc bắt
    // lỗi RIÊNG: việc này hỏng không được kéo theo việc kia.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void settleSocialStateAfterDisconnect(long characterId) {
        ConnectedCharacterRegistry registry = worldGateway.registry();
        if (registry.isOnline(characterId)) {
            return;
        }
        try {
            partyFacade.handOverLeadership(characterId, registry::isOnline).ifPresent(newLeader -> membershipNotices()
                    .partyLeaderChanged(newLeader, partyMemberIds(newLeader)));
        } catch (RuntimeException e) {
            LOG.warn("Chuyển quyền trưởng nhóm lúc ngắt kết nối thất bại: characterId={}", characterId, e);
        }
        try {
            cancelOpenTradeOf(characterId);
        } catch (RuntimeException e) {
            LOG.warn("Huỷ giao dịch lúc ngắt kết nối thất bại: characterId={}", characterId, e);
        }
    }

    /** Huỷ phiên giao dịch đang mở của {@code characterId} (nếu có) và báo bên kia — cùng gói với lệnh Huỷ. */
    private void cancelOpenTradeOf(long characterId) {
        Optional<TradeFacade.TradeStatusView> status = tradeFacade.getStatus(characterId);
        if (status.isEmpty()
                || tradeFacade.cancel(status.get().sessionId(), characterId) != TradeFacade.CancelResult.OK) {
            return;
        }
        TradeClosedEvent event = TradeClosedEvent.newBuilder()
                .setSessionId(status.get().sessionId())
                .setReason(TradeCloseReason.TRADE_CLOSE_REASON_CANCELLED)
                .build();
        worldGateway
                .registry()
                .sendToCharacterIfOnline(
                        status.get().counterpartCharacterId(),
                        Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE,
                        0,
                        event.toByteArray());
    }

    /** Mốc rời world: lần cuối online (ADR-192), rồi mốc thoát bế quan + giây online của phiên (ADR-095/096). */
    private void recordLeftWorld(long characterId) {
        expansion.lot33().facades().lastOnline().markLeft(characterId, java.time.Instant.now());
        daily.onLeftWorld(characterId);
    }

    /** Trạng thái chỉ-trong-phiên phải bỏ khi rời world: tiến độ thành tựu đang đếm, lời nhắn vắng (ADR-178). */
    private void onLeftWorld(long characterId) {
        worldGateway.registry().achievements.onLeftWorld(characterId);
        expansion.lot31().away().clear(characterId);
        worldGateway.registry().deathRecap.forget(characterId); // ADR-195
    }

    /**
     * Bước cuối của lần dọn: thôi giữ phiên và báo cho phiên mới (nếu có ai đang chờ) rằng đã xong.
     * Luôn chạy, kể cả khi một bước trước lỗi — một lần ghi vị trí hỏng không được khoá người chơi
     * ngoài cửa.
     */
    private void finishRelease(CharacterBinding bound, Throwable error) {
        long characterId = bound.characterId();
        if (error != null) {
            LOG.warn("Dọn nhân vật có bước lỗi: characterId={}", characterId, error);
        }
        worldGateway.registry().sessions().release(characterId, session);
        bound.completed().complete(null);
    }

    /**
     * Một lần gắn nhân vật vào kết nối: cờ "đã dọn" (dọn đúng một lần dù nhiều phía cùng thấy kết nối
     * chết) và mốc "dọn xong trọn" (phiên mới của cùng nhân vật chờ mốc này, xem
     * {@link CharacterSessionRegistry.Session#evict}).
     */
    private record CharacterBinding(long characterId, AtomicBoolean released, CompletableFuture<Void> completed) {

        CharacterBinding(long characterId) {
            this(characterId, new AtomicBoolean(), new CompletableFuture<>());
        }
    }

    /**
     * Lời báo "online" vừa gửi có thể đã LỖI THỜI (2026-10-02, {@code FriendPresenceOrderTest}): giữa
     * lúc vào world và lúc gửi nó có vài lần đọc DB, và kết nối rớt đúng khe đó thì lần dọn của
     * {@code channelInactive} báo "offline" trên luồng khác — có thể tới bạn bè TRƯỚC lời "online".
     * Bạn bè thấy người đã thoát vẫn online cho tới lần họ đăng nhập lại.
     *
     * <p>Kiểm lại SAU khi đã gửi: nhân vật không còn đăng ký ⇒ lần dọn đã chạy (gỡ đăng ký đứng trước
     * lời báo offline của nó), báo offline lần nữa để lời cuối cùng đúng. Còn đăng ký ⇒ lần dọn (nếu
     * có) chưa gỡ, nên lời offline của nó chắc chắn đi SAU lời online đã gửi. Kiểm theo "còn đăng ký"
     * chứ không theo kết nối này: bị phiên mới ĐÁ thì phiên mới đã đăng ký và tự báo online — không
     * được chen lời offline vào sau nó. Kiểm + báo dưới khoá của nhân vật vì lý do đó: phiên mới đăng
     * ký dưới đúng khoá này ({@link #enterWorldAsOnlySession}) và chỉ báo online SAU khi nhả nó, nên
     * hoặc ta chạy trước (lời online của nó tới sau lời offline này), hoặc ta thấy nó đã đăng ký.
     */
    private void correctStaleOnlineAnnouncement(long characterId) {
        ReentrantLock lock = worldGateway.registry().sessions().lockFor(characterId);
        lock.lock();
        try {
            if (!worldGateway.registry().isOnline(characterId)) {
                notifyFriendsPresenceChanged(characterId, false);
            }
        } finally {
            lock.unlock();
        }
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

    /**
     * Hết hạn im lặng (NETWORK.md §10, {@code GatewayServer#READER_IDLE_TIMEOUT}) ⇒ đóng kết nối;
     * {@link #channelInactive} dọn nhân vật như mọi lần ngắt khác.
     */
    @Override
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent idle && idle.state() == IdleState.READER_IDLE) {
            LOG.info(
                    "Kết nối im lặng quá lâu — đóng: remote={}, characterId={}",
                    ctx.channel().remoteAddress(),
                    boundCharacterId);
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * Client không đọc phản hồi (hàng đợi ghi vượt high water mark) ⇒ NGỪNG đọc lệnh mới của nó cho
     * tới khi nó đọc kịp. Trước 2026-10-02 không có chặn này: mỗi Ping/lệnh sai đều đẻ một phản hồi,
     * và một client gửi liên tục mà không bao giờ đọc làm hàng đợi ghi của server phình không giới
     * hạn trong heap. Ngừng đọc thì lệnh dồn lại ở bộ đệm TCP của CHÍNH client đó; nếu nó không đọc
     * lại trong {@code READER_IDLE_TIMEOUT}, {@link #userEventTriggered} đóng kết nối.
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        ctx.channel().config().setAutoRead(ctx.channel().isWritable());
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    // FutureReturnValueIgnored: ChannelFuture của close()/writeAndFlush() — lỗi đi qua pipeline Netty.
    @SuppressWarnings("FutureReturnValueIgnored")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Lỗi giải mã (khung khai quá trần, khung ngắn hơn header, bắt tay TLS hỏng) là CLIENT gửi rác,
        // không phải server hỏng: một dòng WARN, không stack trace — cùng luật với gói hỏng (README
        // dòng 44(i)). Trước đó mỗi khung quá cỡ là một ERROR kèm ~60 dòng stack trace (kiểm sống
        // 2026-10-14), tức ai mở được kết nối là ghi được log ERROR tuỳ ý.
        if (cause instanceof DecoderException) {
            LOG.warn(
                    "Đóng kết nối {}: khung không giải mã được ({})",
                    ctx.channel().remoteAddress(),
                    abbreviate(String.valueOf(cause.getMessage())));
        } else {
            LOG.error("Lỗi không mong đợi trên kết nối {}", ctx.channel().remoteAddress(), cause);
        }
        ctx.close();
    }

    /** NotSslRecordException chép hex dump của MỌI byte nhận được vào message — client quyết độ dài. */
    private static String abbreviate(String message) {
        return message.length() <= MAX_LOGGED_DECODER_MESSAGE
                ? message
                : message.substring(0, MAX_LOGGED_DECODER_MESSAGE) + "…";
    }
}
