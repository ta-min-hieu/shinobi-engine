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
 * <p>Khung hình cố ý đặt cạnh nhau ba thứ phải phân biệt được: nhân vật (đã có model từ ADR-031),
 * quái ĐÃ có model ({@code training_dummy}), và quái CHƯA có model ({@code sample_boss}, vẫn là hộp
 * đỏ) — nếu nhánh fallback từng bị hỏng, ảnh này là chỗ thấy ngay.
 */
public final class MonsterModelPreviewTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MonsterModelPreviewTool.class);

    /**
     * Thư mục ảnh, tuyệt đối — cwd của công cụ là gốc asset ({@code assets/exported}, để
     * {@code Gdx.files.internal} tra được model), và đó là cây tài sản của pipeline art chứ không
     * phải chỗ đổ output. Task Gradle truyền đường dẫn thật vào.
     */
    private static final String OUTPUT_DIR = System.getProperty("kage.preview.out", "build/preview-monster") + "/";

    private static final long CHARACTER_ID = 1L;

    /** Id âm = quái, đúng quy ước server ({@code WorldFacade.nextMonsterOrNpcId} đếm lùi từ −1). */
    private static final long DUMMY_ID = -1L;

    private static final long BOSS_ID = -2L;

    // Toàn bộ khung nằm TRƯỚC bệ demo đầu tiên (bắt đầu ở x=3, xem DemoPlatforms) — bệ vẽ đè lên
    // quái thì ảnh không còn chứng minh được gì về chính con quái, đúng bẫy đã gặp ở lượt chụp đầu.
    private static final float PLAYER_X = -3f;
    private static final float DUMMY_X = 0f;
    private static final float BOSS_X = 2f;

    private final Map<Long, CombatHudController.MonsterState> monsters = new LinkedHashMap<>();

    private WorldModelRenderer models;
    private MonsterPresenter presenter;
    private ShapeRenderer shapeRenderer;
    private int frame;

    @Override
    public void create() {
        models = new WorldModelRenderer(GraphicsTier.MEDIUM);
        // ĐÚNG khung nhìn của game thật — kiểm ở zoom khác thì không kết luận được gì về kích thước
        // tương đối giữa nhân vật, quái và dấu mục tiêu.
        models.resize(WorldConstants.VIEWPORT_HEIGHT_UNITS, WorldConstants.VIEWPORT_HEIGHT_UNITS);
        shapeRenderer = new ShapeRenderer();
        presenter = new MonsterPresenter(models);

        models.addCharacter(CHARACTER_ID, "char_hoa-b1");
        if (!models.isEnabled()) {
            LOG.error("Không xem được: thiếu model nhân vật dưới models/med/ — chạy pipeline art trước.");
            Gdx.app.exit();
            return;
        }
        putMonster(DUMMY_ID, "training_dummy", DUMMY_X);
        putMonster(BOSS_ID, "sample_boss", BOSS_X);
        LOG.info(
                "model quái: training_dummy={} sample_boss={} (sample_boss=false là ĐÚNG, chưa vẽ)",
                models.hasModel(DUMMY_ID),
                models.hasModel(BOSS_ID));
    }

    /** Đi qua đúng {@link MonsterPresenter#onSpawned} thay vì tự gọi {@code addMonster}. */
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

        models.setPosition(CHARACTER_ID, PLAYER_X, 0f, 0f);
        // Y HỆT WorldScreen.renderModels: sync trước, follow, rồi render.
        presenter.sync(monsters, PLAYER_X);
        models.follow(PLAYER_X + 2.5f, 0f); // lùi camera ra giữa để cả 3 đối tượng vào khung
        models.render(1f / 60f);
        DemoPlatforms.render(shapeRenderer, models.combined());
        presenter.render(shapeRenderer, models.combined(), monsters, DUMMY_ID);
        presenter.tick(1f / 60f);

        script();
    }

    /**
     * Diễn lần lượt từng trạng thái đáng nhìn. Số khung để clip kịp vào giữa động tác chứ không bắt
     * đúng khung đầu (crossfade 0.15s = 9 khung) — chụp quá sớm thì mọi tư thế trông như nhau.
     */
    private void script() {
        switch (frame) {
            case 60 -> capture("1-idle-va-fallback");
            case 61 -> feedMovement();
            case 75 -> capture("2-run");
            case 76 -> models.playAttack(DUMMY_ID);
            case 82 -> capture("3-attack");
            case 83 -> models.playDie(DUMMY_ID);
            case 95 -> capture("4-die");
            case 100 -> Gdx.app.exit();
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
        new Lwjgl3Application(new MonsterModelPreviewTool(), config);
    }
}
