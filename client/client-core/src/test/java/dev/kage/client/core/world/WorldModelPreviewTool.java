package dev.kage.client.core.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.NpcSpawnedEvent;

/**
 * Chụp ảnh quái đúng như game thật vẽ chúng — {@code ./gradlew :client:client-core:runMonsterPreview},
 * không cần server, xong trong vài giây.
 *
 * <p>Cùng vai trò với {@code HudPreviewTool} và cùng lý do tồn tại: có những thứ build gate <b>không
 * bắt được</b>. Một con quái xoay sai hướng, một dấu mục tiêu cắm vào giữa đầu nó, một model đứng
 * bind pose vì clip không tồn tại — tất cả đều compile sạch, test xanh, và chỉ lộ ra khi nhìn. Bài
 * học đã trả giá hai lần ở dự án này (RENDERING.md §11.1) là <b>đường chụp phải được kiểm chứng
 * trước khi tin vào ảnh</b>, nên công cụ này chụp qua đúng {@link #capture} có {@code flipY=true} và
 * vẽ qua đúng lớp production ({@link MonsterPresenter}, {@link WorldModelRenderer}) chứ không chép
 * tay lại phép vẽ.
 *
 * <p>Khung hình cố ý đặt cạnh nhau những thứ phải phân biệt được: nhân vật (đã có model từ
 * ADR-031), bốn loài quái ĐÃ có model ({@code training_dummy}, {@code wandering_bandit},
 * {@code stone_ogre}, {@code archive_warden} — trước 2026-08-30 ba loài đầu dùng chung một model và
 * trông y hệt nhau, rồi V30 lặp lại đúng chuyện đó với loài thứ tư cho tới V31), và một mã quái
 * cố ý KHÔNG có model ({@code preview_undrawn_monster}, hộp đỏ lơ lửng — vai {@code sample_boss}
 * đóng tới 2026-09-30) — nếu nhánh fallback từng bị hỏng, ảnh này là chỗ thấy ngay.
 */
