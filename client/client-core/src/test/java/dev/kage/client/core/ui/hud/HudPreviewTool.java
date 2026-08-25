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

    /** Khung chụp ảnh thứ nhất — đủ để Scene2D layout xong và font FreeType nạp xong. */
    private static final int FIRST_SHOT_FRAME = 30;

    private static final int SECOND_SHOT_FRAME = 60;

    private CombatHud hud;
    private DialoguePanel dialoguePanel;
    private ChatPanel chatPanel;
    private PartyPanel partyPanel;
    private int frame;

    @Override
    public void create() {
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
        hud.setSkillState("hud.skill.fireball", 0f);
        hud.setSkillReady(true);
        // Nhiệm vụ ĐÃ HOÀN THÀNH — trạng thái duy nhất có thêm nút, tức là trạng thái dễ vỡ bố cục
        // nhất trong hai trạng thái của cụm này.
        hud.setQuest("kill_training_dummies", true);

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
        dialoguePanel.render(1f / 60f);
        frame++;
        if (frame == FIRST_SHOT_FRAME) {
            capture(OUTPUT);
            // Đổi vai: giấu khung thoại đi để lượt sau nhìn thấy trọn cửa sổ Nhóm.
            dialoguePanel.hide();
            partyPanel.openForPreview();
        } else if (frame == SECOND_SHOT_FRAME) {
            capture(OUTPUT_PARTY);
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
