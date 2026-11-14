package dev.kage.server.database;

import javax.sql.DataSource;

/**
 * Dựng facade lô 45 trở đi (ADR-287…) — tách khỏi {@link ExpansionWiring} vì lớp đó chạm trần độ phức tạp PMD tính cho
 * cả lớp. Lấy qua {@link ExpansionWiring#lot45()}.
 */
public final class Lot45Wiring {

    private final DataSource dataSource;

    Lot45Wiring(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** ADR-296 — lịch sử boss đã hạ. */
    public dev.kage.monster.api.BossKillLogFacade wireBossKillLogFacade() {
        return new dev.kage.monster.api.BossKillLogFacade(
                new dev.kage.server.database.monster.PostgresBossKillLogAdapter(dataSource));
    }

    /** ADR-297 — thư hẹn giờ. */
    public dev.kage.mail.api.ScheduledMailFacade wireScheduledMailFacade() {
        return new dev.kage.mail.api.ScheduledMailFacade(
                new dev.kage.server.database.mail.PostgresScheduledMailAdapter(dataSource));
    }

    /** ADR-298 — thích hồ sơ. */
    public dev.kage.character.api.ProfileLikeFacade wireProfileLikeFacade() {
        return new dev.kage.character.api.ProfileLikeFacade(
                new dev.kage.server.database.character.PostgresProfileLikeAdapter(dataSource),
                java.time.Clock.systemUTC());
    }

    /** ADR-312 — giới hạn thư gửi mọi bạn bè. */
    public dev.kage.mail.api.FriendMailFacade wireFriendMailFacade() {
        return new dev.kage.mail.api.FriendMailFacade(
                new dev.kage.server.database.mail.PostgresFriendMailQuotaAdapter(dataSource));
    }

    /** ADR-313 — lời chào thành viên mới. */
    public dev.kage.guild.api.GuildWelcomeFacade wireGuildWelcomeFacade() {
        return new dev.kage.guild.api.GuildWelcomeFacade(
                new dev.kage.server.database.guild.PostgresGuildWelcomeAdapter(dataSource));
    }

    /** ADR-314 — thư chưa đọc. */
    public dev.kage.mail.api.MailReadFacade wireMailReadFacade() {
        return new dev.kage.mail.api.MailReadFacade(
                new dev.kage.server.database.mail.PostgresMailReadAdapter(dataSource));
    }

    /** ADR-319 — ghi chú thành viên bang. */
    public dev.kage.guild.api.GuildMemberNoteFacade wireGuildMemberNoteFacade() {
        return new dev.kage.guild.api.GuildMemberNoteFacade(
                new dev.kage.server.database.guild.PostgresGuildMemberNoteAdapter(dataSource));
    }

    /** ADR-320 — mã giới thiệu. */
    public dev.kage.character.api.ReferralFacade wireReferralFacade() {
        return new dev.kage.character.api.ReferralFacade(
                new dev.kage.server.database.character.PostgresReferralAdapter(dataSource));
    }

    /** ADR-322 — lịch sử đột phá. */
    public dev.kage.character.api.BreakthroughLogFacade wireBreakthroughLogFacade() {
        return new dev.kage.character.api.BreakthroughLogFacade(
                new dev.kage.server.database.character.PostgresBreakthroughLogAdapter(dataSource));
    }

    /** ADR-327 — chuỗi đăng nhập liên tiếp. */
    public dev.kage.character.api.LoginStreakFacade wireLoginStreakFacade() {
        return new dev.kage.character.api.LoginStreakFacade(
                new dev.kage.server.database.character.PostgresLoginStreakAdapter(dataSource));
    }

    /** ADR-329 — người vừa chơi cùng. */
    public dev.kage.party.api.RecentPlayerFacade wireRecentPlayerFacade() {
        return new dev.kage.party.api.RecentPlayerFacade(
                new dev.kage.server.database.party.PostgresRecentPlayerAdapter(dataSource));
    }

    /** ADR-330 — báo khi bị soi trang bị. */
    public dev.kage.character.api.InspectNoticeFacade wireInspectNoticeFacade() {
        return new dev.kage.character.api.InspectNoticeFacade(
                new dev.kage.server.database.character.PostgresInspectNoticeAdapter(dataSource));
    }

    /** ADR-335 — lời nhắn trạng thái. */
    public dev.kage.friend.api.StatusMessageFacade wireStatusMessageFacade() {
        return new dev.kage.friend.api.StatusMessageFacade(
                new dev.kage.server.database.friend.PostgresStatusMessageAdapter(dataSource));
    }

    /** ADR-336 — bảng rao vặt. */
    public dev.kage.chat.api.BulletinFacade wireBulletinFacade() {
        return new dev.kage.chat.api.BulletinFacade(
                new dev.kage.server.database.chat.PostgresBulletinAdapter(dataSource));
    }

    /** ADR-337 — lịch sử tử vong. */
    public dev.kage.character.api.DeathLogFacade wireDeathLogFacade() {
        return new dev.kage.character.api.DeathLogFacade(
                new dev.kage.server.database.character.PostgresDeathLogAdapter(dataSource));
    }

    /** ADR-343 — tra tên cũ. */
    public dev.kage.character.api.NameHistoryFacade wireNameHistoryFacade() {
        return new dev.kage.character.api.NameHistoryFacade(
                new dev.kage.server.database.character.PostgresNameHistoryAdapter(dataSource));
    }

    /** ADR-344 — ký hiệu bang. */
    public dev.kage.guild.api.GuildTagFacade wireGuildTagFacade() {
        return new dev.kage.guild.api.GuildTagFacade(
                new dev.kage.server.database.guild.PostgresGuildTagAdapter(dataSource));
    }

    /** ADR-351/352/353 — xếp hạng chuỗi đăng nhập, người giới thiệu, tìm người chơi. */
    public dev.kage.character.api.PlayerLookupFacade wirePlayerLookupFacade() {
        return new dev.kage.character.api.PlayerLookupFacade(
                new dev.kage.server.database.character.PostgresPlayerLookupAdapter(dataSource));
    }

    /** ADR-354 — lưu trữ thư. */
    public dev.kage.mail.api.MailArchiveFacade wireMailArchiveFacade() {
        return new dev.kage.mail.api.MailArchiveFacade(
                new dev.kage.server.database.mail.PostgresMailArchiveAdapter(dataSource));
    }

    /** ADR-360/361/362 — ai thích mình, lịch sử bị soi, chỉ bạn bè được soi. */
    public dev.kage.character.api.ProfileVisitFacade wireProfileVisitFacade() {
        return new dev.kage.character.api.ProfileVisitFacade(
                new dev.kage.server.database.character.PostgresProfileVisitAdapter(dataSource));
    }

    /** ADR-376 — lời nhắn kèm lời mời kết bạn. */
    public dev.kage.friend.api.FriendRequestNoteFacade wireFriendRequestNoteFacade() {
        return new dev.kage.friend.api.FriendRequestNoteFacade(
                new dev.kage.server.database.friend.PostgresFriendRequestNoteAdapter(dataSource));
    }

    /** ADR-378 — thống kê PvP ngoài đấu trường. */
    public dev.kage.character.api.PvpStatsFacade wirePvpStatsFacade() {
        return new dev.kage.character.api.PvpStatsFacade(
                new dev.kage.server.database.character.PostgresPvpStatsAdapter(dataSource));
    }

    /** ADR-383 — chế độ ẩn với bạn bè. */
    public dev.kage.friend.api.InvisibleModeFacade wireInvisibleModeFacade() {
        return new dev.kage.friend.api.InvisibleModeFacade(
                new dev.kage.server.database.friend.PostgresInvisibleModeAdapter(dataSource));
    }

    /** ADR-391/392 — xếp hạng sát thủ PvP / thời gian tu luyện. */
    public dev.kage.character.api.LifetimeRankingFacade wireLifetimeRankingFacade() {
        return new dev.kage.character.api.LifetimeRankingFacade(
                new dev.kage.server.database.character.PostgresLifetimeRankingAdapter(dataSource));
    }

    /** ADR-393 — tra cứu bang (thâm niên). */
    public dev.kage.guild.api.GuildDirectoryFacade wireGuildDirectoryFacade() {
        return new dev.kage.guild.api.GuildDirectoryFacade(
                new dev.kage.server.database.guild.PostgresGuildDirectoryAdapter(dataSource));
    }

    /** ADR-400 — báo đã đọc thư. */
    public dev.kage.mail.api.MailReceiptFacade wireMailReceiptFacade() {
        return new dev.kage.mail.api.MailReceiptFacade(
                new dev.kage.server.database.mail.PostgresMailReceiptAdapter(dataSource));
    }

    /** ADR-402 — chỉ bạn bè được nhắn riêng. */
    public dev.kage.friend.api.WhisperPrivacyFacade wireWhisperPrivacyFacade() {
        return new dev.kage.friend.api.WhisperPrivacyFacade(
                new dev.kage.server.database.friend.PostgresWhisperPrivacyAdapter(dataSource));
    }

    /** ADR-417 — tân thủ hôm nay. */
    public dev.kage.character.api.NewPlayersFacade wireNewPlayersFacade() {
        return new dev.kage.character.api.NewPlayersFacade(
                new dev.kage.server.database.character.PostgresNewPlayersAdapter(dataSource));
    }

    /** ADR-423 — lịch sử tên bang. */
    public dev.kage.guild.api.GuildNameHistoryFacade wireGuildNameHistoryFacade() {
        return new dev.kage.guild.api.GuildNameHistoryFacade(
                new dev.kage.server.database.guild.PostgresGuildNameHistoryAdapter(dataSource));
    }

    /** ADR-425 — câu chào khi vào game. */
    public dev.kage.friend.api.LoginGreetingFacade wireLoginGreetingFacade() {
        return new dev.kage.friend.api.LoginGreetingFacade(
                new dev.kage.server.database.friend.PostgresLoginGreetingAdapter(dataSource));
    }

    /** ADR-426 — báo cừu nhân online. */
    public dev.kage.pvp.api.EnemyAlertFacade wireEnemyAlertFacade() {
        return new dev.kage.pvp.api.EnemyAlertFacade(
                new dev.kage.server.database.pvp.PostgresEnemyAlertAdapter(dataSource));
    }

    /** ADR-431/432 — thống kê online (chăm chỉ hôm nay, 7 ngày). */
    public dev.kage.checkin.api.OnlineStatsFacade wireOnlineStatsFacade() {
        return new dev.kage.checkin.api.OnlineStatsFacade(
                new dev.kage.server.database.checkin.PostgresOnlineStatsAdapter(dataSource));
    }

    /** ADR-434 — xếp hạng thân mật. */
    public dev.kage.friend.api.IntimacyRankingFacade wireIntimacyRankingFacade() {
        return new dev.kage.friend.api.IntimacyRankingFacade(
                new dev.kage.server.database.friend.PostgresIntimacyRankingAdapter(dataSource));
    }

    /** ADR-288 — lịch sử mua cửa hàng NPC. */
    public dev.kage.shop.api.PurchaseHistoryFacade wirePurchaseHistoryFacade() {
        return new dev.kage.shop.api.PurchaseHistoryFacade(
                new dev.kage.server.database.shop.PostgresPurchaseHistoryAdapter(dataSource));
    }

    /** ADR-290 — thư đã gửi. */
    public dev.kage.mail.api.SentMailFacade wireSentMailFacade() {
        return new dev.kage.mail.api.SentMailFacade(
                new dev.kage.server.database.mail.PostgresSentMailAdapter(dataSource));
    }
}
