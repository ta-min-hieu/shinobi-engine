package dev.kage.server.gateway;

import dev.kage.chat.api.ChatFacade;
import dev.kage.friend.api.FriendFacade;
import dev.kage.mail.api.MailFacade;

/**
 * Gộp friend + mail + chat — {@link ContentFacades} đã CHẠM giới hạn 4 tham số Checkstyle {@code
 * ParameterNumber} với shop/dialogue/quest/friend (Phase 6 lát cắt 4), thêm {@code mailFacade}
 * (lát cắt 5) buộc phải tách thêm 1 lớp gộp thay vì thêm trực tiếp. {@code chatFacade} (lát cắt 6)
 * là trường thứ 3 — record này ĐÃ CÒN 1 chỗ trống trước khi lại chạm giới hạn 4.
 */
record SocialFacades(FriendFacade friendFacade, MailFacade mailFacade, ChatFacade chatFacade) {
}
