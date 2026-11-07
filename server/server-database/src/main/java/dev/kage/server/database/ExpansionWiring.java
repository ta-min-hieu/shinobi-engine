package dev.kage.server.database;

import java.time.LocalTime;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.chat.api.SectQuiz;
import dev.kage.pvp.api.TournamentFacade;
import dev.kage.pvp.application.TournamentDirector;
import dev.kage.pvp.domain.TournamentRules;
import dev.kage.server.database.chat.PostgresQuizAdapter;
import dev.kage.server.database.pvp.PostgresTournamentAdapter;
import dev.kage.server.database.world.PostgresArtifactAdapter;
import dev.kage.server.database.world.PostgresEncounterAdapter;
import dev.kage.server.database.world.PostgresSpiritFieldAdapter;
import dev.kage.server.database.world.PostgresTamingAdapter;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.api.TamingFacade;

/**
 * Dựng facade lô 23 phần 2–3 (ADR-119…126) — tách khỏi {@link ServerDatabaseBootstrap} vì lớp đó chạm trần độ phức tạp
 * PMD tính cho cả lớp. Lấy qua {@link ServerDatabaseBootstrap#expansion()}.
 */
public final class ExpansionWiring {

    private final DataSource dataSource;
    // ADR-158 — MỘT bản cho cả đường gieo quẻ (gateway) lẫn đường rơi đồ: bộ nhớ quẻ-theo-ngày phải là cùng một chỗ.
    private dev.kage.checkin.api.FortuneFacade fortune;

