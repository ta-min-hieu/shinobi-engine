package dev.kage.server.database.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.mail.api.MailFacade;
import dev.kage.quest.api.QuestFacade;
import dev.kage.shop.api.ShopFacade;
import dev.kage.trade.api.TradeFacade;

/**
 * Một luồng "người chơi hỗn loạn": lặp tới hạn chót, mỗi vòng chọn ngẫu nhiên (theo trọng số) MỘT
 * thao tác kinh tế cho MỘT nhân vật ngẫu nhiên, gọi đúng Facade mà gateway gọi. Tham số cố ý lẫn
 * cả hợp lệ lẫn không hợp lệ (số lượng vượt chồng, món của người khác, phiên đã đóng, vàng không
 * đủ, khoá idempotency dùng lại...) — chỉ những dạng gateway để lọt xuống Facade
 * ({@code EconomyCommandShape} chặn trước số lượng ≤ 0 nên ở đây không gửi).
 *
 * <p>Mỗi kết quả THÀNH CÔNG có đẻ/huỷ vật phẩm được ghi vào {@link EconomyStressTally}.
 */
final class EconomyStressActor implements Runnable {

    static final String QUEST_CODE = "kill_training_dummies";
    static final String MONSTER_CODE = "training_dummy";

    private static final int PERCENT = 100;
    private static final int SMALL_BUY_MAX = 4;
    private static final int BULK_BUY_MIN = 50;
    private static final int BULK_BUY_SPREAD = 100;
    private static final int ABSURD_QUANTITY = 1_000_000;
    private static final long MAIL_GOLD_MAX = 300L;
    private static final long TRADE_GOLD_MAX = 200L;
    private static final long ABSURD_GOLD = 1_000_000_000L;
    private static final int SPLIT_ATTACHMENT_PERCENT = 33;
    private static final long BOGUS_ID = Long.MAX_VALUE / 2;

    private final EconomyStressFacades facades;
    private final EconomyStressCatalog catalog;
    private final EconomyStressTally tally;
    private final long[] characters;
    private final Random random;
    private final long deadlineNanos;
    private final List<WeightedOperation> operations = new ArrayList<>();
    private int totalWeight;

    EconomyStressActor(EconomyStressWorld world, Random random, long deadlineNanos) {
        this.facades = world.facades();
        this.catalog = world.catalog();
        this.tally = world.tally();
        this.characters = world.characters().stream().mapToLong(Long::longValue).toArray();
        this.random = random;
        this.deadlineNanos = deadlineNanos;
        register(14, this::buy);
        register(2, this::buyThenRetrySameKey);
        register(14, this::sell);
        register(10, this::killMonster);
        register(5, this::useItem);
        register(8, this::enhance);
        register(5, this::equip);
        register(3, this::unequip);
        register(7, this::sendMail);
        register(7, this::claimMail);
        register(2, this::deleteMail);
        register(6, this::requestTrade);
        register(6, this::acceptTrade);
        register(8, this::setTradeOffer);
        register(10, this::confirmTrade);
        register(3, this::cancelTrade);
        register(1, this::acceptQuest);
        register(2, this::claimQuest);
    }

    @Override
    public void run() {
        while (System.nanoTime() < deadlineNanos) {
            long actor = characters[random.nextInt(characters.length)];
            pickOperation().run(actor);
        }
    }

    private void register(int weight, Operation operation) {
        operations.add(new WeightedOperation(weight, operation));
        totalWeight += weight;
    }

    private Operation pickOperation() {
        int roll = random.nextInt(totalWeight);
        for (WeightedOperation candidate : operations) {
            roll -= candidate.weight();
            if (roll < 0) {
                return candidate.operation();
            }
        }
        throw new IllegalStateException("Trọng số thao tác hỏng");
    }

    // ---------------------------------------------------------------- cửa hàng

    private void buy(long actor) {
        long templateId = catalog.buyable().get(random.nextInt(catalog.buyable().size()));
        int quantity = buyQuantity();
        ShopFacade.BuyResult result = facades.shop().buyItem(actor, templateId, quantity, newKey());
        tally.outcome("buy", result);
        if (result == ShopFacade.BuyResult.OK) {
            tally.bought(templateId, quantity);
        }
    }

    /** Client tự retry cùng khoá: lần hai KHÔNG được có hiệu lực nếu lần một đã có. */
    private void buyThenRetrySameKey(long actor) {
        long templateId = catalog.buyable().get(random.nextInt(catalog.buyable().size()));
        String key = newKey();
        ShopFacade.BuyResult first = facades.shop().buyItem(actor, templateId, 1, key);
        ShopFacade.BuyResult second = facades.shop().buyItem(actor, templateId, 1, key);
        tally.outcome("buyRetry", first + "->" + second);
        if (first == ShopFacade.BuyResult.OK) {
            tally.bought(templateId, 1);
        }
        if (second == ShopFacade.BuyResult.OK) {
            tally.bought(templateId, 1);
            if (first == ShopFacade.BuyResult.OK) {
                tally.violation("idempotency: cùng khoá mua 2 lần đều OK, nhân vật " + actor);
            }
        }
    }

