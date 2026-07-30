package dev.kage.server.gateway;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.shop.api.ShopFacade;

/**
 * Gộp shop + dialogue + quest + {@link SocialFacades friend/mail} — đều là "nội dung tương tác
 * thế giới" nhỏ, không cần Facade riêng từng cái ở {@link GatewayFacades} (đã CHẠM giới hạn 4 tham
 * số Checkstyle {@code ParameterNumber} với account/character/inventory + 1 slot còn lại, xem
 * javadoc {@link GatewayFacades}). Bản thân record này CŨNG đã chạm giới hạn 4 trường (Phase 6 lát
 * cắt 4) — thêm {@code mailFacade} (lát cắt 5) phải tách qua {@link SocialFacades} thay vì thêm
 * trực tiếp.
 */
record ContentFacades(
        ShopFacade shopFacade, DialogueFacade dialogueFacade, QuestFacade questFacade, SocialFacades socialFacades) {}
