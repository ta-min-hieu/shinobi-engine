package dev.kage.client.core.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * ĐO chiều cao của mọi cửa sổ HUD có danh sách ở trạng thái TỆ NHẤT — danh sách đầy tới
 * {@link ScrollingList#MAX_BUILT_ROWS}, mọi dòng tuỳ chọn đều hiện, chữ dài nhất — và FAIL nếu cửa
 * sổ nào cao hơn cửa sổ game 720p.
 *
 * <p>Tồn tại vì bài học "đo bố cục ở trạng thái tệ nhất, đừng nhìn": khung túi đồ từng cao 1238px
 * trong màn hình 720px mà build vẫn xanh, và ảnh chụp ở trạng thái bình thường không cho thấy gì.
 * Con số ở đây là {@code Table#getPrefHeight()} SAU vài lượt layout thật — nhãn tự xuống hàng chỉ có
 * chiều cao đúng khi đã biết bề rộng của nó, nên đo trước lượt layout đầu là đo sai.
 *
 * <p>KHÔNG phải JUnit: cần GL context thật (mỗi panel dựng {@code SpriteBatch}, {@link KageSkin} sinh
 * texture), mà CI chạy {@code ubuntu-latest} không có display — cùng lý do
 * {@code FontFactoryVietnameseGlyphTest} ghi lại. Chạy tay:
 * {@code ./gradlew :client:client-core:runHudLayoutCheck} (~10 giây, mở một cửa sổ rồi tự đóng).
 * Thêm {@code -PkageLayoutRows=N} để đo với N dòng thay vì trần.
 *
 * <h2>Số đo (2026-09-11, 1280x720)</h2>
 *
 * <pre>
 * cửa sổ            TRƯỚC (trần cũ, không cuộn)     SAU (60 dòng, vùng cuộn chặn chiều cao)
 * Hòm thư           1224px @ 6 dòng  — TRÀN         695px
 * Cửa hàng           669px @ 8 dòng                 590px
 * Bang hội          1261px @ 8+8     — TRÀN         635px
 * Giao dịch          886px @ 6+6     — TRÀN         676px
 * Bạn bè            1044px @ 8+8     — TRÀN         692px
 * Nhật ký nhiệm vụ   626px @ 8 dòng                 547px
 * Túi đồ             693px (đã có vùng cuộn từ trước) 693px
 * </pre>
 *
 * Bốn cửa sổ TRÀN ngay cả với trần cũ — tức là trần "cho vừa màn hình" chưa bao giờ làm được việc
 * nó hứa ở trạng thái tệ nhất, nó chỉ giấu dữ liệu. Hòm thư tràn chủ yếu vì nội dung thư 1024 ký
 * tự (~400px) tự nở, không phải vì danh sách. Chỉnh bất kỳ chiều cao nào ở các panel thì chạy lại
 * và sửa bảng này.
 */
public final class HudLayoutBudgetTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(HudLayoutBudgetTool.class);

    /** Đúng kích thước cửa sổ game thật ({@code DesktopLauncher}). */
    private static final int SCREEN_WIDTH_PX = 1280;

    private static final int SCREEN_HEIGHT_PX = 720;

    /** Đủ để nhãn xuống hàng nhận bề rộng ở lượt đầu rồi tính lại chiều cao ở lượt sau. */
    private static final int LAYOUT_PASSES = 4;

    private static final String ROWS_PROPERTY = "kage.layout.rows";

    /** Kết quả vượt trần — đọc lại ở {@link #main} SAU khi vòng lặp GL đã đóng. */
    private static final List<String> FAILURES = new ArrayList<>();

    private static final String LONG_NAME = "PhongVanTuyetNguyet";

    private final int rows = Integer.getInteger(ROWS_PROPERTY, ScrollingList.MAX_BUILT_ROWS);

    private KageSkin skin;
    private final List<HudWindow> built = new ArrayList<>();

    @Override
    public void create() {
        skin = new KageSkin();
        LOG.info("Đo {} cửa sổ ở {} dòng mỗi danh sách, màn hình {}x{}", 7, rows, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);
        measureMail();
        measureShop();
        measureGuild();
        measureTrade();
        measureFriend();
        measureQuestLog();
        measureInventory();
        Gdx.app.exit();
    }

    private void measure(String name, HudWindow window, DoubleSupplier preferredHeight) {
        built.add(window);
        window.resize(SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);
        for (int pass = 0; pass < LAYOUT_PASSES; pass++) {
            window.render(0f);
        }
        double height = preferredHeight.getAsDouble();
        boolean fits = height <= SCREEN_HEIGHT_PX;
        LOG.info(
                "{} — cao {}px / {}px ({})",
                name,
                Math.round(height),
                SCREEN_HEIGHT_PX,
                fits ? "vừa, dư " + Math.round(SCREEN_HEIGHT_PX - height) + "px" : "TRÀN");
        if (!fits) {
            FAILURES.add(name + " cao " + Math.round(height) + "px > " + SCREEN_HEIGHT_PX + "px");
        }
    }

    /** Hòm thư đầy, MỌI dòng đều có nút Nhận, đang đọc một lá thư dài đúng 1024 ký tự (cột {@code mail.body}). */
    private void measureMail() {
        MailPanel panel = new MailPanel(skin);
        panel.setStatus("Đã gửi thư cho " + LONG_NAME + " — người nhận sẽ thấy ở lần mở hòm thư tới.");
        panel.setNewMailBadge(true);
        List<MailPanel.MailRow> mails = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            mails.add(new MailPanel.MailRow(
                    index + 1L, "Hệ thống — Phần thưởng nhiệm vụ Tuần Tra Đêm · 1.200 vàng + Vật phẩm #12 x3", true));
        }
        panel.setMails(mails, "... còn 12 lá thư nữa chưa hiện.");
        panel.showMail("Hệ thống — Phần thưởng nhiệm vụ Tuần Tra Đêm · 1.200 vàng + Vật phẩm #12 x3", longBody());
        panel.open();
        measure("Hòm thư", panel, panel::preferredHeight);
    }

    /** Nội dung thư dài đúng 1024 ký tự — trần cột {@code mail.body} (V13), chữ tiếng Việt thật. */
    private static String longBody() {
        String sentence = "Sư đệ, tối nay trưởng lão Chu Hoành gọi cả ngoại môn tới Tàng Kinh Các, nhớ mang lệnh bài. ";
        StringBuilder body = new StringBuilder();
        while (body.length() < MailBodyLength.MAX) {
            body.append(sentence);
        }
        return body.substring(0, MailBodyLength.MAX);
    }

    /** Trần cột {@code mail.body} (V13) — khớp {@code MailController.MAX_BODY_LENGTH}, package khác. */
    private static final class MailBodyLength {
        static final int MAX = 1024;
    }

    private void measureShop() {
        ShopPanel panel = new ShopPanel(skin);
        panel.setStatus("Đã mua 3 Tiểu Hồi Nguyên Đan — vàng đã trừ theo giá server tính.");
        panel.setGoldText("Vàng: 1.234.567");
        List<ShopPanel.ShopRow> listings = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            listings.add(new ShopPanel.ShopRow(index + 1L, "Dây Chuyền Hạ Nhẫn — Mua: 1.500 · Bán: 500"));
        }
        panel.setListings(listings, "... còn 4 món nữa chưa hiện.");
        panel.open();
        measure("Cửa hàng", panel, panel::preferredHeight);
    }

    /** Bang chủ: danh sách thành viên ĐẦY + danh sách đơn xin gia nhập ĐẦY, cả hai cùng lúc. */
    private void measureGuild() {
        GuildPanel panel = new GuildPanel(skin);
        panel.setStatus("Đã duyệt đơn của " + LONG_NAME + " — danh sách thành viên đã hỏi lại server.");
        panel.showGuild("Bang Hắc Long Thiên Hạ Đệ Nhất — Bang chủ", true);
        List<GuildPanel.MemberRow> members = new ArrayList<>();
        List<GuildPanel.RequestRow> requests = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            members.add(new GuildPanel.MemberRow(LONG_NAME + " — Thành viên · Ngoại tuyến", LONG_NAME, true));
            requests.add(new GuildPanel.RequestRow(index + 1L, LONG_NAME));
        }
        panel.setMembers(members, "... còn 7 dòng nữa chưa hiện.");
        panel.setPendingRequests(requests, "... còn 7 dòng nữa chưa hiện.");
        panel.open();
        measure("Bang hội", panel, panel::preferredHeight);
    }

    /** Đang trong phiên + có lời mời chờ + CẢ HAI lời chào đầy vật phẩm. */
    private void measureTrade() {
        TradePanel panel = new TradePanel(skin);
        panel.setStatus("Lời chào đã đổi — cả hai cờ xác nhận rơi về chưa xác nhận.");
        panel.setInvite(LONG_NAME + " mời bạn giao dịch.");
        panel.setSession(
                "Đang giao dịch với " + LONG_NAME + " — đang mở",
                "Vàng: 1.234.567 · Chưa xác nhận",
                "Vàng: 7.654.321 · Đã xác nhận");
        List<String> items = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            items.add("Áo Giáp Hạ Nhẫn x1 +15");
        }
        panel.setItems(items, "... còn 3 món nữa chưa hiện.", true);
        panel.setItems(items, "... còn 3 món nữa chưa hiện.", false);
        panel.open();
        measure("Giao dịch", panel, panel::preferredHeight);
    }

    /** Lời mời đầy + bạn bè đầy + dòng thông báo. */
    private void measureFriend() {
        FriendPanel panel = new FriendPanel(skin);
        List<FriendPanel.FriendRow> friends = new ArrayList<>();
        List<FriendPanel.RequestRow> requests = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            friends.add(new FriendPanel.FriendRow(LONG_NAME, false));
            requests.add(new FriendPanel.RequestRow(LONG_NAME));
        }
        panel.open();
        panel.setFriends(friends);
        panel.setPendingRequests(requests);
        panel.setNotice("Đã gửi lời mời kết bạn tới " + LONG_NAME + ".");
        measure("Bạn bè", panel, panel::preferredHeight);
    }

    /** Nhật ký đầy, MỌI dòng có nút Nhận thưởng (nhãn tiếng Việt dài nhất của cửa sổ này). */
    private void measureQuestLog() {
        QuestLogPanel panel = new QuestLogPanel(skin);
        panel.setStatus("Đã nhận thưởng nhiệm vụ — EXP và vật phẩm đã về túi.");
        List<QuestLogPanel.QuestRow> quests = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            quests.add(new QuestLogPanel.QuestRow("awakening_trial", "Khảo Hạch Thức Tỉnh — Đã hoàn thành", true));
        }
        panel.setQuests(quests, "... còn 2 nhiệm vụ nữa chưa hiện.");
        panel.open();
        measure("Nhật ký nhiệm vụ", panel, panel::preferredHeight);
    }

    /** Túi đầy + đã chọn một món (bày cả hàng nút) + 7 ô trang bị đều có đồ — cùng {@code HudPreviewTool}. */
    private void measureInventory() {
        InventoryPanel panel = new InventoryPanel(skin);
        panel.setGold("1.234.567 vàng");
        panel.setStatus("Cường hoá thất bại — Bảo vệ đã chặn rớt cấp, tiêu 1 bùa.");
        List<InventoryPanel.ItemRow> items = new ArrayList<>();
        for (int index = 0; index < rows; index++) {
            items.add(new InventoryPanel.ItemRow(index + 1L, "", "Tiểu Hồi Nguyên Đan x20"));
        }
        panel.setItems(items, rows + "/20 ô", "... còn 4 vật phẩm nữa chưa hiện.");
        List<InventoryPanel.EquipmentRow> equipment = new ArrayList<>();
        for (int slot = 1; slot <= EquipmentSlots.COUNT; slot++) {
            equipment.add(new InventoryPanel.EquipmentRow(slot, "Trang sức 1", "Trường kiếm thẳng +15", true));
        }
        panel.setEquipment(equipment);
        panel.showDetail(
                "Tiểu Hồi Nguyên Đan x20" + System.lineSeparator() + "Hồi 50 máu",
                InventoryPanel.PrimaryAction.USE,
                new InventoryPanel.Actions(true, true, true));
        panel.open();
        measure("Túi đồ", panel, panel::preferredHeight);
    }

    /** Số ô trang bị thật ({@code EquipmentSlot} trừ UNSPECIFIED) — protobuf ở package khác. */
    private static final class EquipmentSlots {
        static final int COUNT = 7;
    }

    @Override
    public void dispose() {
        for (HudWindow window : built) {
            window.dispose(); // TRƯỚC skin: panel mượn skin
        }
        if (skin != null) {
            skin.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage - HUD layout budget (cong cu, khong phai game)");
        config.setWindowedMode(SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);
        config.useVsync(false);
        new Lwjgl3Application(new HudLayoutBudgetTool(), config);
        if (!FAILURES.isEmpty()) {
            throw new IllegalStateException("Cửa sổ HUD tràn màn hình 720p ở trạng thái tệ nhất: " + FAILURES);
        }
        LOG.info("Mọi cửa sổ HUD có danh sách đều vừa màn hình 720p ở trạng thái tệ nhất.");
    }
}