    private int buyQuantity() {
        int roll = random.nextInt(PERCENT);
        if (roll < 85) {
            return 1 + random.nextInt(SMALL_BUY_MAX);
        }
        if (roll < 95) {
            return BULK_BUY_MIN + random.nextInt(BULK_BUY_SPREAD);
        }
        return ABSURD_QUANTITY;
    }

    private void sell(long actor) {
        Optional<ItemStack> row = pickRow(random.nextInt(PERCENT) < 10 ? otherCharacter(actor) : actor);
        if (row.isEmpty()) {
            tally.outcome("sell", "EMPTY_BAG");
            return;
        }
        ItemStack item = row.get();
        int quantity = random.nextInt(PERCENT) < 20 ? item.quantity() + 1 : 1 + random.nextInt(item.quantity());
        ShopFacade.SellResult result = facades.shop().sellItem(actor, item.id(), quantity, newKey());
        tally.outcome("sell", result);
        if (result == ShopFacade.SellResult.OK) {
            tally.sold(item.itemTemplateId(), quantity);
        }
    }

    // ---------------------------------------------------------------- túi đồ

    /** Đúng thứ tự {@code ConnectedCharacterRegistry.onMonsterKilled}: rơi đồ rồi báo quest. */
    private void killMonster(long actor) {
        List<ItemStack> granted = facades.inventory().grantLoot(actor, MONSTER_CODE);
        tally.outcome("loot", granted.isEmpty() ? "NOTHING" : "GRANTED");
        for (ItemStack stack : granted) {
            tally.created(stack.itemTemplateId(), stack.quantity());
        }
        facades.quest().onMonsterKilled(actor, MONSTER_CODE);
    }

    private void useItem(long actor) {
        Optional<ItemStack> row = pickRow(actor);
        long itemId = row.map(ItemStack::id).orElse(BOGUS_ID);
        UseItemUseCase.UseItemResult result = facades.inventory().useItem(actor, itemId);
        tally.outcome("use", result.outcome());
        if (result.outcome() == UseItemUseCase.Outcome.OK) {
            tally.used(row.orElseThrow().itemTemplateId());
        }
    }

    private void enhance(long actor) {
        Optional<ItemStack> row = pickEquipmentRow(actor);
        long itemId = row.map(ItemStack::id).orElse(BOGUS_ID);
        InventoryFacade.EnhanceResult result =
                facades.inventory().enhanceItem(actor, itemId, random.nextBoolean(), newKey());
        tally.outcome("enhance", result);
        if (result == InventoryFacade.EnhanceResult.FAILED_PROTECTED) {
            tally.destroyed(catalog.idOf(EnhancementTable.PROTECTION_CHARM_CODE), 1);
        }
    }

    private void equip(long actor) {
        Optional<ItemStack> row = pickEquipmentRow(actor);
        if (row.isEmpty()) {
            tally.outcome("equip", "NO_EQUIPMENT");
            return;
        }
        Optional<EquipmentSlotType> ownSlot =
                catalog.template(row.get().itemTemplateId()).equipSlot();
        EquipmentSlotType slot = ownSlot.isPresent() && random.nextInt(PERCENT) < 80 ? ownSlot.get() : randomSlot();
        tally.outcome("equip", facades.inventory().equipItem(actor, row.get().id(), slot, 1));
    }

    private void unequip(long actor) {
        tally.outcome("unequip", facades.inventory().unequipItem(actor, randomSlot()));
    }

    // ---------------------------------------------------------------- thư

    private void sendMail(long actor) {
        long recipient = characters[random.nextInt(characters.length)]; // có thể là chính mình
        long gold = random.nextBoolean() ? 0L : 1L + (long) random.nextInt((int) MAIL_GOLD_MAX);
        if (random.nextInt(PERCENT) < 5) {
            gold = ABSURD_GOLD;
        }
        List<MailFacade.AttachmentRequest> attachments = new ArrayList<>();
        Optional<ItemStack> row = pickRow(actor);
        if (row.isPresent() && random.nextBoolean()) {
            int quantity = random.nextInt(PERCENT) < 15
                    ? row.get().quantity() + 1
                    : 1 + random.nextInt(row.get().quantity());
            addAttachmentLines(attachments, row.get().itemTemplateId(), quantity);
        }
        MailFacade.SendOutcome outcome = facades.mail()
                .sendMail(new MailFacade.SendMailRequest(actor, recipient, "stress", "body", gold, attachments));
        tally.outcome("mailSend", outcome.result());
    }

    /**
     * Một phần ba số lần chia CÙNG số lượng thành hai dòng đính kèm cùng loại — dạng mà client hợp
     * lệ không gửi nhưng dây cho phép; bản cấp-từng-dòng cũ từng làm thư không nhận nổi (2026-10-10).
     */
    private void addAttachmentLines(List<MailFacade.AttachmentRequest> attachments, long templateId, int quantity) {
        if (quantity < 2 || random.nextInt(PERCENT) >= SPLIT_ATTACHMENT_PERCENT) {
            attachments.add(new MailFacade.AttachmentRequest(templateId, quantity));
            return;
        }
        int firstLine = 1 + random.nextInt(quantity - 1);
        attachments.add(new MailFacade.AttachmentRequest(templateId, firstLine));
        attachments.add(new MailFacade.AttachmentRequest(templateId, quantity - firstLine));
    }

