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
