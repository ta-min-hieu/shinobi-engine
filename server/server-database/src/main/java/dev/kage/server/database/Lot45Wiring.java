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
