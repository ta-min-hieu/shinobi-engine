package dev.kage.client.core.ui.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.TextWidthMeter;

/**
 * Mọi nút HUD có bề rộng CỐ ĐỊNH phải chứa vừa nhãn của nó ở CẢ HAI ngôn ngữ.
 *
 * <p><b>Vì sao tồn tại.</b> Bẫy "nút bề rộng cố định": {@code QuestLogPanel} đặt nút 96px theo nhãn
 * tiếng Anh, nhãn tiếng Việt "Nhận thưởng" cần ~125px và vẽ tràn ra ngoài nền nút ở cả hai phía —
 * bắt được từ ảnh chụp playtest 2026-09-05, build vẫn xanh. Lần rà khi đó bằng ảnh chụp + dò pixel,
 * chỉ cho tiếng Việt, và phải mở game thật. Từ khi game song ngữ (quyết định chủ dự án 2026-10-01),
 * mỗi chuỗi có HAI độ dài, nên phép đo chuyển thành cổng build: đo bằng metric FreeType thật
 * ({@link TextWidthMeter}), không cần GL, chạy trong {@code check}.
 *
 * <p><b>Bảng dưới đây viết tay</b> — hằng bề rộng lấy thẳng từ panel (package-private) nên đổi bề
 * rộng là test tự đo lại, nhưng THÊM một nút rộng cố định mới thì phải thêm một dòng ở đây. Nút dùng
 * {@code minWidth} (VD {@code QuestLogPanel}) tự nở theo chữ, không cần có mặt.
 */
class HudButtonLabelFitTest {

    /** Nhãn nút HUD vẽ ở cỡ chữ gốc. */
    private static final float FULL_SCALE = 1f;

    private static TextWidthMeter meter;

    @BeforeAll
    static void openMeter() {
        meter = new TextWidthMeter();
    }

    @AfterAll
    static void closeMeter() {
        meter.close();
    }

    @Test
    void everyFixedWidthHudButtonFitsItsLabelInEveryLanguage() {
        List<String> problems = new ArrayList<>();
        problems.addAll(meter.overflows(
                "ChatPanel kênh",
                ChatPanel.CHANNEL_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "chat.channel.world",
                        "chat.channel.area",
                        "chat.channel.private",
                        "chat.channel.guild",
                        "chat.channel.party",
                        "chat.channel.system")));
        problems.addAll(meter.overflows(
                "DuelPanel",
                DuelPanel.ACTION_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "duel.action.challenge",
                        "duel.action.accept",
                        "duel.action.decline",
                        "duel.action.forfeit",
                        "duel.action.close")));
        problems.addAll(meter.overflows(
                "FactionPanel",
                FactionPanel.CHOICE_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("faction.hoa", "faction.thuy", "faction.phong", "faction.loi", "faction.tho")));
        problems.addAll(meter.overflows(
                "FriendPanel",
                FriendPanel.ACTION_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "friend.action.add",
                        "friend.action.close",
                        "friend.action.remove",
                        "friend.action.accept",
                        "friend.action.reject")));
        problems.addAll(meter.overflows(
                "GuildPanel",
                GuildPanel.ROW_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "guild.action.create",
                        "guild.action.join",
                        "guild.action.leave",
                        "guild.action.disband",
                        "guild.action.refresh",
                        "guild.action.kick",
                        "guild.action.approve",
                        "guild.action.reject")));
        problems.addAll(meter.overflows(
                "InventoryPanel hàng",
                InventoryPanel.ROW_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "inventory.action.equip",
                        "inventory.action.use",
                        "inventory.action.sell",
                        "inventory.action.select",
                        "inventory.action.unequip")));
        problems.addAll(meter.overflows(
                "InventoryPanel hành động",
                InventoryPanel.ACTION_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("inventory.action.offer", "inventory.action.enhance", "inventory.action.refresh")));
        problems.addAll(meter.overflows(
                "InventoryPanel hộ linh",
                InventoryPanel.PROTECTION_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("inventory.action.protection_on", "inventory.action.protection_off")));
        problems.addAll(meter.overflows(
                "MailPanel",
                MailPanel.ROW_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("mail.action.send", "mail.action.read", "mail.action.claim", "mail.action.delete")));
        problems.addAll(meter.overflows(
                "MailPanel làm mới", MailPanel.REFRESH_BUTTON_WIDTH_PX, FULL_SCALE, List.of("mail.action.refresh")));
        problems.addAll(
                meter.overflows("ShopPanel", ShopPanel.ROW_BUTTON_WIDTH_PX, FULL_SCALE, List.of("shop.action.buy")));
        problems.addAll(meter.overflows(
                "TradePanel",
                TradePanel.ACTION_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of(
                        "trade.action.request",
                        "trade.action.accept",
                        "trade.action.decline",
                        "trade.action.confirm",
                        "trade.action.cancel",
                        "trade.action.refresh")));
        problems.addAll(meter.overflows(
                "TradePanel linh thạch",
                TradePanel.GOLD_BUTTON_WIDTH_PX,
                FULL_SCALE,
                List.of("trade.action.set_gold")));
        problems.addAll(meter.overflows(
                "CombatHud ô gợi ý phím",
                CombatHud.SLOT_SIZE,
                CombatHud.HINT_LABEL_SCALE,
                List.of(
                        "hud.hint.left",
                        "hud.hint.right",
                        "hud.hint.jump",
                        "hud.hint.target",
                        "hud.hint.attack",
                        "hud.hint.skill")));

        assertThat(problems)
                .as("Nhãn vẽ tràn ra ngoài nền nút rộng cố định — rút gọn chữ hoặc nới hằng bề rộng"
                        + " (hoặc đổi .width() sang .minWidth() + pad như QuestLogPanel)")
                .isEmpty();
    }

    /**
     * Đối chứng: thước đo phải BẮT được đúng lỗi đã từng xảy ra. "Nhận thưởng" trong nút 96px cũ của
     * {@code QuestLogPanel} — nếu thước báo vừa thì mọi dòng xanh ở trên là vô nghĩa.
     */
    @Test
    void meterCatchesTheOriginalQuestLogOverflow() {
        float oldQuestLogButtonPx = 96f;
        assertThat(meter.overflows(
                        "QuestLogPanel cũ", oldQuestLogButtonPx, FULL_SCALE, List.of("questlog.action.claim")))
                .anySatisfy(problem -> assertThat(problem).contains("[vi]"));
    }
}
