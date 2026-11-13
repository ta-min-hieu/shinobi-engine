package dev.kage.server.gateway;

import dev.kage.guild.api.GuildWelcomeFacade;
import dev.kage.mail.api.FriendMailFacade;
import dev.kage.mail.api.MailReadFacade;

/** Facade lô 48 (ADR-311…) gom thành MỘT thành phần của {@link Lot46Facades} (lô 47 không có facade riêng). */
record Lot48Facades(FriendMailFacade friendMail, GuildWelcomeFacade guildWelcome, MailReadFacade mailRead) {

    /** Composition không có các tính năng này (test). */
    static final Lot48Facades DISABLED =
            new Lot48Facades(FriendMailFacade.DISABLED, GuildWelcomeFacade.DISABLED, MailReadFacade.DISABLED);
}
