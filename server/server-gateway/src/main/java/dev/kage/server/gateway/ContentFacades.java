package dev.kage.server.gateway;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.friend.api.FriendFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.shop.api.ShopFacade;

/**
 * Gộp shop + dialogue + quest + friend — đều là "nội dung tương tác thế giới" nhỏ, không cần Facade riêng
 * từng cái ở {@link GatewayFacades} (đã CHẠM giới hạn 4 tham số Checkstyle {@code ParameterNumber}
 * với account/character/inventory + 1 slot còn lại — thêm từng Facade trực tiếp sẽ vượt 4, xem
 * javadoc {@link GatewayFacades}). {@code friendFacade} thêm ở Phase 6 lát cắt 4 —
 * ĐÃ CHẠM giới hạn 4 trường ở record này (shop/dialogue/quest/friend); Mail/Chat (lát cắt kế tiếp)
 * sẽ cần tách thêm 1 record gộp mới, không thể thêm trực tiếp vào đây nữa.
 */
record ContentFacades(
        ShopFacade shopFacade, DialogueFacade dialogueFacade, QuestFacade questFacade, FriendFacade friendFacade) {
}
