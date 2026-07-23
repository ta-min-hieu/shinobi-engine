package dev.kage.server.gateway;

import dev.kage.friend.api.FriendFacade;
import dev.kage.mail.api.MailFacade;

/**
 * Gộp friend + mail — {@link ContentFacades} đã CHẠM giới hạn 4 tham số Checkstyle {@code
 * ParameterNumber} với shop/dialogue/quest/friend (Phase 6 lát cắt 4), thêm {@code mailFacade}
 * (lát cắt 5) buộc phải tách thêm 1 lớp gộp thay vì thêm trực tiếp. Còn chỗ cho Chat (lát cắt 6)
 * trước khi lại chạm giới hạn.
 */
record SocialFacades(FriendFacade friendFacade, MailFacade mailFacade) {
}
