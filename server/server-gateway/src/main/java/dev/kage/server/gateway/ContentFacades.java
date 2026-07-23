package dev.kage.server.gateway;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.shop.api.ShopFacade;

/**
 * Gộp shop + dialogue + quest — đều là "nội dung tương tác thế giới" nhỏ, không cần Facade riêng
 * từng cái ở {@link GatewayFacades} (đã CHẠM giới hạn 4 tham số Checkstyle {@code ParameterNumber}
 * với account/character/inventory + 1 slot còn lại — thêm từng Facade trực tiếp sẽ vượt 4, xem
 * javadoc {@link GatewayFacades}).
 */
record ContentFacades(ShopFacade shopFacade, DialogueFacade dialogueFacade, QuestFacade questFacade) {
}
