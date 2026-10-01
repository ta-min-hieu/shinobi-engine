package dev.kage.client.core.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.theme.ClientLanguage;
import dev.kage.client.core.ui.theme.KageI18n;
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
 * Hội thoại          870px @ 15 lựa chọn — TRÀN      570px
 * </pre>
 *
 * Bốn cửa sổ TRÀN ngay cả với trần cũ — tức là trần "cho vừa màn hình" chưa bao giờ làm được việc
 * nó hứa ở trạng thái tệ nhất, nó chỉ giấu dữ liệu. Hòm thư tràn chủ yếu vì nội dung thư 1024 ký
 * tự (~400px) tự nở, không phải vì danh sách. Chỉnh bất kỳ chiều cao nào ở các panel thì chạy lại
 * và sửa bảng này.
 *
 * <h2>Cửa sổ thứ TÁM — hội thoại (thêm 2026-09-16)</h2>
 *
 * Lớp này đo BẢY cửa sổ suốt từ 2026-09-11, và {@code DialoguePanel} là cửa sổ có-danh-sách DUY
 * NHẤT không có mặt. Nó lọt qua vì nó không giống sáu cửa sổ kia ở đúng chỗ khiến người ta nhớ:
 * nó KHÔNG có trần {@code MAX_VISIBLE_*} nào để mà nghi ngờ, nên ô {@code ScrollingListTest}
 * "không cửa sổ nào giữ trần riêng" vẫn xanh một cách vô hại. Không có trần thì không có gì để
 * kiểm — và cũng không có gì chặn nó nở.
 *
 * Số lựa chọn ở đó KHÔNG do client quyết: nó bằng số nhánh trong {@code town_elder.js}, và nhánh
 * chỉ thêm chứ chưa bao giờ bớt. V47 đẩy {@code about_town} lên 15 lựa chọn ⇒ đo được <b>870px</b>;
 * V48 đẩy tiếp lên <b>20</b>, và đó chính là thứ vùng cuộn sinh ra để chịu.
 *
 * ⚠️ Và hướng tràn ở đây NGƯỢC với sáu cửa sổ kia: khung hội thoại neo ở ĐÁY rồi nở LÊN TRÊN, nên
 * thứ bị đẩy ra ngoài màn hình là ĐỈNH khung — <b>chính lời thoại của NPC</b> cùng những lựa chọn
 * ĐẦU danh sách, chứ không phải mấy nhánh mới thêm ở cuối. Người chơi thấy một khung cụt đầu.
 */