public final class WorldModelPreviewTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(WorldModelPreviewTool.class);

    /**
     * Thư mục ảnh, tuyệt đối — cwd của công cụ là gốc asset ({@code assets/exported}, để
     * {@code Gdx.files.internal} tra được model), và đó là cây tài sản của pipeline art chứ không
     * phải chỗ đổ output. Task Gradle truyền đường dẫn thật vào.
     */
    private static final String OUTPUT_DIR = System.getProperty("kage.preview.out", "build/preview-world") + "/";

    private static final long CHARACTER_ID = 1L;

    /** Id âm = quái, đúng quy ước server ({@code WorldFacade.nextMonsterOrNpcId} đếm lùi từ −1). */
    private static final long DUMMY_ID = -1L;

    private static final long BOSS_ID = -2L;

    private static final long ELDER_ID = -3L;

    /** Mã NPC chưa có art — chứng minh nhánh hộp thay thế của NpcPresenter còn sống. */
    private static final long UNDRAWN_NPC_ID = -4L;

    /** Hai loài ADR-037. Có mặt ở đây vì lý do chúng được vẽ: ba loài từng trông y hệt nhau. */
    private static final long BANDIT_ID = -5L;

    private static final long OGRE_ID = -6L;

    /**
     * Loài của V30. Đứng NGAY CẠNH quỷ đá có chủ đích: V30 cho nó mượn {@code mob_stone-ogre} và ghi
     * đó là nợ nhìn thấy được, V31 trả nợ — câu hỏi duy nhất ảnh này phải trả lời là hai con đá đó
     * còn lẫn vào nhau nữa không.
     */
    private static final long WARDEN_ID = -7L;

    /**
     * Loài của V33, và lần THỨ BA dự án đi đúng vào cùng một cái bẫy: nó mượn
     * {@code mob_archive-warden} y như V30 mượn {@code mob_stone-ogre} và V23 mượn
     * {@code mob_training-dummy}. Đặt nó ngay cạnh thạch vệ — và cả hai cạnh quỷ đá — vì khung này
     * nay phải trả lời một câu khó hơn trước: BA cỗ máy đá cùng một dòng dõi có còn phân biệt được
     * không, khi lore bắt chúng phải giống nhau ở mức nào đó.
     */
    private static final long TRIAL_ID = -8L;

    /**
     * NPC THỨ HAI của dự án (V37) — Tô Vân Nhi. Đứng NGAY CẠNH Chu Hoành có chủ đích, và câu hỏi
     * khung này phải trả lời là câu đã hỏi ba lần với ba cỗ máy đá, nay hỏi cho người: hai NPC cùng
     * mặc đạo bào Thanh Hư, đứng cùng một sân, ở cỡ thật có lẫn vào nhau không?
     *
     * <p>Model của cô dựng theo luật "đảo bố cục sáng-tối" (áo SÁNG + tóc TỐI, ngược hẳn ông lão áo
     * TỐI + tóc TRẮNG) chứ không phải "cùng bộ đồ, đổi màu" — xem `build_npc_senior_sister.py`. Ảnh
     * này là chỗ DUY NHẤT chứng minh được luật đó có hiệu lực, vì nó chụp qua chính bộ dựng của
     * client (camera chúc xuống), thứ đã ba lần bác bỏ ảnh Blender.
     */
    private static final long SISTER_ID = -9L;

    /**
     * Loài của V40 — Thám Tử Huyết Sa, quái đầu tiên của Huyết Sa. Đứng NGAY CẠNH tán tu có chủ
     * đích, và đây là cặp khó nhất từ trước tới nay: ba cỗ máy đá còn có lore bắt chúng giống nhau,
     * còn hai con này đều là NGƯỜI dựng từ cùng một mesh kit, cùng dáng chibi, cùng mười một xương.
     * Thứ duy nhất tách chúng ra là bố cục sáng-tối bị ĐẢO (tán tu: da nâu vừa, mặt hở, điểm đỏ nhỏ
     * · thám tử: áo gần đen, khăn che mặt, mảng đỏ sẫm lớn) cộng dáng vũ khí khác hẳn (một đao dài
     * vs hai đao ngắn). Khung này là chỗ DUY NHẤT chứng minh được điều đó, vì nó chụp qua chính bộ
     * dựng của client — thứ đã ba lần bác bỏ ảnh Blender.
     */
    private static final long SCOUT_ID = -10L;

    /** NPC thứ ba (V49, ADR-050 D6): một `npc_template` đứng ở cả SÁU trạm tiếp ứng dọc trục. */
    private static final long WAYPOST_ID = -11L;

    /**
     * Loài của V48, Lv29. Cả hai loài V48 đã SHIP (`3a8d98a`, 2026-09-17) mà chưa lần nào đi qua bộ
     * dựng của client — không có mục nào trong ASSET-LOG và không có khung này. Đặt nó NGAY SAU thám
     * tử Huyết Sa có chủ đích: lập luận tách biệt duy nhất của nó là MÀU (ô-cạ + xanh bệnh, chứ
     * không phải họ xám/nâu/đỏ sẫm của Huyết Sa), mà lập luận về màu thì chỉ chứng minh được khi hai
     * bên đứng cạnh nhau trong CÙNG một ảnh, dưới CÙNG một đèn.
     */
    private static final long BEAST_ID = -12L;

    /**
     * Loài của V48, Lv33 — trận CUỐI của v1. Cùng dòng dõi máy Linh Triều với quỷ đá, thạch vệ và cỗ
     * khảo hạch, nên nó phải đứng NGAY SAU cỗ khảo hạch: câu hỏi cần trả lời là "cái thứ tư này có
     * đọc ra LỄ KHÍ của tông môn (ngọc nhạt + vàng) thay vì đá phong hoá như ba cái kia không".
     */
    private static final long FOUNDATION_ID = -13L;

    /** Xem {@link #UNDRAWN_MONSTER_X}. */
    private static final long UNDRAWN_MONSTER_ID = -14L;

    // Toàn bộ khung nằm TRƯỚC bệ demo đầu tiên (bắt đầu ở x=3, xem DemoPlatforms) — bệ vẽ đè lên
    // quái thì ảnh không còn chứng minh được gì về chính con quái, đúng bẫy đã gặp ở lượt chụp đầu.
    // Ba loài quái đứng CẠNH NHAU (dummy/bandit/ogre) chứ không rải ra: câu hỏi mà ảnh này phải trả
    // lời là "có phân biệt được không", và thứ tự đó là thứ tự sát thương 8/22/40.
    // ⚠️ Nới sang TRÁI, không đẩy đuôi sang phải (2026-09-18, thêm NPC thứ ba). Camera bám
    // {@code PLAYER_X + 4.65} nên hạ mốc này là khung trượt sang trái mà không con quái nào đổi
    // chỗ và không gì vượt mốc x=3 của bệ demo — đúng luật NÉN đã ghi ngay trên. Bản đầu tôi đẩy
    // hộp thay thế sang 3.6 cho rảnh chỗ, tức tự cấp ngoại lệ khỏi chính luật này.
    // 2026-09-19, thêm HAI loài V48: cùng cách nới sang trái, lần này −1,4 = đúng hai bước 0,7.
    // Cả hàng dịch trái nguyên khối, riêng {@code UNDRAWN_NPC_X} = 2,9 GIỮ NGUYÊN vì nó là mốc
    // chạm trần x=3 — nó ở lại đúng chỗ thì trần vẫn chưa bị chạm sau khi thêm người.
    private static final float PLAYER_X = -6.5f;
    private static final float DUMMY_X = -3.4f;
    // ⚠️ Thêm một thực thể vào khung này phải NÉN khoảng cách, KHÔNG được đẩy đuôi sang phải: bệ
    // demo đầu tiên bắt đầu ở x=3 (DemoPlatforms) và nó là khối 3D có depth test, nên bất cứ thứ gì
    // trôi qua mốc đó bị bệ VẼ ĐÈ và ảnh thôi chứng minh được gì về chính nó. Lần thêm thám tử
    // (2026-09-12) đã dính đúng bẫy đó ở bản đầu — ảnh ra thì cỗ khảo hạch và hai hộp thay thế nằm
    // sau một mảng olive. Bước giữa các mốc vì thế là 0.7 chứ không phải 1.15.
    private static final float BANDIT_X = -2.7f;

    /** Sát tán tu: xem javadoc {@link #SCOUT_ID} về vì sao đúng chỗ này chứ không phải chỗ khác. */
    private static final float SCOUT_X = -2.0f;

    /** Sát thám tử Huyết Sa — xem javadoc {@link #BEAST_ID}. */
    private static final float BEAST_X = -1.3f;

    private static final float OGRE_X = -0.6f;
    private static final float WARDEN_X = 0.1f;
    private static final float TRIAL_X = 0.8f;

    /** Sát cỗ khảo hạch — xem javadoc {@link #FOUNDATION_ID}. */
    private static final float FOUNDATION_X = 1.5f;

    private static final float BOSS_X = 2.2f;
    // Hai NPC xếp sát nhau hơn hàng quái (0,8 thay vì 1,15): chúng mảnh hơn hẳn ba cỗ máy đá, và
    // câu hỏi "có lẫn vào nhau không" chỉ trả lời được khi chúng thật sự đứng cạnh nhau.
    private static final float ELDER_X = -5.75f;
    private static final float SISTER_X = -4.95f;
    private static final float UNDRAWN_NPC_X = 2.9f;

    /**
     * Quái KHÔNG có art — đối chứng cho nhánh hộp thay thế của {@link MonsterPresenter}. Tới
     * 2026-09-30 vai này do {@code sample_boss} đóng (hộp đỏ cuối cùng của game); boss có model thì
     * đối chứng phải là một mã cố ý không tồn tại, cùng cách {@link #UNDRAWN_NPC_ID} làm cho NPC.
     *
     * <p>⚠️ Đứng CÙNG x với hộp NPC nhưng lơ lửng phía trên ({@link #UNDRAWN_MONSTER_Y} là trục
     * cao, không phải trục sâu). Hàng ngang không còn chỗ: khung chỉ rộng ~10 đơn vị quanh
     * {@code PLAYER_X + 4.65}, và bản đầu dịch cả hàng sang trái 0,7 để mua một ô thì chính hộp NPC
     * trôi ra khỏi mép phải — tức đổi một đối chứng lấy một đối chứng.
     */
    private static final float UNDRAWN_MONSTER_X = UNDRAWN_NPC_X;

    /**
     * Đo trên ảnh: 1,4 vẫn chồng lên hộp NPC (trục cao co hơn trục ngang dưới camera nghiêng), 3,2
     * thì tách hẳn — hai hộp phải đọc được là HAI.
     */
    private static final float UNDRAWN_MONSTER_Y = 3.2f;

    /**
     * NPC thứ ba, nối tiếp cặp cũ ở cùng bước 0,8. ⚠️ KHÔNG đặt từ x=3.0 trở đi:
     * {@code DemoPlatforms.PLATFORMS} chiếm x ∈ [3,7], [9,13], [15,19] và là khối 3D có depth
     * test, nên mọi thứ ở đó bị bệ ô-liu vẽ đè từ ngang ngực xuống. Bản đầu đặt ở 3.6 và ảnh chỉ
     * còn cái đầu với hai bàn chân — đúng bẫy mà chú thích phía trên đã ghi sẵn.
     */
    private static final float WAYPOST_X = -4.15f;

    /**
     * Phép so A/B của ÁO CHOÀNG (câu hỏi để ngỏ từ 2026-09-06): bốn trong năm bộ B3 mặc
     * {@code cloak}, mà chính phụ kiện đó vừa bị GỠ khỏi cỗ khảo hạch vì camera client nhìn gần
     * chính diện nên áo choàng treo SAU lưng bị che kín trong MỌI khung — tốn 116 tam giác mà vẽ ra
     * đúng con số không.
     *
     * <p><b>Hai ảnh CÙNG một entity, CÙNG một chỗ đứng</b>, chỉ đổi model bằng
     * {@code replaceCharacterModel} — chứ không phải hai nhân vật đứng cạnh nhau. Lần thử đầu xếp
     * cạnh nhau và ảnh KHÔNG kết luận được gì: ở cỡ thật, mảng tối bên hông có thể là áo choàng mà
     * cũng có thể là vũ khí, và hai khối lệch nhau vài pixel thì không diff được. Cùng chỗ đứng thì
     * mọi pixel khác nhau giữa hai ảnh CHỈ có thể là cloak — {@code _accessories_for} cộng dồn theo
     * bậc, nên B3 = B2 + đúng một món đó.
     *
     * <p>Chọn hệ LÔI, và KHÔNG chọn hệ THỔ dù thoạt nhìn thổ tương phản mạnh hơn: {@code char_tho-b3}
     * đã được DỰNG LẠI thành giáp kim loại xám (ART-AUDIT-TU-TIEN §5 dòng #7, chủ dự án chốt "GIỮ"),
     * nên nó KHÔNG còn là "b2 + đúng một cái cloak" và mọi phép so trên nó đo nhầm sang bộ giáp.
     * Đây là bẫy đã dính thật: ảnh đầu tiên của phép đo này là thổ, và nó suýt cho ra kết luận sai.
     * Lôi thì cloak {@code #3B2A5C} gần bằng GIÁ TRỊ với tóc {@code #1E1B2B} — trường hợp TỆ NHẤT.
     *
     * <p>⚠️ <b>Hai entity dựng CÙNG MỘT KHUNG</b> rồi thay nhau đứng vào chỗ chụp, chứ không phải
     * đổi model của một entity giữa hai lần chụp. Bản đầu làm cách sau và phép đo HỎNG: hai ảnh
     * cách nhau 10 khung nên nhân vật đã trôi sang tư thế idle khác, và 2.959 pixel "khác nhau" đếm
     * được khi đó là TỔNG của áo choàng cộng độ trôi tư thế — không tách ra được. Dựng cùng lúc thì
     * hai model chạy animation đồng pha.
     */
    private static final long CLOAK_TEST_ID = 10L;

    /** Entity thứ hai, dựng CÙNG LÚC với entity thứ nhất — xem {@link #startCloakTest()}. */
    private static final long CLOAK_TEST_ID_WITH = 11L;

    /** Chỗ đứng "ngoài khung": entity nào đang không được chụp thì dời ra đây. */
    private static final float CLOAK_TEST_OFFSCREEN_X = 500f;

    private static final String CLOAK_TEST_WITHOUT = "char_loi-b2";

    private static final String CLOAK_TEST_WITH = "char_loi-b3";

    private static final float CLOAK_TEST_X = 18.0f;

    /** Từ khung này trở đi kịch bản chuyển sang phép so áo choàng — xem {@link #cloakScript()}. */
    private static final int CLOAK_TEST_FIRST_FRAME = 96;

    /**
     * Dải chấm Q7 (ART-QUALITY-STANDARD §2: <i>"vũ khí đọc được ĐÚNG loại ở @1x"</i>) — cả NĂM hệ
     * đứng cạnh nhau trong MỘT ảnh, cầm năm hình vũ khí khác nhau của kit.
     *
     * <p>Khung này sinh ra vì Q7 của nhân vật người chơi chưa bao giờ chấm được bằng chính bộ dựng
     * của client: công cụ này chỉ vẽ {@code char_hoa-b1} (người chơi) và hai bộ Lôi (phép so áo
     * choàng). Sau 2026-09-19 thì nó thành bắt buộc — vị trí vũ khí (`seat_weapon`) và màu vũ khí
     * (`export_gltf`) đều vừa đổi, và "đọc được không" là câu hỏi CHỈ trả lời được bằng ảnh.
     *
     * <p>Bậc B1 cho cả năm: vũ khí có đủ từ bậc đó, còn phụ kiện B2/B3 chỉ thêm thứ che nó đi.
     * ⚠️ {@code char_hoa-b1} vào khung như một ĐỐI CHỨNG chứ không phải một ứng viên — file
     * {@code hoa-b1.blend} trong repo là bản dựng bằng mesh kit chứ không phải nguồn của model đang
     * ship, nên nó KHÔNG xuất lại được và vũ khí của nó vẫn giữ màu xám cũ.
     */
    private static final int WEAPON_STRIP_FIRST_FRAME = 124;

    /**
     * Tâm dải, đặt SAU bệ demo cuối cùng ({@code DemoPlatforms.PLATFORMS} chiếm tới x=19) và cách
     * xa chỗ đứng của phép so áo choàng. Nền đất kéo tới {@code GROUND_MAX_X}=210 nên vẫn có đất.
     */
    private static final float WEAPON_STRIP_X = 26.0f;

    /** Bước giữa năm nhân vật: đủ rời để không chồng silhouette, đủ gần để vào chung một khung. */
    private static final float WEAPON_STRIP_STEP = 1.2f;

    /** Thứ tự theo HÌNH vũ khí, không theo thứ tự hệ: kunai · bầu · song đao · trường kiếm · chuỳ. */
    private static final String[] WEAPON_STRIP_MODELS = {
        "char_hoa-b1", "char_thuy-b1", "char_phong-b1", "char_loi-b1", "char_tho-b1",
    };

    /** Id riêng cho dải chấm, không đụng id nào đang dùng. */
    private static final long WEAPON_STRIP_FIRST_ID = 20L;

    private final Map<Long, CombatHudController.MonsterState> monsters = new LinkedHashMap<>();

    private WorldModelRenderer models;
    private MonsterPresenter presenter;
    private NpcPresenter npcPresenter;
    private ShapeRenderer shapeRenderer;
    private int frame;

    /** Từ khung này trở đi, cảnh chỉ còn phép so áo choàng — xem {@link #CLOAK_TEST_ID}. */
    private boolean showingCloakTest;

    /** Từ khung này trở đi, cảnh chỉ còn dải chấm Q7 — xem {@link #WEAPON_STRIP_FIRST_FRAME}. */
    private boolean showingWeaponStrip;

    @Override
    public void create() {
        models = new WorldModelRenderer(GraphicsTier.MEDIUM);
        // ĐÚNG khung nhìn của game thật — kiểm ở zoom khác thì không kết luận được gì về kích thước
        // tương đối giữa nhân vật, quái và dấu mục tiêu.
        models.resize(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS);
        shapeRenderer = new ShapeRenderer();
        presenter = new MonsterPresenter(models);
        npcPresenter = new NpcPresenter(models);

        models.addCharacter(CHARACTER_ID, "char_hoa-b1");
        if (!models.isEnabled()) {
            LOG.error("Không xem được: thiếu model nhân vật dưới models/med/ — chạy pipeline art trước.");
            Gdx.app.exit();
            return;
        }
        putMonster(DUMMY_ID, "training_dummy", DUMMY_X);
        putMonster(BANDIT_ID, "wandering_bandit", BANDIT_X);
        putMonster(SCOUT_ID, "blood_sand_scout", SCOUT_X);
        putMonster(BEAST_ID, "wasteland_beast", BEAST_X);
        putMonster(OGRE_ID, "stone_ogre", OGRE_X);
        putMonster(WARDEN_ID, "archive_warden", WARDEN_X);
        putMonster(TRIAL_ID, "trial_construct", TRIAL_X);
        putMonster(FOUNDATION_ID, "foundation_warden", FOUNDATION_X);
        putMonster(BOSS_ID, "sample_boss", BOSS_X);
        putMonster(UNDRAWN_MONSTER_ID, "preview_undrawn_monster", UNDRAWN_MONSTER_X, UNDRAWN_MONSTER_Y);
        putNpc(ELDER_ID, "town_elder", ELDER_X);
        putNpc(SISTER_ID, "senior_sister", SISTER_X);
        putNpc(UNDRAWN_NPC_ID, "shop_keeper", UNDRAWN_NPC_X);
        putNpc(WAYPOST_ID, "waypost_attendant", WAYPOST_X);
        logWhichModelsLoaded();
    }

    /** Model nào đã nạp được — để đọc log là biết art nào còn thiếu, không cần nhìn ảnh. */
    private void logWhichModelsLoaded() {
        LOG.info(
                "model quái: training_dummy={} wandering_bandit={} blood_sand_scout={} "
                        + "wasteland_beast={} stone_ogre={} archive_warden={} trial_construct={} "
                        + "foundation_warden={} sample_boss={} preview_undrawn_monster={} "
                        + "(preview_undrawn_monster=false là ĐÚNG, mã quái cố ý không tồn tại)",
                models.hasModel(DUMMY_ID),
                models.hasModel(BANDIT_ID),
                models.hasModel(SCOUT_ID),
                models.hasModel(BEAST_ID),
                models.hasModel(OGRE_ID),
                models.hasModel(WARDEN_ID),
                models.hasModel(TRIAL_ID),
                models.hasModel(FOUNDATION_ID),
                models.hasModel(BOSS_ID),
                models.hasModel(UNDRAWN_MONSTER_ID));
        LOG.info(
                "model NPC: town_elder={} senior_sister={} shop_keeper={} "
                        + "waypost_attendant={} (shop_keeper=false là ĐÚNG, mã NPC chưa tồn tại)",
                models.hasModel(ELDER_ID),
                models.hasModel(SISTER_ID),
                models.hasModel(UNDRAWN_NPC_ID),
                models.hasModel(WAYPOST_ID));
    }

    private void putNpc(long npcId, String npcCode, float x) {
        npcPresenter.onSpawned(NpcSpawnedEvent.newBuilder()
                .setNpcId(npcId)
                .setNpcCode(npcCode)
                .setX(x)
                .setY(0f)
                .build());
    }

    /** Đi qua đúng {@link MonsterPresenter#onSpawned} thay vì tự gọi {@code addMonsterOrNpc}. */
    private void putMonster(long monsterId, String monsterCode, float x) {
        putMonster(monsterId, monsterCode, x, 0f);
    }

    private void putMonster(long monsterId, String monsterCode, float x, float y) {
        monsters.put(monsterId, new CombatHudController.MonsterState(monsterCode, x, y, 100, 100));
        presenter.onSpawned(dev.kage.protocol.MonsterSpawnedEvent.newBuilder()
                .setMonsterId(monsterId)
                .setMonsterCode(monsterCode)
                .setX(x)
                .setY(y)
                .setCurrentHp(100)
                .setMaxHp(100)
                .build());
    }

    @Override
    public void render() {
        if (models == null || !models.isEnabled()) {
            return;
        }
        frame++;
        // WorldModelRenderer.render() KHÔNG tự clear (trách nhiệm của caller) — thiếu dòng này thì
        // ảnh ra toàn đen, bẫy đã dính thật ở công cụ chẩn đoán trước.
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (showingWeaponStrip) {
            renderWeaponStrip();
            script();
            return;
        }
        if (showingCloakTest) {
            renderCloakTest();
            script();
            return;
        }

        models.setPosition(CHARACTER_ID, PLAYER_X, 0f, 0f);
        // Y HỆT WorldScreen.renderModels: sync trước, follow, rồi render.
        presenter.sync(monsters, PLAYER_X);
        npcPresenter.sync(PLAYER_X);
        models.follow(PLAYER_X + 4.65f, 0f); // lùi camera ra giữa để cả 14 đối tượng vào khung
        models.render(1f / 60f);
        DemoPlatforms.render(shapeRenderer, models.combined());
        presenter.render(shapeRenderer, models.combined(), monsters, DUMMY_ID);
        npcPresenter.render(shapeRenderer, models.combined());
        presenter.tick(1f / 60f);

        script();
    }

    /**
     * Diễn lần lượt từng trạng thái đáng nhìn. Số khung để clip kịp vào giữa động tác chứ không bắt
     * đúng khung đầu (crossfade 0.15s = 9 khung) — chụp quá sớm thì mọi tư thế trông như nhau.
     */
    private void script() {
        if (frame >= WEAPON_STRIP_FIRST_FRAME) {
            weaponScript();
            return;
        }
        if (frame >= CLOAK_TEST_FIRST_FRAME) {
            cloakScript();
            return;
        }
        openingScript();
    }

    /**
     * Phần một: bốn tư thế của cảnh chính. Tách khỏi {@link #script()} khi phần ba ra đời — chỉ
     * thêm MỘT nhánh rẽ nữa là {@code script()} chạm trần {@code CyclomaticComplexity} = 10 của PMD,
     * đúng bức tường đã tách {@link #cloakScript()} ra trước đó.
     */
    private void openingScript() {
        switch (frame) {
            case 60 -> capture("1-idle-va-fallback");
            case 61 -> feedMovement();
            case 75 -> capture("2-run");
            case 76 -> models.playAttack(DUMMY_ID);
            case 82 -> capture("3-attack");
            case 83 -> models.playDie(DUMMY_ID);
            case 95 -> capture("4-die");
            default -> {
                /* các khung còn lại chỉ để animation chạy tiếp */
            }
        }
    }

    /**
     * Nửa sau của kịch bản: phép so A/B áo choàng. Tách khỏi {@link #script()} vì gộp chung làm
     * switch vượt trần {@code CyclomaticComplexity} của PMD — cùng bức tường mà
     * {@code InventoryCoordinator} đâm vào hôm nay.
     */
    private void cloakScript() {
        switch (frame) {
            case CLOAK_TEST_FIRST_FRAME -> startCloakTest();
            case 110 -> capture("5a-loi-b2-khong-ao-choang");
            // Chụp NGAY khung sau: một khung 60Hz là độ trôi idle nhỏ nhất có thể, và cả hai model
            // đã chạy cùng số khung kể từ lúc dựng nên chúng đồng pha.
            case 111 -> swapCloakTest();
            case 112 -> capture("5b-loi-b3-co-ao-choang");
            // ĐỐI CHỨNG: CÙNG một model, cách 2 khung — đúng khoảng cách thời gian giữa 5a và 5b.
            // Không có ô này thì mọi con số "pixel khác nhau" ở trên đều lẫn độ trôi tư thế idle, và
            // đó chính là lỗi đã làm hỏng phép đo lần đầu.
            case 114 -> capture("5c-doi-chung-cung-model-2-khung-sau");
            default -> {
                /* các khung còn lại chỉ để animation chạy tiếp */
            }
        }
    }

    /**
     * Phần ba: dải chấm Q7. Tách hẳn khỏi hai phần trên vì cùng một lý do PMD
     * {@code CyclomaticComplexity} đã tách {@link #cloakScript()}.
     */
    private void weaponScript() {
        switch (frame) {
            case WEAPON_STRIP_FIRST_FRAME -> startWeaponStrip();
            // Đủ khung để crossfade 0,15s (9 khung) về hẳn idle trên CẢ NĂM model.
            case 140 -> capture("6-q7-vu-khi-nam-he");
            case 146 -> Gdx.app.exit();
            default -> {
                /* các khung còn lại chỉ để animation chạy tiếp */
            }
        }
    }

    /** Dựng năm nhân vật thành một hàng và dời camera sang đó. */
    private void startWeaponStrip() {
        models.setPosition(CLOAK_TEST_ID, CLOAK_TEST_OFFSCREEN_X, 0f, 0f);
        models.setPosition(CLOAK_TEST_ID_WITH, CLOAK_TEST_OFFSCREEN_X, 0f, 0f);
        float left = WEAPON_STRIP_X - WEAPON_STRIP_STEP * (WEAPON_STRIP_MODELS.length - 1) / 2f;
        StringBuilder drawn = new StringBuilder();
        for (int index = 0; index < WEAPON_STRIP_MODELS.length; index++) {
            long id = WEAPON_STRIP_FIRST_ID + index;
            models.addCharacter(id, WEAPON_STRIP_MODELS[index]);
            models.setPosition(id, left + WEAPON_STRIP_STEP * index, 0f, 0f);
            drawn.append(WEAPON_STRIP_MODELS[index])
                    .append('=')
                    .append(models.hasModel(id))
                    .append(' ');
        }
        showingWeaponStrip = true;
        LOG.info("dải chấm Q7: {}", drawn.toString().trim());
    }

    private void renderWeaponStrip() {
        presenter.sync(monsters, WEAPON_STRIP_X);
        npcPresenter.sync(WEAPON_STRIP_X);
        models.follow(WEAPON_STRIP_X, 0f);
        models.render(1f / 60f);
    }

    /**
     * Giả lập {@code MonsterMovedEvent} liên tiếp — đó là cách DUY NHẤT quái vào clip {@code run}
     * (không có vận tốc trên dây, xem {@link MonsterPresenter}), nên gọi thẳng {@code setMoving} sẽ
     * kiểm chứng nhầm một đường code mà game thật không đi qua.
     */
    private void feedMovement() {
        presenter.onMoved(MonsterMovedEvent.newBuilder()
                .setMonsterId(DUMMY_ID)
                .setX(DUMMY_X + 0.5f)
                .setY(0f)
                .build());
        monsters.put(DUMMY_ID, new CombatHudController.MonsterState("training_dummy", DUMMY_X + 0.5f, 0f, 100, 100));
    }

    /**
     * Bỏ cả cảnh cũ ra khỏi khung rồi dựng MỘT nhân vật thử ở {@link #CLOAK_TEST_X}. Không tháo
     * entity nào: chỉ dời camera — presenter tự thôi vẽ những gì ngoài AoI, đúng như trong game thật.
     */
    private void startCloakTest() {
        models.addCharacter(CLOAK_TEST_ID, CLOAK_TEST_WITHOUT);
        models.addCharacter(CLOAK_TEST_ID_WITH, CLOAK_TEST_WITH);
        models.setPosition(CLOAK_TEST_ID, CLOAK_TEST_X, 0f, 0f);
        models.setPosition(CLOAK_TEST_ID_WITH, CLOAK_TEST_OFFSCREEN_X, 0f, 0f);
        monsters.clear();
        showingCloakTest = true;
        LOG.info("so áo choàng: {} (không cloak) vs {} (có cloak)", CLOAK_TEST_WITHOUT, CLOAK_TEST_WITH);
    }

    /** Đổi ca: bộ CÓ áo choàng bước vào đúng chỗ bộ không có vừa đứng. */
    private void swapCloakTest() {
        models.setPosition(CLOAK_TEST_ID, CLOAK_TEST_OFFSCREEN_X, 0f, 0f);
        models.setPosition(CLOAK_TEST_ID_WITH, CLOAK_TEST_X, 0f, 0f);
    }

    private void renderCloakTest() {
        presenter.sync(monsters, CLOAK_TEST_X);
        npcPresenter.sync(CLOAK_TEST_X);
        models.follow(CLOAK_TEST_X, 0f);
        models.render(1f / 60f);
    }

    private void capture(String name) {
        Pixmap pixmap = Pixmap.createFromFrameBuffer(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.files.absolute(OUTPUT_DIR).mkdirs();
        // ⚠️ flipY=TRUE BẮT BUỘC — getFrameBufferPixmap trả dữ liệu THÔ của GL (dòng 0 = ĐÁY màn
        // hình) còn PNG quy ước dòng 0 = ĐỈNH. Bản 2 tham số mặc định false và lật ngược cả ảnh; đó
        // đúng là thứ từng làm một lượt chẩn đoán kết luận SAI (RENDERING.md §11.1).
        PixmapIO.writePNG(Gdx.files.absolute(OUTPUT_DIR + name + ".png"), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        pixmap.dispose();
        LOG.info("Đã chụp {}{}.png", OUTPUT_DIR, name);
    }

    @Override
    public void dispose() {
        if (models != null) {
            models.dispose();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage — xem model quái");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new WorldModelPreviewTool(), config);
    }
}