    ExpansionWiring(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Linh điền (ADR-119). */
    public SpiritFieldFacade wireSpiritFieldFacade() {
        return new SpiritFieldFacade(new PostgresSpiritFieldAdapter(dataSource));
    }

    /** Kỳ ngộ (ADR-121). */
    public EncounterFacade wireEncounterFacade() {
        return new EncounterFacade(new PostgresEncounterAdapter(dataSource));
    }

    /** Tàng Bảo Đồ (ADR-147). */
    public dev.kage.world.api.TreasureFacade wireTreasureFacade() {
        return new dev.kage.world.api.TreasureFacade(
                new dev.kage.server.database.world.PostgresTreasureAdapter(dataSource));
    }

    /** Dịch Danh Phù (ADR-148). */
    public dev.kage.character.api.RenameFacade wireRenameFacade() {
        return new dev.kage.character.api.RenameFacade(
                new dev.kage.server.database.character.PostgresRenameAdapter(dataSource));
    }

    /** Quẻ bói hằng ngày (ADR-149). */
    public synchronized dev.kage.checkin.api.FortuneFacade wireFortuneFacade() {
        if (fortune == null) {
            fortune = new dev.kage.checkin.api.FortuneFacade(
                    new dev.kage.server.database.checkin.PostgresFortuneAdapter(dataSource));
        }
        return fortune;
    }

    /** Vòng quay may mắn (ADR-151). */
    public dev.kage.checkin.api.LuckyWheelFacade wireLuckyWheelFacade() {
        return new dev.kage.checkin.api.LuckyWheelFacade(
                new dev.kage.server.database.checkin.PostgresLuckyWheelAdapter(dataSource));
    }

    /** Quà mốc cảnh giới (ADR-153). */
    public dev.kage.character.api.MilestoneFacade wireMilestoneFacade() {
        return new dev.kage.character.api.MilestoneFacade(
                new dev.kage.server.database.character.PostgresMilestoneAdapter(dataSource));
    }

    /** Nhiệm vụ tuần cá nhân (ADR-154). */
    public dev.kage.checkin.api.WeeklyTaskFacade wireWeeklyTaskFacade() {
        return new dev.kage.checkin.api.WeeklyTaskFacade(
                new dev.kage.server.database.checkin.PostgresWeeklyTaskAdapter(dataSource));
    }

    /** ADR-159 — khoá vật phẩm. */
    public dev.kage.inventory.api.ItemLockFacade wireItemLockFacade() {
        return new dev.kage.inventory.api.ItemLockFacade(
                new dev.kage.server.database.inventory.PostgresItemLockAdapter(dataSource));
    }

    /** ADR-160 — sắp xếp túi đồ. */
    public dev.kage.inventory.api.BagSortFacade wireBagSortFacade() {
        return new dev.kage.inventory.api.BagSortFacade(
                new dev.kage.server.database.inventory.PostgresBagSortAdapter(dataSource));
    }

    /** ADR-161 — ghép Linh Ngọc. */
    public dev.kage.inventory.api.GemCombineFacade wireGemCombineFacade() {
        return new dev.kage.inventory.api.GemCombineFacade(
                new dev.kage.server.database.inventory.PostgresGemCombineAdapter(dataSource));
    }

    /** ADR-162 — quét nhanh Bí Cảnh. */
    public dev.kage.world.api.DungeonSweepFacade wireDungeonSweepFacade() {
        return new dev.kage.world.api.DungeonSweepFacade(
                new dev.kage.server.database.world.PostgresDungeonSweepAdapter(dataSource),
                java.time.Clock.systemUTC());
    }

    /** ADR-163 — túi phúc. */
    public dev.kage.inventory.api.LuckyBagFacade wireLuckyBagFacade() {
        return new dev.kage.inventory.api.LuckyBagFacade(
                new dev.kage.server.database.inventory.PostgresLuckyBagAdapter(dataSource));
    }

    /** ADR-164 — đan x2 kinh nghiệm (đọc hạn). */
    public dev.kage.character.api.ExpBuffFacade wireExpBuffFacade() {
        return new dev.kage.character.api.ExpBuffFacade(
                new dev.kage.server.database.character.PostgresExpBuffAdapter(dataSource));
    }

    /** ADR-165 — sổ các đợt thư bồi thường. */
    public dev.kage.gm.api.CompensationFacade wireCompensationFacade() {
        return new dev.kage.gm.api.CompensationFacade(
                new dev.kage.server.database.gm.PostgresCompensationAdapter(dataSource));
    }

    /** ADR-166 — huyễn hình trang phục. */
    public dev.kage.character.api.CostumeFacade wireCostumeFacade() {
        return new dev.kage.character.api.CostumeFacade(
                new dev.kage.server.database.character.PostgresCostumeAdapter(dataSource));
    }

    /** ADR-168 — tháo Linh Ngọc. */
    public dev.kage.inventory.api.GemRemovalFacade wireGemRemovalFacade() {
        return new dev.kage.inventory.api.GemRemovalFacade(
                new dev.kage.server.database.inventory.PostgresGemRemovalAdapter(dataSource));
    }

    /** ADR-169 — sổ vàng cá nhân. */
    public dev.kage.character.api.GoldHistoryFacade wireGoldHistoryFacade() {
        return new dev.kage.character.api.GoldHistoryFacade(
                new dev.kage.server.database.character.PostgresGoldHistoryAdapter(dataSource));
    }

    /** ADR-170 — bán nhanh đồ rác. */
    public dev.kage.inventory.api.JunkSaleFacade wireJunkSaleFacade() {
        return new dev.kage.inventory.api.JunkSaleFacade(
                new dev.kage.server.database.inventory.PostgresJunkSaleAdapter(dataSource));
    }

    /** ADR-189 — lịch sử giao dịch. */
    public dev.kage.trade.api.TradeHistoryFacade wireTradeHistoryFacade() {
        return new dev.kage.trade.api.TradeHistoryFacade(
                new dev.kage.server.database.trade.PostgresTradeHistoryAdapter(dataSource));
    }

    /** ADR-205 — xoá nhân vật có chờ. */
    public dev.kage.character.api.CharacterDeletionFacade wireCharacterDeletionFacade() {
        return new dev.kage.character.api.CharacterDeletionFacade(
                new dev.kage.server.database.character.PostgresCharacterDeletionAdapter(dataSource));
    }

    /** ADR-204 — lọc nhặt đồ (tự bán đồ rác rơi ra). */
    public dev.kage.inventory.api.AutoSellJunkFacade wireAutoSellJunkFacade() {
        return new dev.kage.inventory.api.AutoSellJunkFacade(
                new dev.kage.server.database.inventory.PostgresAutoSellJunkAdapter(dataSource));
    }

    /** ADR-202 — mua lại đồ đã bán. */
    public dev.kage.shop.api.BuybackFacade wireBuybackFacade() {
        return new dev.kage.shop.api.BuybackFacade(
                new dev.kage.server.database.shop.PostgresBuybackAdapter(dataSource));
    }

    /** ADR-198 — kho bang: luật kho tông môn, adapter theo bang. */
    public dev.kage.inventory.api.GuildBankFacade wireGuildBankFacade() {
        return new dev.kage.inventory.api.GuildBankFacade(
                storageUseCase(new dev.kage.server.database.inventory.PostgresGuildStorageAdapter(dataSource)));
    }

    /** ADR-197 — kho chung tài khoản: luật kho tông môn, adapter theo tài khoản. */
    public dev.kage.inventory.api.AccountStorageFacade wireAccountStorageFacade() {
        return new dev.kage.inventory.api.AccountStorageFacade(
                storageUseCase(new dev.kage.server.database.inventory.PostgresAccountStorageAdapter(dataSource)));
    }

    /** Một {@code StorageUseCase} đủ cổng, cắm {@code port} làm nơi lưu (ADR-197/198). */
    private dev.kage.inventory.application.StorageUseCase storageUseCase(dev.kage.inventory.domain.StoragePort port) {
        return new dev.kage.inventory.application.StorageUseCase(
                new dev.kage.inventory.application.StorageUseCase.Ports(
                        new dev.kage.server.database.inventory.PostgresInventoryAdapter(dataSource),
                        new dev.kage.server.database.inventory.PostgresEquipmentAdapter(dataSource),
                        new dev.kage.server.database.inventory.PostgresItemTemplateAdapter(dataSource),
                        new dev.kage.server.database.inventory.PostgresInventoryTradeOfferAdapter()),
                port,
                new dev.kage.server.database.inventory.PostgresAuditAdapter(),
                new dev.kage.server.database.inventory.JdbcInventoryTransactionRunner(dataSource));
    }

    /** ADR-196 — theo dõi phường thị. */
    public dev.kage.market.api.MarketWatchFacade wireMarketWatchFacade() {
        return new dev.kage.market.api.MarketWatchFacade(
                new dev.kage.server.database.market.PostgresMarketWatchAdapter(dataSource));
    }

    /** ADR-194 — danh sách cừu nhân. */
    public dev.kage.pvp.api.EnemyListFacade wireEnemyListFacade() {
        return new dev.kage.pvp.api.EnemyListFacade(
                new dev.kage.server.database.pvp.PostgresEnemyListAdapter(dataSource));
    }

    /** ADR-193 — tên riêng linh thú. */
    public dev.kage.inventory.api.PetNameFacade wirePetNameFacade() {
        return new dev.kage.inventory.api.PetNameFacade(
                new dev.kage.server.database.inventory.PostgresPetNameAdapter(dataSource));
    }

    /** ADR-192 — lần cuối online. */
    public dev.kage.character.api.LastOnlineFacade wireLastOnlineFacade() {
        return new dev.kage.character.api.LastOnlineFacade(
                new dev.kage.server.database.character.PostgresLastOnlineAdapter(dataSource));
    }

    /** ADR-187 — nhắn riêng khi offline. */
    public dev.kage.chat.api.OfflineWhisperFacade wireOfflineWhisperFacade() {
        return new dev.kage.chat.api.OfflineWhisperFacade(
                new dev.kage.server.database.chat.PostgresOfflineWhisperAdapter(dataSource));
    }

    /** ADR-212 — chặn thư người lạ. */
    public dev.kage.mail.api.StrangerMailFacade wireStrangerMailFacade() {
        return new dev.kage.mail.api.StrangerMailFacade(
                new dev.kage.server.database.mail.PostgresStrangerMailAdapter(dataSource));
    }

    /** ADR-210 — chỉ số cộng của danh hiệu. */
    public dev.kage.achievement.api.TitleBonusFacade wireTitleBonusFacade() {
        return new dev.kage.achievement.api.TitleBonusFacade(
                new dev.kage.server.database.achievement.PostgresTitleBonusAdapter(dataSource));
    }

    /** ADR-208 — lịch sử bang chiến. */
    public dev.kage.guild.api.GuildWarHistoryFacade wireGuildWarHistoryFacade() {
        return new dev.kage.guild.api.GuildWarHistoryFacade(
                new dev.kage.server.database.guild.PostgresGuildWarHistoryAdapter(dataSource));
    }

    /** ADR-207 — góp vật phẩm cho bang. */
    public dev.kage.guild.api.GuildItemDonationFacade wireGuildItemDonationFacade() {
        return new dev.kage.guild.api.GuildItemDonationFacade(
                new dev.kage.server.database.guild.PostgresGuildItemDonationAdapter(dataSource));
    }

    /** ADR-186 — điểm danh bang. */
    public dev.kage.guild.api.GuildCheckInFacade wireGuildCheckInFacade() {
        return new dev.kage.guild.api.GuildCheckInFacade(
                new dev.kage.server.database.guild.PostgresGuildCheckInAdapter(dataSource),
                java.time.Clock.systemUTC());
    }

    /** ADR-185 — bảng chiêu mộ bang. */
    public dev.kage.guild.api.GuildRecruitmentFacade wireGuildRecruitmentFacade() {
        return new dev.kage.guild.api.GuildRecruitmentFacade(
                new dev.kage.server.database.guild.PostgresGuildRecruitmentAdapter(dataSource));
    }

    /** ADR-183 — nhường chức bang chủ. */
    public dev.kage.guild.api.GuildLeadershipFacade wireGuildLeadershipFacade() {
        return new dev.kage.guild.api.GuildLeadershipFacade(
                new dev.kage.server.database.guild.PostgresGuildLeadershipAdapter(dataSource));
    }

    /** ADR-180 — nhật ký bang (đọc). */
    public dev.kage.guild.api.GuildLogFacade wireGuildLogFacade() {
        return new dev.kage.guild.api.GuildLogFacade(
                new dev.kage.server.database.guild.PostgresGuildLogAdapter(dataSource));
    }

    /** ADR-179 — thông báo bang. */
    public dev.kage.guild.api.GuildNoticeFacade wireGuildNoticeFacade() {
        return new dev.kage.guild.api.GuildNoticeFacade(
                new dev.kage.server.database.guild.PostgresGuildNoticeAdapter(dataSource));
    }

    /** ADR-177 — lịch sử phường thị. */
    public dev.kage.market.api.MarketHistoryFacade wireMarketHistoryFacade() {
        return new dev.kage.market.api.MarketHistoryFacade(
                new dev.kage.server.database.market.PostgresMarketHistoryAdapter(dataSource));
    }

    /** ADR-175 — lưu bộ trang bị. */
    public dev.kage.inventory.api.EquipmentPresetFacade wireEquipmentPresetFacade() {
        return new dev.kage.inventory.api.EquipmentPresetFacade(
                new dev.kage.server.database.inventory.PostgresEquipmentPresetAdapter(dataSource));
    }

    /** ADR-173 — ghi chú bạn bè. */
    public dev.kage.friend.api.FriendNoteFacade wireFriendNoteFacade() {
        return new dev.kage.friend.api.FriendNoteFacade(
                new dev.kage.server.database.friend.PostgresFriendNoteAdapter(dataSource));
    }

    /** ADR-171 — công tắc cho người khác soi trang bị. */
    public dev.kage.character.api.InspectPrivacyFacade wireInspectPrivacyFacade() {
        return new dev.kage.character.api.InspectPrivacyFacade(
                new dev.kage.server.database.character.PostgresInspectPrivacyAdapter(dataSource));
    }

    /** ADR-172 — đổi tên bang hội. */
    public dev.kage.guild.api.GuildRenameFacade wireGuildRenameFacade() {
        return new dev.kage.guild.api.GuildRenameFacade(
                new dev.kage.server.database.guild.PostgresGuildRenameAdapter(dataSource));
    }

    /** Điểm vinh dự đấu trường + cửa hàng (ADR-155). */
    public dev.kage.pvp.api.HonorFacade wireHonorFacade() {
        return new dev.kage.pvp.api.HonorFacade(new dev.kage.server.database.pvp.PostgresHonorAdapter(dataSource));
    }

    /** Hỏi đáp tông môn (ADR-122) — {@code starts} là giờ bắt đầu phiên theo giờ Việt Nam. */
    public SectQuiz wireSectQuiz(List<LocalTime> starts) {
        return new SectQuiz(new PostgresQuizAdapter(dataSource), starts, System.nanoTime());
    }

    /** Thu phục yêu thú (ADR-124). */
    public TamingFacade wireTamingFacade() {
        return new TamingFacade(new PostgresTamingAdapter(dataSource));
    }

    /** Động phủ (ADR-129). */
    public dev.kage.world.api.CaveFacade wireCaveFacade() {
        return new dev.kage.world.api.CaveFacade(new dev.kage.server.database.world.PostgresCaveAdapter(dataSource));
    }

    /** Sát khí (ADR-142). */
    public dev.kage.world.api.KillingIntentFacade wireKillingIntentFacade() {
        return new dev.kage.world.api.KillingIntentFacade(
                new dev.kage.server.database.world.PostgresKillingIntentAdapter(dataSource));
    }

    /** Pháp bảo (ADR-127). */
    public dev.kage.world.api.ArtifactFacade wireArtifactFacade() {
        return new dev.kage.world.api.ArtifactFacade(new PostgresArtifactAdapter(dataSource));
    }

    /** Thiên Kiếp (ADR-126). */
    public dev.kage.character.api.TribulationFacade wireTribulationFacade() {
        return new dev.kage.character.api.TribulationFacade(
                new dev.kage.server.database.character.PostgresTribulationAdapter(dataSource));
    }

    /** Luận võ đại hội (ADR-125) — {@code schedule} là ngày + giờ giải theo giờ Việt Nam. */
    public TournamentFacade wireTournamentFacade(TournamentRules.Schedule schedule) {
        return new TournamentFacade(
                new TournamentDirector(new PostgresTournamentAdapter(dataSource), schedule, System.nanoTime()));
    }
}
