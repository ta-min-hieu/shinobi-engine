package dev.kage.server.gateway;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.shop.api.ShopFacade;

/**
 * Gộp shop + dialogue — cả 2 đều là "nội dung tương tác thế giới" nhỏ, không cần Facade riêng
 * từng cái ở {@link GatewayFacades} (đã CHẠM giới hạn 4 tham số Checkstyle {@code ParameterNumber}
 * với account/character/inventory + 1 slot còn lại — thêm {@code dialogueFacade} trực tiếp sẽ
 * vượt 4, xem javadoc {@link GatewayFacades}).
 */
record ContentFacades(ShopFacade shopFacade, DialogueFacade dialogueFacade) {
}