public final class HudLayoutBudgetTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(HudLayoutBudgetTool.class);

    /** Đúng kích thước cửa sổ game thật ({@code DesktopLauncher}). */
    private static final int SCREEN_WIDTH_PX = 1280;

    private static final int SCREEN_HEIGHT_PX = 720;

    /** Đủ để nhãn xuống hàng nhận bề rộng ở lượt đầu rồi tính lại chiều cao ở lượt sau. */
    private static final int LAYOUT_PASSES = 4;

    private static final String ROWS_PROPERTY = "kage.layout.rows";

    /** Sai số làm tròn của một lượt layout; dưới ngưỡng này thì không phải tràn thật. */
    private static final float OVERFLOW_TOLERANCE_PX = 1f;

    /** Kết quả vượt trần — đọc lại ở {@link #main} SAU khi vòng lặp GL đã đóng. */
    private static final List<String> FAILURES = new ArrayList<>();

    private static final String LONG_NAME = "PhongVanTuyetNguyet";

    private final int rows = Integer.getInteger(ROWS_PROPERTY, ScrollingList.MAX_BUILT_ROWS);

    private KageSkin skin;
    private final List<HudWindow> built = new ArrayList<>();

    @Override
    public void create() {
        // -PkageLanguage=en: đo/chụp bằng bundle tiếng Anh (song ngữ, 2026-10-01). Phải trước mọi load().
        KageI18n.useLanguage(
                ClientLanguage.fromCode(System.getProperty("kage.language")).orElse(ClientLanguage.DEFAULT));
        skin = new KageSkin();
        LOG.info("Đo {} cửa sổ ở {} dòng mỗi danh sách, màn hình {}x{}", 8, rows, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);
        measureMail();
        measureShop();
        measureGuild();
        measureTrade();
        measureFriend();
        measureQuestLog();
        measureInventory();
        measureDialogue();
        Gdx.app.exit();
    }

    /**
     * Khung hội thoại ở node ĐÔNG NHẤT của cây: {@code about_town} của Chu Hoành.
     *
     * <p>Số lựa chọn ở đây KHÔNG do client quyết — nó bằng số nhánh trong {@code town_elder.js}, và
     * nhánh chỉ có thêm chứ chưa bao giờ bớt: mỗi quest mới là một dòng nữa. V47 đẩy nó lên **15**
     * (14 nhánh + "tạm biệt"), và ba nhánh mới nhất nằm ở chỉ số 11/12/13 — tức gần cuối.
     *
     * <p>⚠️ Khác bảy cửa sổ trên: khung này KHÔNG có vùng cuộn và KHÔNG có trần, nên nó không "giấu
     * bớt" dòng như các danh sách kia từng làm — nó nở thẳng ra ngoài màn hình. Danh sách khoá dưới
     * đây là danh sách THẬT, chép từ nhật ký {@code gameProbe} chứ không bịa, để con số đo được là
     * trạng thái người chơi thật gặp.
     */
    private void measureDialogue() {
        DialoguePanel panel = new DialoguePanel(skin);
        List<String> options = new ArrayList<>();
        options.add("dialogue.option.ask_about_realm");
        options.add("dialogue.option.ask_about_elder");
        options.add("dialogue.option.ask_night_watch");
        options.add("dialogue.option.ask_old_archive");
        options.add("dialogue.option.ask_awakening_trial");
        options.add("dialogue.option.ask_spirit_vein");
        options.add("dialogue.option.ask_first_manual");
        options.add("dialogue.option.ask_raider_band");
        options.add("dialogue.option.ask_outer_sect");
        options.add("dialogue.option.ask_missing_disciple");
        options.add("dialogue.option.ask_confront_steward");
        options.add("dialogue.option.ask_dead_vein");
        options.add("dialogue.option.ask_blood_sand_trail");
        options.add("dialogue.option.ask_scatter_band");
        options.add("dialogue.option.ask_wasteland_edge");
        options.add("dialogue.option.ask_rank_board");
        options.add("dialogue.option.ask_pill_run");
        options.add("dialogue.option.ask_proving");
        options.add("dialogue.option.ask_breakthrough");
        options.add("dialogue.option.bye");
        panel.show("dialogue.town_elder.about_town", options);
        measure("Hội thoại", panel, panel::preferredHeight);
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
        measureOverflowWidth(name, window);
    }

    /**
     * Đo chiều NGANG: nội dung bên trong mỗi vùng cuộn có rộng hơn chính vùng cuộn không.
     *
     * <p>Vì sao cần, và vì sao phép đo chiều cao ở trên KHÔNG đủ: mọi vùng cuộn HUD khoá trục ngang
     * ({@link ScrollingList#wrap}), nên bảng con rộng hơn viewport KHÔNG sinh thanh cuộn — nó bị căn
     * giữa và **tràn ra cả hai mép**, ăn mất chữ đầu của cột trái lẫn nút ở cột phải. Đó đúng là lỗi
     * khu "Trang bị" của túi đồ: nhãn hiện ra là "áp khí"/"o bào" thay vì "Pháp khí"/"Đạo bào", thấy
     * lúc PLAYTEST SỐNG 2026-09-12, trong khi công cụ này vẫn báo xanh vì khung vẫn VỪA chiều cao.
     */
    private void measureOverflowWidth(String name, HudWindow window) {
        for (ScrollPane pane : scrollPanes(window.stage().getRoot())) {
            Actor content = pane.getActor();
            if (!(content instanceof Layout laidOut)) {
                continue;
            }
            // Đại lượng đúng là chiều rộng TỐI THIỂU, không phải getWidth() (trục ngang bị khoá nên
            // ScrollPane đã ép nó bằng vùng nhìn — mọi cửa sổ ra cùng một con số vô nghĩa) và cũng
            // không phải getPrefWidth() (một danh sách co giãn LUÔN muốn rộng hơn; đó là bình
            // thường). Tràn xảy ra đúng khi nội dung TỪ CHỐI co xuống dưới vùng nhìn — tức
            // getMinWidth() > vùng nhìn. Cả hai lần đo sai trước đó đều vì lẫn ba đại lượng này.
            float available = pane.getScrollWidth();
            float needed = laidOut.getMinWidth();
            boolean fits = needed <= available + OVERFLOW_TOLERANCE_PX;
            LOG.info(
                    "  vùng cuộn {}px — nội dung tối thiểu {}px, mong muốn {}px ({})",
                    Math.round(available),
                    Math.round(needed),
                    Math.round(laidOut.getPrefWidth()),
                    fits ? "co vừa" : "TRÀN NGANG");
            if (!fits) {
                FAILURES.add(name + " tràn NGANG " + Math.round(needed - available) + "px (tối thiểu "
                        + Math.round(needed) + "px / vùng cuộn " + Math.round(available) + "px)");
            }
        }
    }

    private static List<ScrollPane> scrollPanes(Group group) {
        List<ScrollPane> found = new ArrayList<>();
        for (Actor child : group.getChildren()) {
            if (child instanceof ScrollPane pane) {
                found.add(pane);
            }
            if (child instanceof Group nested) {
                found.addAll(scrollPanes(nested));
            }
        }
        return found;
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
            // Đúng cái game thật dựng: nhãn slot dài nhất của bundle + mô tả HAI dòng, dòng dưới là
            // chuỗi chỉ số dài nhất có thể. Bản cũ dùng "Trang sức 1"/"Trường kiếm thẳng +15" — ngắn
            // hơn thực tế, nên nó KHÔNG chạm tới lỗi tràn ngang có thật ở khung này.
            equipment.add(new InventoryPanel.EquipmentRow(
                    slot,
                    "Ngọc bội 1",
                    "Áo Vải Tạp Dịch +15" + System.lineSeparator() + "Chỉ số gốc: Giáp +3, Kháng +2, Máu +8",
                    true));
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
