package dev.kage.client.core.ui.hud;

import java.util.zip.Deflater;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.theme.ClientLanguage;
import dev.kage.client.core.ui.theme.KageI18n;

/**
 * THROWAWAY — xem trước bố cục {@link CombatHud} bằng ảnh chụp, KHÔNG cần chạy server/đăng nhập.
 * Dựng lên khi chỉnh HUD theo ảnh Ninja School Online chủ dự án gửi (2026-08-09): vòng lặp
 * "sửa → đăng nhập → nhìn" quá chậm để căn chỉnh giao diện.
 *
 * <p>Chạy: {@code ./gradlew :client:client-core:runHudPreview}. Xoá cùng công cụ chẩn đoán khác khi
 * HUD đã chốt.
 */
public final class HudPreviewTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(HudPreviewTool.class);
    private static final String OUTPUT = "E:/study/libgdx/shinobi-engine/.debug-orientation/hud.png";

    /**
     * Ảnh THỨ HAI. Hai cửa sổ che giữa màn hình (hội thoại NPC và Nhóm) không bao giờ mở cùng lúc
     * trong game thật — {@code WorldScreen.talkToNpcAt} đóng cửa sổ Nhóm trước khi mở khung thoại —
     * nên chụp cả hai vào một ảnh chỉ cho ra một cái đè lên cái kia, không kiểm được bố cục của cái
     * bị che. Chụp làm hai lượt là cách duy nhất nhìn thấy cả hai.
     */
    private static final String OUTPUT_PARTY = "E:/study/libgdx/shinobi-engine/.debug-orientation/hud-party.png";

    /** Ảnh THỨ BA — cửa sổ Bạn bè. Cũng neo giữa màn hình nên cũng phải chụp riêng. */
    private static final String OUTPUT_FRIEND = "E:/study/libgdx/shinobi-engine/.debug-orientation/hud-friend.png";

    /** Ảnh THỨ TƯ — cửa sổ Bang hội. */
    private static final String OUTPUT_GUILD = "E:/study/libgdx/shinobi-engine/.debug-orientation/hud-guild.png";

    /** Ảnh THỨ NĂM — túi đồ + trang bị, gồm hàng Cường hoá (2026-08-29). Cũng neo giữa nên chụp riêng. */
    private static final String OUTPUT_INVENTORY =
            "E:/study/libgdx/shinobi-engine/.debug-orientation/hud-inventory.png";

    /** Khung chụp ảnh thứ nhất — đủ để Scene2D layout xong và font FreeType nạp xong. */
    private static final int FIRST_SHOT_FRAME = 30;

    private static final int SECOND_SHOT_FRAME = 60;

    private static final int THIRD_SHOT_FRAME = 90;

    private static final int FOURTH_SHOT_FRAME = 120;

    private static final int FIFTH_SHOT_FRAME = 150;

    /**
     * Đủ để lấp đầy vùng cuộn túi đồ và NHÌN icon ở cỡ ô túi. KHÔNG phải trần thật
     * ({@link ScrollingList#MAX_BUILT_ROWS}) — danh sách cuộn nên số dòng không đổi chiều cao khung;
     * chiều cao ở trạng thái tệ nhất đo ở {@code HudLayoutBudgetTool}, không ở đây.
     */
    private static final int MAX_VISIBLE_SLOTS_PREVIEW = 10;

    private CombatHud hud;
    private DialoguePanel dialoguePanel;
    private ChatPanel chatPanel;
    private PartyPanel partyPanel;
    private FriendPanel friendPanel;
    private GuildPanel guildPanel;
    private InventoryPanel inventoryPanel;
    private int frame;

    @Override
    public void create() {
        // -PkageLanguage=en: đo/chụp bằng bundle tiếng Anh (song ngữ, 2026-10-01). Phải trước mọi load().
        KageI18n.useLanguage(
                ClientLanguage.fromCode(System.getProperty("kage.language")).orElse(ClientLanguage.DEFAULT));
        hud = new CombatHud();
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        // Dữ liệu giả nhưng ĐÚNG dải giá trị thật (HP/Khí tối đa 100 — CombatConstants) để bố cục
        // phải chịu đúng độ dài chuỗi mà game thật sinh ra.
        hud.setLevel(12);
        hud.setPlayerHp(72, 100);
        hud.setPlayerChi(45, 100);
        // Vạch EXP (2026-08-09) — số thật của cấp 12 theo ExperienceCurve (100 × cấp = 1200).
        hud.setExperience(430L, 1200L);
        hud.setTarget("training_dummy", 38, 60);
        // Trạng thái SẴN SÀNG — đây mới là trạng thái người chơi thấy phần lớn thời gian; xem
        // trước ở trạng thái hồi chiêu (nút xám) sẽ đánh giá sai độ nổi bật của nút.
        // Tên chiêu DÀI NHẤT trong 5 hệ, không phải chiêu của hệ mặc định: nhãn tên chiêu nở theo
        // chữ và đẩy cả cụm nút góc phải, nên xem trước bằng cái ngắn là không đo được gì.
        hud.setSkillState("hud.skill.tho_trong_kich", 0f);
        hud.setSkillReady(true);
        // Nhiệm vụ ĐÃ HOÀN THÀNH — trạng thái duy nhất có thêm nút, tức là trạng thái dễ vỡ bố cục
        // nhất trong hai trạng thái của cụm này.
        hud.setQuest("kill_training_dummies", true);
        // Độ trễ đường truyền (2026-08-29, opcode PONG) — con số 3 chữ số là trường hợp RỘNG nhất
        // mà cột phải phải chứa nổi, hẹp hơn thì không kiểm được gì.
        hud.setLatency(148L);

        // Khung hội thoại (2026-08-09) — ĐÚNG nội dung cây town_elder.js, để bố cục phải chịu đúng
        // độ dài câu tiếng Việt thật chứ không phải chuỗi ngắn tự bịa.
        dialoguePanel = new DialoguePanel(hud.skin());
        dialoguePanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        dialoguePanel.show(
                "dialogue.town_elder.greeting",
                java.util.List.of(
                        "dialogue.option.ask_about_town",
                        "dialogue.option.accept_quest_kill_dummies",
                        "dialogue.option.open_shop",
                        "dialogue.option.bye"));

        // Khung chat (2026-08-25) — nhật ký ĐẦY (8 dòng, trần thật của ChatController) + ô nhập
        // đang mở: hai thứ cùng lúc là trạng thái CAO NHẤT của khung này, tức là trạng thái duy
        // nhất đủ để thấy nó có đè lên thanh gợi ý phím hay nút Đánh hay không.
        chatPanel = new ChatPanel(hud.skin(), 6);
        chatPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        chatPanel.setChannelLabel("Thế giới"); // nhãn DÀI NHẤT — nút phải chứa nổi nó, xem preview cũ
        chatPanel.setLines(
                java.util.List.of(
                        "Nhấn T để chat. /w <tên> <nội dung> = nhắn riêng.",
                        "[Thế giới] KageHoa: có ai đi bãi tập không, mình thiếu 2 con nữa là xong nhiệm vụ",
                        "[Khu vực] PhongVan: ai bán kunai rẻ không",
                        "[Riêng] ThoSon: ê, lát nữa lập nhóm nhé",
                        "Nhắn chậm thôi!",
                        "[Khu vực] KageHoa: xong rồi, cảm ơn cả nhà — hẹn gặp lại!"),
                java.util.List.of(
                        ChatPanel.LineKind.SYSTEM,
                        ChatPanel.LineKind.WORLD,
                        ChatPanel.LineKind.AREA,
                        ChatPanel.LineKind.PRIVATE,
                        ChatPanel.LineKind.SYSTEM,
                        ChatPanel.LineKind.AREA));
        chatPanel.openComposer();

        // Cửa sổ Nhóm (2026-08-25) — trạng thái ĐẦY ĐỦ NHẤT: đang là trưởng nhóm (có nút Đuổi cạnh
        // mỗi thành viên) VÀ đang có lời mời chờ trả lời. Đó là trạng thái nhiều actor nhất, tức là
        // trạng thái duy nhất đủ để thấy bố cục có vỡ hay không.
        partyPanel = new PartyPanel(hud.skin());
        partyPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        partyPanel.setContent(
                "Nhóm 3 người — bạn là trưởng nhóm.",
                java.util.List.of(
                        new PartyPanel.MemberRow("KageHoa (trưởng nhóm)", "KageHoa", true),
                        new PartyPanel.MemberRow("ThuyLong", "ThuyLong", true),
                        new PartyPanel.MemberRow("PhongVanTuyetNguyet", "PhongVanTuyetNguyet", false)),
                true,
                java.util.List.of(new PartyPanel.InviteRow(77L, "Lời mời vào nhóm #5")));
        partyPanel.setActionsEnabled(true, true);
        partyPanel.openForPreview();

        // Bạn bè + Bang hội (2026-08-25, làm ở luồng song song) — dựng ở đây để BỐ CỤC của chúng
        // cũng bị nhìn bằng ảnh chứ không chỉ bằng build xanh.
        friendPanel = new FriendPanel(hud.skin());
        friendPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        friendPanel.setFriends(java.util.List.of(
                new FriendPanel.FriendRow("ThuyLong", true),
                new FriendPanel.FriendRow("PhongVanTuyetNguyet", false),
                new FriendPanel.FriendRow("ThoSon", true)));
        friendPanel.setPendingRequests(java.util.List.of(new FriendPanel.RequestRow("LoiDien")));
        friendPanel.setNotice("Đã gửi lời mời kết bạn.");

        guildPanel = new GuildPanel(hud.skin());
        guildPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        guildPanel.setStatus("Bang Hắc Long — 3 thành viên, bạn là bang chủ.");
        guildPanel.setMembers(
                java.util.List.of(
                        new GuildPanel.MemberRow("KageHoa (bang chủ)", "KageHoa", false),
                        new GuildPanel.MemberRow("ThuyLong", "ThuyLong", true),
                        new GuildPanel.MemberRow("PhongVanTuyetNguyet", "PhongVanTuyetNguyet", true)),
                "");
        guildPanel.setPendingRequests(java.util.List.of(new GuildPanel.RequestRow(12L, "LoiDien xin gia nhập")), "");

        buildInventoryPreview();
    }

    /**
     * Túi đồ ở trạng thái ĐẦY ĐỦ NHẤT: đã chọn một món (bày cả bốn nút Mặc/Bán/Giao dịch/Cường hoá
     * + công tắc Bảo Vệ), đang trong phiên giao dịch, và có món ĐANG MẶC ở nhiều ô. Đó là trạng
     * thái nhiều actor nhất trên một hàng, tức là trạng thái duy nhất đủ để thấy bố cục có vỡ không.
     *
     * <p>Tách khỏi {@link #create()} để giữ nó dưới ngưỡng {@code MethodLength} của Checkstyle.
     */
    private void buildInventoryPreview() {
        inventoryPanel = new InventoryPanel(hud.skin());
        inventoryPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        inventoryPanel.setGold("1.240 vàng");
        inventoryPanel.setStatus("Cường hoá thất bại — Bảo vệ đã chặn rớt cấp, tiêu 1 bùa.");
        // TÚI ĐẦY: vùng cuộn đầy + dòng "còn n món chưa hiện" + cả 7 ô đều có đồ — trạng thái nhiều
        // actor nhất để NHÌN. Chiều cao khung ở trạng thái tệ nhất thì ĐO bằng HudLayoutBudgetTool.
        // Mã icon lấy từ V21 chứ không phải 3 mã cũ: đây là chỗ DUY NHẤT chứng minh vùng
        // icon_<code> mới thật sự có trong atlas và client tra ra được (2026-08-30). Ô đầu nay là
        // `minor_healing_pill` (V36, ADR-046 D5) — icon MỚI NHẤT luôn nên đứng ở đây, vì nó là cái
        // chưa ai từng nhìn thấy ở cỡ ô túi.
        java.util.List<InventoryPanel.ItemRow> full = new java.util.ArrayList<>();
        String[] codes = {
            "minor_healing_pill", "genin_vest", "genin_headband", "genin_wraps", "genin_sandals",
            "genin_bracelet", "genin_necklace", "novice_vest", "iron_scrap", "chakra_shard"
        };
        String[] names = {
            "Tiểu Hồi Nguyên Đan x3", "Áo Giáp Hạ Nhẫn", "Mũ Trụ Hạ Nhẫn", "Găng Tay Hạ Nhẫn", "Giày Hạ Nhẫn",
            "Vòng Tay Hạ Nhẫn", "Dây Chuyền Hạ Nhẫn", "Áo Vải Tập Sự", "Sắt Vụn x40", "Mảnh Chakra x7"
        };
        for (int index = 0; index < MAX_VISIBLE_SLOTS_PREVIEW; index++) {
            full.add(new InventoryPanel.ItemRow(index + 1L, codes[index], names[index]));
        }
        inventoryPanel.setItems(full, MAX_VISIBLE_SLOTS_PREVIEW + "/30 ô", "... còn 4 vật phẩm nữa chưa hiện.");
        inventoryPanel.setEquipment(java.util.List.of(
                new InventoryPanel.EquipmentRow(1, "Vũ khí", "Kiếm gỗ +9", true),
                new InventoryPanel.EquipmentRow(2, "Áo giáp", "Áo vải thô", true),
                new InventoryPanel.EquipmentRow(3, "Mũ", "Khăn trùm đầu", true),
                new InventoryPanel.EquipmentRow(4, "Găng tay", "Bao tay vải", true),
                new InventoryPanel.EquipmentRow(5, "Giày", "Giày rơm", true),
                new InventoryPanel.EquipmentRow(6, "Trang sức 1", "Vòng gỗ", true),
                new InventoryPanel.EquipmentRow(7, "Trang sức 2", "(Trống)", false)));
        inventoryPanel.setProtection(true);
        // ADR-046 D5 — chọn ĐAN DƯỢC chứ không phải trang bị: đây là chỗ DUY NHẤT nhìn thấy nút
        // "Dùng" (nó dùng CHUNG ô nút với "Mặc", xem javadoc InventoryPanel#primaryButton) và cũng
        // là chỗ duy nhất kiểm được nhãn tiếng Việt có tràn ra ngoài nền nút hay không —
        // đúng lỗi đã bắt ở QuestLogPanel. Dòng "Hồi 50 máu" đến từ ItemCatalogEntry.use_value.
        inventoryPanel.showDetail(
                "Tiểu Hồi Nguyên Đan x3" + System.lineSeparator() + "Hồi 50 máu",
                InventoryPanel.PrimaryAction.USE,
                new InventoryPanel.Actions(true, true, true));
    }

    @Override
    public void render() {
        // Nền xanh rêu giả lập màu map, để thấy HUD tương phản ra sao trên nền sáng chứ không phải
        // trên nền đen (nền đen làm panel tối trông "chìm" hơn thực tế).
        Gdx.gl.glClearColor(0.22f, 0.42f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.render(1f / 60f);
        chatPanel.render(1f / 60f);
        partyPanel.render(1f / 60f);
        friendPanel.render(1f / 60f);
        guildPanel.render(1f / 60f);
        inventoryPanel.render(1f / 60f);
        dialoguePanel.render(1f / 60f);
        frame++;
        if (frame == FIRST_SHOT_FRAME) {
            capture(OUTPUT);
            // Đổi vai: giấu khung thoại đi để lượt sau nhìn thấy trọn cửa sổ Nhóm.
            dialoguePanel.hide();
            partyPanel.openForPreview();
        } else if (frame == SECOND_SHOT_FRAME) {
            capture(OUTPUT_PARTY);
            partyPanel.close();
            friendPanel.open();
        } else if (frame == THIRD_SHOT_FRAME) {
            capture(OUTPUT_FRIEND);
            friendPanel.close();
            guildPanel.open();
        } else if (frame == FOURTH_SHOT_FRAME) {
            capture(OUTPUT_GUILD);
            guildPanel.close();
            inventoryPanel.open();
        } else if (frame == FIFTH_SHOT_FRAME) {
            capture(OUTPUT_INVENTORY);
            LOG.info(
                    "Túi đồ ở trạng thái ĐẦY cao {}px, màn hình cao {}px",
                    inventoryPanel.preferredHeight(),
                    Gdx.graphics.getHeight());
            Gdx.app.exit();
        }
    }

    private static void capture(String path) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.files.absolute(path).parent().mkdirs();
        // flipY=true BẮT BUỘC — xem WorldModelRenderer.Y_UP_TO_Z_UP về lần dính bẫy này.
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        pixmap.dispose();
        LOG.info("Đã chụp {}", path);
    }

    @Override
    public void dispose() {
        if (dialoguePanel != null) {
            dialoguePanel.dispose(); // TRƯỚC hud: panel mượn skin của hud
        }
        if (chatPanel != null) {
            chatPanel.dispose(); // TRƯỚC hud, cùng lý do dialoguePanel
        }
        if (partyPanel != null) {
            partyPanel.dispose();
        }
        if (friendPanel != null) {
            friendPanel.dispose();
        }
        if (guildPanel != null) {
            guildPanel.dispose();
        }
        if (inventoryPanel != null) {
            inventoryPanel.dispose();
        }
        if (hud != null) {
            hud.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage - HUD preview (cong cu, khong phai game)");
        config.setWindowedMode(1280, 720); // đúng kích thước cửa sổ game thật (DesktopLauncher)
        config.useVsync(false);
        new Lwjgl3Application(new HudPreviewTool(), config);
    }
}
