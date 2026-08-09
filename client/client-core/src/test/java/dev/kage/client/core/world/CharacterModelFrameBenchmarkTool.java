package dev.kage.client.core.world;

import java.util.Arrays;
import java.util.Locale;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.Faction;

/**
 * Đo GPU frame time thật với 20 model nhân vật cùng lúc — số liệu còn thiếu ở
 * [ART-PIPELINE §4.4](docs/14-art/ART-PIPELINE.md) và điều kiện #1 của
 * [ADR-031 "Revisit Trigger"](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)
 * (frame time GPU {@code > 8 ms} với 20 nhân vật trên màn).
 *
 * <p><b>KHÔNG phải JUnit test</b> — đây là công cụ chẩn đoán thủ công, chạy qua Gradle task
 * {@code runFrameBenchmark} ({@code client-core/build.gradle.kts}), không qua {@code test}. Lý do
 * giống hệt {@code FontFactoryVietnameseGlyphTest}: cần một {@link Lwjgl3Application} MỞ CỬA SỔ GL
 * thật, CI ({@code ci.yml}, {@code ubuntu-latest}, không Xvfb) sẽ luôn fail nếu việc này chạy trong
 * {@code test}. Nằm ở {@code src/test} (không phải {@code src/main}) vì client-desktop production
 * KHÔNG cần biết công cụ đo này tồn tại (Điều 28 — launcher chỉ chứa gameplay thật).
 *
 * <p>Dùng chung {@link CharacterModelRenderer} + {@link CharacterModelKey} với {@link WorldScreen} —
 * đo đúng đường vẽ thật, không phải mô phỏng riêng. Cycle qua đủ 15 tổ hợp hệ phái × bậc trang phục
 * (5 hệ × 3 bậc = đúng số file {@code .glb} thật dưới {@code assets/exported/models/med/}) để 20
 * nhân vật không dùng chung một {@code SceneAsset} — phản ánh đúng tình huống 20 người chơi khác
 * hệ phái/bậc trang phục đứng cạnh nhau, không phải 20 bản sao của cùng một model (rẻ hơn thật vì
 * chung {@code SceneAsset}, xem Flyweight ở {@link CharacterModelRenderer#addCharacter}).
 */
