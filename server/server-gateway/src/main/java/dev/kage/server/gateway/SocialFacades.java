package dev.kage.server.gateway;

import dev.kage.chat.api.ChatFacade;
import dev.kage.friend.api.FriendFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.mail.api.MailFacade;

/**
 * Gộp friend + mail + chat + guild — {@link ContentFacades} đã CHẠM giới hạn 4 tham số Checkstyle
 * {@code ParameterNumber} với shop/dialogue/quest/friend (Phase 6 lát cắt 4), thêm {@code
 * mailFacade} (lát cắt 5) buộc phải tách thêm 1 lớp gộp thay vì thêm trực tiếp. {@code chatFacade}
 * (lát cắt 6) là trường thứ 3; {@code guildFacade} (Phase 7 lát cắt 1) là trường thứ 4 — record
 * này ĐÃ CHẠM giới hạn 4, thêm facade tiếp theo (party/trade) PHẢI tách thêm 1 lớp gộp nữa.
 */
record SocialFacades(FriendFacade friendFacade, MailFacade mailFacade, ChatFacade chatFacade, GuildFacade guildFacade) {
}
