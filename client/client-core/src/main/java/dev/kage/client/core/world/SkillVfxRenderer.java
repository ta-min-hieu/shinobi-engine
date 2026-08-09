package dev.kage.client.core.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vẽ hiệu ứng kỹ năng — nhánh 2D sprite của [ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)
 * ("VFX kỹ năng · icon · UI vẫn là sprite 2D", RENDERING.md §2).
 *
 * <p>Bộ sprite VFX đã được pipeline art sinh ra từ lâu (17 hiệu ứng, một cho mỗi entry
 * {@code SkillRegistry}) và nằm sẵn trong {@code main.atlas}, nhưng TRƯỚC 2026-08-09 <b>không một
 * dòng code client nào tham chiếu tới chúng</b> — người chơi dùng skill mà màn hình không hiện gì.
 * Lớp này là chỗ nối còn thiếu đó.
 *
 * <p><b>Billboard hướng camera</b>: sprite phẳng phải luôn quay mặt về camera trong không gian 3D.
 * Nhờ camera đã bị ADR-033 khoá cứng góc ({@link WorldModelRenderer#VIEW_YAW_DEGREES}/
 * {@link WorldModelRenderer#VIEW_PITCH_DEGREES}), hệ trục billboard là HẰNG SỐ — tính một lần
 * lúc dựng, không phải mỗi khung như billboard tổng quát.
 *
 * <p>Tự TẮT khi thiếu atlas (bản checkout chưa chạy pipeline art) thay vì crash — cùng nguyên tắc
 * với {@link WorldModelRenderer}.
 */
final class SkillVfxRenderer implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(SkillVfxRenderer.class);

    /** Khớp {@code ART-PIPELINE §4.2} — VFX 2D chạy 12 khung/giây. */
    private static final float FRAME_DURATION_SECONDS = 1f / 12f;

    /** Cạnh của sprite hiệu ứng, world unit. Nhân vật cao ~1.8 nên 2.4 là "trùm vừa người". */
    private static final float VFX_SIZE_UNITS = 2.4f;

    /**
     * Tâm hiệu ứng cao bao nhiêu so với gốc entity (gốc model nằm ở CHÂN — xem
     * {@code WorldModelRenderer.setPosition}). Đặt ngang thân giữa thay vì ngay chân,
     * nếu không hiệu ứng trông như nổ dưới đất/lơ lửng trên đầu (đã thấy thật qua ảnh chụp).
     */
    private static final float VFX_ANCHOR_HEIGHT_UNITS = 0.9f;

    /**
     * Số hiệu ứng hiện cùng lúc tối đa. Vượt ngưỡng thì hiệu ứng mới GHI ĐÈ cái cũ nhất thay vì cấp
     * phát thêm — giữ đúng luật không allocation trong vòng lặp vẽ (/CLAUDE.md §6).
     */
    private static final int MAX_CONCURRENT_VFX = 16;

    private static final String ATLAS_ROOT = "atlases/";

    private final TextureAtlas atlas;
    private final SpriteBatch batch;
    private final Array<Animation<TextureRegion>> animations = new Array<>();
    private final ActiveVfx[] pool = new ActiveVfx[MAX_CONCURRENT_VFX];
    private final Matrix4 billboard = new Matrix4();
    private final float[] billboardRight;
    private final float[] billboardUp;
    private int nextSlot;
    private boolean enabled = true;

    SkillVfxRenderer(GraphicsTier tier) {
        this.billboardRight = screenRightAxis();
        this.billboardUp = screenUpAxis(billboardRight);
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new ActiveVfx();
        }

        FileHandle file = Gdx.files.internal(ATLAS_ROOT + tier.folderName + "/main.atlas");
        if (!file.exists()) {
            LOG.warn("Không thấy atlas '{}' — hiệu ứng kỹ năng tắt, phần còn lại vẫn chạy", file.path());
            this.atlas = null;
            this.batch = null;
            this.enabled = false;
            return;
        }
        this.atlas = new TextureAtlas(file);
        this.batch = new SpriteBatch();
    }

    /**
     * Phát hiệu ứng của {@code skillCode} tại vị trí thế giới. Không tìm thấy sprite tương ứng thì
     * âm thầm bỏ qua — thiếu một hiệu ứng không đáng làm hỏng cả trận đánh.
     */
    void play(String skillCode, float x, float y, float z) {
        if (!enabled) {
            return;
        }
        Animation<TextureRegion> animation = animationFor(skillCode);
        if (animation == null) {
            return;
        }
        ActiveVfx vfx = pool[nextSlot];
        nextSlot = (nextSlot + 1) % pool.length;
        vfx.animation = animation;
        vfx.elapsedSeconds = 0f;
        vfx.x = x;
        vfx.y = y;
        vfx.z = z;
        vfx.alive = true;
    }

    /** @param projection ma trận chiếu của nhánh 3D — VFX phải nằm CÙNG không gian với nhân vật. */
    void render(float deltaTime, Matrix4 projection) {
        if (!enabled) {
            return;
        }
        batch.setProjectionMatrix(projection);
        boolean drawing = false;
        for (ActiveVfx vfx : pool) {
            if (!vfx.alive) {
                continue;
            }
            vfx.elapsedSeconds += deltaTime;
            if (vfx.animation.isAnimationFinished(vfx.elapsedSeconds)) {
                vfx.alive = false;
                continue;
            }
            if (!drawing) {
                batch.begin();
                drawing = true;
            }
            applyBillboard(vfx.x, vfx.y, vfx.z);
            batch.setTransformMatrix(billboard);
            // Căn TÂM sprite vào thân giữa nhân vật (xem VFX_ANCHOR_HEIGHT_UNITS) — cả hai trục đều
            // lùi nửa cạnh, không phải dựng lên từ chân.
            batch.draw(
                    vfx.animation.getKeyFrame(vfx.elapsedSeconds),
                    -VFX_SIZE_UNITS / 2f,
                    VFX_ANCHOR_HEIGHT_UNITS - (VFX_SIZE_UNITS / 2f),
                    VFX_SIZE_UNITS,
                    VFX_SIZE_UNITS);
        }
        if (drawing) {
            batch.end();
        }
    }

    /**
     * Dựng ma trận đặt mặt phẳng sprite vào đúng mặt phẳng màn hình tại {@code (x, y, z)} — cột X/Y
     * là hai trục màn hình đã tính sẵn, cột tịnh tiến là vị trí thế giới. Ghi tại chỗ vào
     * {@link #billboard}, không cấp phát (Điều 31).
     */
    private void applyBillboard(float x, float y, float z) {
        billboard.idt();
        billboard.val[Matrix4.M00] = billboardRight[0];
        billboard.val[Matrix4.M10] = billboardRight[1];
        billboard.val[Matrix4.M20] = billboardRight[2];
        billboard.val[Matrix4.M01] = billboardUp[0];
        billboard.val[Matrix4.M11] = billboardUp[1];
        billboard.val[Matrix4.M21] = billboardUp[2];
        billboard.val[Matrix4.M03] = x;
        billboard.val[Matrix4.M13] = y;
        billboard.val[Matrix4.M23] = z;
    }

    /**
     * Hiệu ứng theo {@code skill_code} — quy ước tên vùng atlas {@code vfx_<skill_code>_impact}
     * (ART-PIPELINE §5). Tra và cache 1 lần; {@code null} nghĩa là bộ art chưa có hiệu ứng cho skill
     * đó (VD skill mới thêm mà chưa chạy lại pipeline).
     */
    private Animation<TextureRegion> animationFor(String skillCode) {
        for (int i = 0; i < cachedSkillCodes.size; i++) {
            if (cachedSkillCodes.get(i).equals(skillCode)) {
                return animations.get(i);
            }
        }
        Array<TextureAtlas.AtlasRegion> frames = atlas.findRegions("vfx_" + skillCode + "_impact");
        Animation<TextureRegion> animation = frames.isEmpty()
                ? null
                : new Animation<>(FRAME_DURATION_SECONDS, new Array<TextureRegion>(frames.toArray()));
        cachedSkillCodes.add(skillCode);
        animations.add(animation);
        return animation;
    }

    private final Array<String> cachedSkillCodes = new Array<>();

    /** Trục "sang phải màn hình" = {@code direction × up}, cùng công thức camera đã khoá góc. */
    private static float[] screenRightAxis() {
        double yaw = Math.toRadians(WorldModelRenderer.VIEW_YAW_DEGREES);
        double pitch = Math.toRadians(WorldModelRenderer.VIEW_PITCH_DEGREES);
        float[] right = {(float) (-Math.cos(yaw) * Math.cos(pitch)), (float) (Math.sin(yaw) * Math.cos(pitch)), 0f};
        return normalize(right);
    }

    /** Trục "lên trên màn hình" = {@code right × direction}. */
    private static float[] screenUpAxis(float[] right) {
        double yaw = Math.toRadians(WorldModelRenderer.VIEW_YAW_DEGREES);
        double pitch = Math.toRadians(WorldModelRenderer.VIEW_PITCH_DEGREES);
        float dx = (float) (-Math.sin(yaw) * Math.cos(pitch));
        float dy = (float) (-Math.cos(yaw) * Math.cos(pitch));
        float dz = (float) -Math.sin(pitch);
        float[] up = {
            (right[1] * dz) - (right[2] * dy), (right[2] * dx) - (right[0] * dz), (right[0] * dy) - (right[1] * dx)
        };
        return normalize(up);
    }

    private static float[] normalize(float[] v) {
        float length = (float) Math.sqrt((v[0] * v[0]) + (v[1] * v[1]) + (v[2] * v[2]));
        v[0] /= length;
        v[1] /= length;
        v[2] /= length;
        return v;
    }

    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
        }
        if (atlas != null) {
            atlas.dispose();
        }
    }

    /** Một hiệu ứng đang chạy — tái dùng trong {@link #pool}, không bao giờ cấp phát lại. */
    private static final class ActiveVfx {
        private Animation<TextureRegion> animation;
        private float elapsedSeconds;
        private float x;
        private float y;
        private float z;
        private boolean alive;
    }
}