public final class CharacterModelFrameBenchmarkTool extends ApplicationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CharacterModelFrameBenchmarkTool.class);

    /** Đúng con số ở Revisit Trigger #1 của ADR-031 — không phải số tuỳ chọn. */
    private static final int CHARACTER_COUNT = 20;

    /**
     * Bỏ qua trước khi đo — hấp thụ chi phí một-lần (JIT chưa nóng, biên dịch shader lần đầu cho
     * từng {@code SceneAsset} khác nhau khi lần đầu được vẽ). Đo ngay từ khung đầu tiên sẽ lẫn chi
     * phí này vào số liệu ổn định, ra kết quả bi quan giả.
     */
    private static final int WARMUP_FRAMES = 300;

    /**
     * Đủ lớn để hai việc cùng lúc: (1) p95 có ý nghĩa thống kê, (2) vòng lặp đo chạy đủ LÂU (vài
     * giây thật, không phải vài chục mili-giây) để công cụ lấy mẫu cấp phát dựa trên JFR
     * ({@code -XX:StartFlightRecording}, xem {@code runFrameBenchmark} ở
     * {@code client-core/build.gradle.kts}) có đủ mẫu RƠI VÀO đúng vòng lặp render thay vì toàn bộ
     * rơi vào pha nạp {@code .glb} một-lần lúc khởi động (đã xác nhận thật: chạy thử với 300 khung —
     * dưới 100 ms — cho ra 0/253 mẫu JFR nằm trong {@code SceneManager}/{@code AnimationController},
     * toàn bộ mẫu đều nằm trong {@code GLBLoader.load}).
     */
    private static final int MEASURED_FRAMES = 6000;

    /** {@code FIXED_DT} của dự án (/CLAUDE.md, ADR-018) — tick logic 60Hz, không phải tốc độ vẽ thật. */
    private static final float FIXED_DT = 1f / 60f;

    private static final int GRID_COLUMNS = 5;
    private static final float GRID_SPACING_UNITS = 2.5f;

    /**
     * Rộng hơn hẳn {@link WorldConstants#VIEWPORT_HEIGHT_UNITS} (10) vì gameplay thật chỉ cần thấy
     * 1-2 nhân vật gần camera — lưới 20 nhân vật trải rộng hơn thế, phải nới camera ra để đúng yêu
     * cầu "20 nhân vật trên màn" của Revisit Trigger #1, không phải 20 nhân vật ngoài khung hình.
     */
    private static final float VIEWPORT_UNITS = 30f;

    private CharacterModelRenderer models;
    private final float[] measuredFrameTimesMs = new float[MEASURED_FRAMES];
    private int measuredFrameIndex;
    private int totalFramesSeen;
    private long usedMemoryBeforeMeasurement;
    private boolean reported;

    @Override
    public void create() {
        // MEDIUM: đúng tier mà pipeline art xuất mặc định và benchmark này đo (log lỗi dưới đây
        // cũng nói rõ "models/med/") — không đổi ý nghĩa phép đo cũ, chỉ hết hardcode ở phía đọc.
        models = new CharacterModelRenderer(GraphicsTier.MEDIUM);
        models.resize(VIEWPORT_UNITS, VIEWPORT_UNITS);
        spawnCharacterGrid();
        models.follow(0f, 0f);

        if (!models.isEnabled()) {
            // CharacterModelRenderer tự tắt nhánh 3D khi thiếu .glb (xem Javadoc lớp đó) — đúng
            // hành vi production, nhưng với công cụ ĐO thì không có gì để đo, nên dừng rõ ràng thay
            // vì lặng lẽ báo "0ms" (điều CLAUDE.md cấm — không được bịa số liệu).
            LOG.error("Không đo được: CharacterModelRenderer tắt nhánh 3D (thiếu .glb dưới"
                    + " models/med/). Chạy pipeline art (tools:asset-pipeline) trước khi chạy"
                    + " benchmark này.");
            Gdx.app.exit();
        }
    }

    /**
     * Xếp 20 nhân vật thành lưới {@value #GRID_COLUMNS} cột, cách đều {@value #GRID_SPACING_UNITS}
     * world unit, tâm tại gốc toạ độ — nằm trong {@link #VIEWPORT_UNITS} của camera đã khoá góc.
     */
    private void spawnCharacterGrid() {
        int rows = (int) Math.ceil((double) CHARACTER_COUNT / GRID_COLUMNS);
        for (int i = 0; i < CHARACTER_COUNT; i++) {
            long characterId = i + 1L;
            String assetKey = CharacterModelKey.of(factionFor(i), costumeTierFor(i));
            models.addCharacter(characterId, assetKey);
            if (!models.isEnabled()) {
                return; // Thiếu model — dừng thêm, create() sẽ phát hiện và thoát.
            }
            int col = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            float x = (col - ((GRID_COLUMNS - 1) / 2f)) * GRID_SPACING_UNITS;
            float y = (row - ((rows - 1) / 2f)) * GRID_SPACING_UNITS;
            models.setCharacterPosition(characterId, x, y, 0f);
        }
    }

    // 5 hệ phái × 3 bậc = 15 tổ hợp thật (khớp đúng 15 file .glb dưới models/med/) — nhân vật thứ
    // 16-20 lặp lại 5 tổ hợp đầu, dùng chung SceneAsset với nhân vật 1-5 (đúng bản chất Flyweight).
    private static Faction factionFor(int index) {
        Faction[] factions = {
            Faction.FACTION_HOA, Faction.FACTION_THUY, Faction.FACTION_PHONG, Faction.FACTION_LOI, Faction.FACTION_THO
        };
        return factions[index % factions.length];
    }

    private static CostumeTier costumeTierFor(int index) {
        CostumeTier[] tiers = {CostumeTier.COSTUME_TIER_B1, CostumeTier.COSTUME_TIER_B2, CostumeTier.COSTUME_TIER_B3};
        return tiers[(index / 5) % tiers.length];
    }

    @Override
    public void render() {
        if (reported || !models.isEnabled()) {
            return;
        }
        totalFramesSeen++;

        if (totalFramesSeen <= WARMUP_FRAMES) {
            models.render(FIXED_DT);
            if (totalFramesSeen == WARMUP_FRAMES) {
                // Mốc bắt đầu đo cấp phát — System.gc() ở đây là NGOẠI LỆ cố ý cho công cụ chẩn
                // đoán thủ công, không phải code chạy trong game loop thật (/CLAUDE.md §6 cấm cho
                // RenderingSystem, không cấm cho công cụ benchmark một lần). Mục đích: kéo baseline
                // về gần trạng thái ổn định trước khi đo, GIẢM (không loại bỏ) nhiễu GC.
                System.gc();
                usedMemoryBeforeMeasurement = usedMemoryBytes();
            }
            return;
        }

        long startNanos = System.nanoTime();
        models.render(FIXED_DT);
        long elapsedNanos = System.nanoTime() - startNanos;
        measuredFrameTimesMs[measuredFrameIndex++] = elapsedNanos / 1_000_000f;

        if (measuredFrameIndex >= MEASURED_FRAMES) {
            reportAndExit();
        }
    }

    private void reportAndExit() {
        reported = true;
        long usedMemoryAfterMeasurement = usedMemoryBytes();

        float[] sorted = measuredFrameTimesMs.clone();
        Arrays.sort(sorted);
        float min = sorted[0];
        float max = sorted[sorted.length - 1];
        float sum = 0f;
        for (float ms : sorted) {
            sum += ms;
        }
        float avg = sum / sorted.length;
        int p95Index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1);
        float p95 = sorted[p95Index];

        String glVendor = Gdx.gl.glGetString(GL20.GL_VENDOR);
        String glRenderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
        String glVersion = Gdx.gl.glGetString(GL20.GL_VERSION);

        LOG.info(
                "=== CharacterModelFrameBenchmarkTool — {} nhân vật, {} khung đo (sau {} khung" + " warmup) ===",
                CHARACTER_COUNT,
                MEASURED_FRAMES,
                WARMUP_FRAMES);
        LOG.info("GL_VENDOR={} | GL_RENDERER={} | GL_VERSION={}", glVendor, glRenderer, glVersion);
        LOG.info("frame time (ms): min={} avg={} p95={} max={}", format(min), format(avg), format(p95), format(max));

        // Xem Javadoc lớp — Runtime#freeMemory() là tín hiệu THÔ, KHÔNG phải phép đo cấp phát
        // chính xác: nhiễu bởi GC chạy không đoán trước được và bởi heap có thể tự lớn ra giữa lúc
        // đo. Không dùng số này để khẳng định "cấp phát X byte/khung" — chỉ dùng để phát hiện rò rỉ
        // rõ ràng (số tăng đều, không giảm lại sau GC).
        long usedMemoryDeltaBytes = usedMemoryAfterMeasurement - usedMemoryBeforeMeasurement;
        LOG.info(
                "cấp phát (tín hiệu THÔ, có nhiễu GC — xem Javadoc lớp): usedMemory trước={} KB,"
                        + " sau={} KB, delta={} KB qua {} khung ({} KB/khung nếu chia đều — KHÔNG"
                        + " phải số đo chính xác)",
                usedMemoryBeforeMeasurement / 1024,
                usedMemoryAfterMeasurement / 1024,
                usedMemoryDeltaBytes / 1024,
                MEASURED_FRAMES,
                format((float) usedMemoryDeltaBytes / 1024f / MEASURED_FRAMES));

        Gdx.app.exit();
    }

    private static long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @Override
    public void dispose() {
        if (models != null) {
            models.dispose();
        }
    }

    /**
     * Chạy độc lập — KHÔNG qua {@code DesktopLauncher}, không kết nối server (công cụ đo render
     * thuần, không phải màn hình gameplay). {@code workingDir} phải là {@code assets/exported/}
     * (Gradle task {@code runFrameBenchmark} đặt sẵn — xem {@code client-core/build.gradle.kts}),
     * giống hệt lý do {@code client-desktop/build.gradle.kts} đặt cho task {@code run}.
     */
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kage - Character Model Frame Benchmark (cong cu do, khong phai game)");
        config.setWindowedMode(1280, 720);
        // Tắt vsync + không trần FPS: muốn đo tốc độ CPU/GPU thật vẽ được, không bị khoá bởi
        // swap-interval của màn hình hiển thị.
        config.useVsync(false);
        config.setForegroundFPS(0);
        new Lwjgl3Application(new CharacterModelFrameBenchmarkTool(), config);
    }
}