    private void claimMail(long actor) {
        long owner = random.nextInt(PERCENT) < 10 ? otherCharacter(actor) : actor;
        List<MailFacade.MailView> unclaimed = facades.mail().listMail(owner).stream()
                .filter(mail -> !mail.claimed())
                .toList();
        long mailId = unclaimed.isEmpty()
                ? BOGUS_ID
                : unclaimed.get(random.nextInt(unclaimed.size())).mailId();
        tally.outcome("mailClaim", facades.mail().claimMail(mailId, actor, newKey()));
    }

    private void deleteMail(long actor) {
        List<MailFacade.MailView> mails = facades.mail().listMail(actor);
        long mailId = mails.isEmpty()
                ? BOGUS_ID
                : mails.get(random.nextInt(mails.size())).mailId();
        tally.outcome("mailDelete", facades.mail().deleteMail(mailId, actor));
    }

    // ---------------------------------------------------------------- giao dịch

    private void requestTrade(long actor) {
        long target = random.nextInt(PERCENT) < 5 ? actor : otherCharacter(actor);
        tally.outcome(
                "tradeRequest", facades.trade().requestTrade(actor, target).outcome());
    }

    private void acceptTrade(long actor) {
        long sessionId = sessionOf(actor);
        tally.outcome("tradeAccept", facades.trade().acceptRequest(sessionId, actor));
    }

    private void setTradeOffer(long actor) {
        long sessionId = sessionOf(actor);
        long gold = random.nextInt(PERCENT) < 10 ? ABSURD_GOLD : (long) random.nextInt((int) TRADE_GOLD_MAX);
        List<TradeFacade.OfferItemInput> items = new ArrayList<>();
        int count = random.nextInt(3);
        for (int i = 0; i < count; i++) {
            pickRow(actor)
                    .ifPresent(row -> items.add(new TradeFacade.OfferItemInput(
                            row.id(),
                            random.nextInt(PERCENT) < 10 ? row.quantity() + 1 : 1 + random.nextInt(row.quantity()))));
        }
        tally.outcome("tradeOffer", facades.trade().setOffer(sessionId, actor, gold, items));
    }

    private void confirmTrade(long actor) {
        long sessionId = sessionOf(actor);
        tally.outcome("tradeConfirm", facades.trade().confirm(sessionId, actor, newKey()));
    }

    private void cancelTrade(long actor) {
        long sessionId = sessionOf(actor);
        tally.outcome("tradeCancel", facades.trade().cancel(sessionId, actor));
    }

    /** Phiên hiện tại của nhân vật — hoặc (10%, hay khi không có) một phiên của người khác / không tồn tại. */
    private long sessionOf(long actor) {
        long subject = random.nextInt(PERCENT) < 10 ? otherCharacter(actor) : actor;
        return facades.trade()
                .getStatus(subject)
                .map(TradeFacade.TradeStatusView::sessionId)
                .orElse(BOGUS_ID);
    }

    // ---------------------------------------------------------------- nhiệm vụ

    private void acceptQuest(long actor) {
        tally.outcome("questAccept", facades.quest().acceptQuest(actor, QUEST_CODE));
    }

    private void claimQuest(long actor) {
        QuestFacade.ClaimResult result = facades.quest().claimReward(actor, QUEST_CODE, newKey());
        tally.outcome("questClaim", result.outcome());
        if (result.outcome() == QuestFacade.ClaimOutcome.OK) {
            for (QuestFacade.ItemReward reward : result.itemsGranted()) {
                tally.created(catalog.idOf(reward.itemTemplateCode()), reward.quantity());
            }
        }
    }

    // ---------------------------------------------------------------- chọn ngẫu nhiên

    private Optional<ItemStack> pickRow(long owner) {
        List<ItemStack> items =
                facades.inventory().listInventory(owner).inventory().items();
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(random.nextInt(items.size())));
    }

    /** Chỉ trang bị ({@code max_stack = 1}) — cường hoá chồng xếp là câu hỏi đang chờ chủ dự án. */
    private Optional<ItemStack> pickEquipmentRow(long owner) {
        List<ItemStack> equipment = facades.inventory().listInventory(owner).inventory().items().stream()
                .filter(item ->
                        catalog.template(item.itemTemplateId()).equipSlot().isPresent())
                .toList();
        return equipment.isEmpty() ? Optional.empty() : Optional.of(equipment.get(random.nextInt(equipment.size())));
    }

    private long otherCharacter(long actor) {
        long other = actor;
        while (other == actor) {
            other = characters[random.nextInt(characters.length)];
        }
        return other;
    }

    private EquipmentSlotType randomSlot() {
        EquipmentSlotType[] slots = EquipmentSlotType.values();
        return slots[random.nextInt(slots.length)];
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    private interface Operation {
        void run(long actor);
    }

    private record WeightedOperation(int weight, Operation operation) {}
}
