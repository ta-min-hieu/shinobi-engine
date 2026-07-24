package dev.kage.server.gateway;

import dev.kage.chat.api.ChatFacade;
import dev.kage.friend.api.FriendFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.party.api.PartyFacade;

/**
 * Gộp friend + mail + chat + guild + party — record KHÔNG có compact constructor tường minh nên
 * KHÔNG bị Checkstyle {@code ParameterNumber} tính (chỉ áp dụng cho constructor/method viết tay
 * trong source, xem tiền lệ {@code GuildUseCases}/{@code FriendUseCases} nhiều thành phần hơn 4
 * vẫn build xanh) — record phẳng vẫn là lựa chọn đơn giản nhất cho nhóm "nội dung tương tác thế
 * giới nhỏ" này, không cần tách thêm lớp gộp chỉ vì số trường tăng.
 */
record SocialFacades(
        FriendFacade friendFacade, MailFacade mailFacade, ChatFacade chatFacade, GuildFacade guildFacade,
        PartyFacade partyFacade) {
}
