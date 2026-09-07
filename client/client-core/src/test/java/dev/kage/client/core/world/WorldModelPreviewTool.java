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
import com.badlogic.gdx.utils.ScreenUtils;
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
 * trông y hệt nhau, rồi V30 lặp lại đúng chuyện đó với loài thứ tư cho tới V31), và quái
 * CHƯA có model ({@code sample_boss}, vẫn là hộp đỏ) — nếu nhánh fallback từng bị hỏng, ảnh này là
 * chỗ thấy ngay.
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

    // Toàn bộ khung nằm TRƯỚC bệ demo đầu tiên (bắt đầu ở x=3, xem DemoPlatforms) — bệ vẽ đè lên
    // quái thì ảnh không còn chứng minh được gì về chính con quái, đúng bẫy đã gặp ở lượt chụp đầu.
    // Ba loài quái đứng CẠNH NHAU (dummy/bandit/ogre) chứ không rải ra: câu hỏi mà ảnh này phải trả
    // lời là "có phân biệt được không", và thứ tự đó là thứ tự sát thương 8/22/40.
    private static final float PLAYER_X = -4.4f;
    private static final float DUMMY_X = -2.0f;
    private static final float BANDIT_X = -0.85f;
    private static final float OGRE_X = 0.3f;
    private static final float WARDEN_X = 1.45f;
    private static final float TRIAL_X = 2.6f;
    private static final float BOSS_X = 3.75f;
    private static final float ELDER_X = -3.0f;
    private static final float UNDRAWN_NPC_X = 4.9f;

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

    private final Map<Long, CombatHudController.MonsterState> monsters = new LinkedHashMap<>();

    private WorldModelRenderer models;
    private MonsterPresenter presenter;
    private NpcPresenter npcPresenter;
    private ShapeRenderer shapeRenderer;
    private int frame;

    /** Từ khung này trở đi, cảnh chỉ còn phép so áo choàng — xem {@link #CLOAK_TEST_ID}. */
    private boolean showingCloakTest;

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
        putMonster(OGRE_ID, "stone_ogre", OGRE_X);
        putMonster(WARDEN_ID, "archive_warden", WARDEN_X);
        putMonster(TRIAL_ID, "trial_construct", TRIAL_X);
        putMonster(BOSS_ID, "sample_boss", BOSS_X);
        putNpc(ELDER_ID, "town_elder", ELDER_X);
        putNpc(UNDRAWN_NPC_ID, "shop_keeper", UNDRAWN_NPC_X);
        LOG.info(
                "model quái: training_dummy={} wandering_bandit={} stone_ogre={} archive_warden={} "
                        + "trial_construct={} sample_boss={} (sample_boss=false là ĐÚNG, chưa vẽ)",
                models.hasModel(DUMMY_ID),
                models.hasModel(BANDIT_ID),
                models.hasModel(OGRE_ID),
                models.hasModel(WARDEN_ID),
                models.hasModel(TRIAL_ID),
                models.hasModel(BOSS_ID));
        LOG.info(
                "model NPC: town_elder={} shop_keeper={} (shop_keeper=false là ĐÚNG, mã NPC chưa tồn tại)",
                models.hasModel(ELDER_ID),
                models.hasModel(UNDRAWN_NPC_ID));
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
        monsters.put(monsterId, new CombatHudController.MonsterState(monsterCode, x, 0f, 100, 100));
        presenter.onSpawned(dev.kage.protocol.MonsterSpawnedEvent.newBuilder()
                .setMonsterId(monsterId)
                .setMonsterCode(monsterCode)
                .setX(x)
                .setY(0f)
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

        if (showingCloakTest) {
            renderCloakTest();
            script();
            return;
        }

        models.setPosition(CHARACTER_ID, PLAYER_X, 0f, 0f);
        // Y HỆT WorldScreen.renderModels: sync trước, follow, rồi render.
        presenter.sync(monsters, PLAYER_X);
        npcPresenter.sync(PLAYER_X);
        models.follow(PLAYER_X + 4.65f, 0f); // lùi camera ra giữa để cả 8 đối tượng vào khung
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
        if (frame >= CLOAK_TEST_FIRST_FRAME) {
            cloakScript();
            return;
        }
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
            case 120 -> Gdx.app.exit();
            default -> {
                /* các khung còn lại chỉ để animation chạy tiếp */
            }
        }
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
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(
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
