package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vẽ nhân vật bằng model glTF 3D thời gian thực qua camera orthographic khoá góc
 * ([ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)).
 *
 * <p>Góc camera là <b>hằng số runtime</b>, không nướng vào tài sản — đó là điều ADR-031 mua được khi
 * bỏ pre-render sprite. Đổi {@link #VIEW_YAW_DEGREES} là đổi cả game, không phải render lại asset nào.
 *
 * <p>Lớp này <b>tự tắt</b> khi không tìm thấy model và để {@link WorldScreen} vẽ hộp như cũ. Đó không
 * phải phòng thủ thừa: model nằm ở {@code assets/exported/models/}, sinh ra từ pipeline art và
 * <b>không commit</b> ([ART-PIPELINE §7](docs/14-art/ART-PIPELINE.md)), nên một bản checkout sạch
 * chưa chạy pipeline sẽ không có chúng. Crash lúc khởi động ở trường hợp đó là sai.
 */
public final class CharacterModelRenderer implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(CharacterModelRenderer.class);

    /**
     * Góc quay quanh trục đứng, độ. 35° là góc 3/4 mà chủ dự án duyệt bản pilot art
     * ([ART-QUALITY-STANDARD §7.1](docs/14-art/ART-QUALITY-STANDARD.md)); ở góc nghiêng thuần, khối
     * tóc của mesh sinh từ Text→3D che kín mặt.
     */
    public static final float VIEW_YAW_DEGREES = 35f;

    /** Độ ngẩng của camera. Đủ để thấy mặt đất mà không thành góc nhìn từ trên xuống. */
    public static final float VIEW_PITCH_DEGREES = 30f;

    /**
     * Tier hình học nạp lúc chạy. MEDIUM là mặc định cài đặt theo
     * [ADR-028](docs/01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) — LOW/HIGH tải thêm
     * khi người chơi đổi mức đồ hoạ, chưa nối ở lát cắt này.
     */
    private static final String MODEL_DIRECTORY = "models/med/";

    /**
     * Xa camera bao nhiêu. Với phép chiếu orthographic, khoảng cách <b>không</b> đổi khung hình —
     * nó chỉ cần đủ để hình học nằm trong near/far.
     */
    private static final float CAMERA_DISTANCE = 20f;

    /**
     * Đổi hệ trục Y-up của glTF sang Z-up của thế giới. glTF 2.0 quy định Y là trục đứng và
     * {@code export_character_lods.py} xuất đúng chuẩn đó ({@code export_yup=True}), còn thế giới
     * Kage đi trên mặt phẳng XY với Z là trục đứng. Không xoay thì nhân vật nằm ngang và lệch hẳn
     * khỏi tâm màn hình — đúng triệu chứng thấy được lần đầu chạy nhánh 3D thật.
     *
     * <p>Hằng số, không dựng lại mỗi frame (/CLAUDE.md §6).
     */
    private static final Matrix4 Y_UP_TO_Z_UP = new Matrix4().setToRotation(Vector3.X, 90f);

    private final SceneManager sceneManager;
    private final OrthographicCamera camera;
    private final Map<String, SceneAsset> assetsByKey = new HashMap<>();
    private final Map<Long, Scene> scenesByCharacter = new HashMap<>();
    private final Vector3 target = new Vector3();
    private boolean enabled = true;

    public CharacterModelRenderer() {
        this.sceneManager = new SceneManager();
        this.camera = new OrthographicCamera();
        // Ống nhìn phải bao được cả nhân vật lẫn mặt đất phía sau nó. Orthographic nên near có thể
        // âm; để dương và lùi camera ra xa cho dễ suy luận.
        this.camera.near = 0.1f;
        this.camera.far = CAMERA_DISTANCE * 2f;
        applyLockedAngle();
        sceneManager.setCamera(camera);

        DirectionalLightEx key = new DirectionalLightEx();
        // Chiếu từ trên-trước-phải, cùng hướng key light dùng lúc nghiệm thu art
        // (kage_sprite_render.setup_scene) — để vẻ ngoài trong game khớp ảnh đã duyệt.
        key.direction.set(-1f, -3f, -1f).nor();
        key.color.set(Color.WHITE);
        key.intensity = 3f;
        sceneManager.environment.add(key);
        sceneManager.setAmbientLight(0.6f);
    }

    /**
     * Đặt camera vào góc khoá. Tách riêng vì {@link #resize} phải dựng lại hướng nhìn sau khi
     * viewport đổi kích thước.
     */
    private void applyLockedAngle() {
        double yaw = Math.toRadians(VIEW_YAW_DEGREES);
        double pitch = Math.toRadians(VIEW_PITCH_DEGREES);
        float horizontal = (float) Math.cos(pitch);
        camera.direction
                .set((float) (-Math.sin(yaw) * horizontal), (float) (-Math.cos(yaw) * horizontal), (float)
                        -Math.sin(pitch))
                .nor();
        camera.up.set(Vector3.Z);
    }

    /** True khi có model thật để vẽ; false thì gọi phải tự vẽ hình thay thế. */
    public boolean isEnabled() {
        return enabled;
    }

    public void resize(float viewportWidthUnits, float viewportHeightUnits) {
        camera.viewportWidth = viewportWidthUnits;
        camera.viewportHeight = viewportHeightUnits;
        applyLockedAngle();
    }

    /**
     * Gắn model cho một nhân vật. Gọi lúc spawn, không gọi mỗi frame — nạp glTF là đọc đĩa và dựng
     * texture, thứ tuyệt đối không được nằm trong game loop (/CLAUDE.md §6).
     *
     * @param assetKey khoá asset kiểu {@code char_thuy-b1}, khớp cột {@code asset_key} của
     *     {@code art_asset} ([ART-PIPELINE §8](docs/14-art/ART-PIPELINE.md)).
     */
    public void addCharacter(long characterId, String assetKey) {
        if (!enabled || scenesByCharacter.containsKey(characterId)) {
            return;
        }
        SceneAsset asset = assetsByKey.get(assetKey);
        if (asset == null) {
            FileHandle file = Gdx.files.internal(MODEL_DIRECTORY + assetKey + ".glb");
            if (!file.exists()) {
                // Lần đầu thiếu model là tắt hẳn nhánh 3D, không phải cảnh báo mỗi lần spawn: thiếu
                // một model gần như luôn có nghĩa là chưa chạy pipeline art, không phải hỏng một file.
                LOG.warn("Không thấy model '{}' — nhánh 3D tắt, quay về hình thay thế", file.path());
                enabled = false;
                return;
            }
            asset = new GLBLoader().load(file);
            assetsByKey.put(assetKey, asset);
        }
        // Mỗi nhân vật một Scene riêng nhưng dùng chung SceneAsset: model instance tách nhau để có
        // transform và trạng thái animation riêng, còn mesh/texture chỉ nạp một lần.
        Scene scene = new Scene(asset.scene);
        scenesByCharacter.put(characterId, scene);
        sceneManager.addScene(scene);
    }

    public void removeCharacter(long characterId) {
        Scene scene = scenesByCharacter.remove(characterId);
        if (scene != null) {
            sceneManager.removeScene(scene);
        }
    }

    /** Dời model của một nhân vật tới vị trí đã nội suy trong frame này. */
    public void setCharacterPosition(long characterId, float x, float y) {
        Scene scene = scenesByCharacter.get(characterId);
        if (scene != null) {
            // Gốc model nằm ở chân (bước E của pipeline art đảm bảo điều đó) nên không cần chỉnh
            // độ cao — chỉ xoay về Z-up rồi tịnh tiến. `trn` ghi thẳng vào cột tịnh tiến, giữ
            // nguyên phần xoay và không cấp phát gì.
            scene.modelInstance.transform.set(Y_UP_TO_Z_UP).trn(x, y, 0f);
        }
    }

    /** Camera bám nhân vật của người chơi, giữ nguyên góc khoá. */
    public void follow(float x, float y) {
        target.set(x, y, 0f);
        camera.position.set(target).mulAdd(camera.direction, -CAMERA_DISTANCE);
        camera.update();
    }

    public void render(float deltaTime) {
        if (!enabled) {
            return;
        }
        sceneManager.update(deltaTime);
        sceneManager.render();
    }

    @Override
    public void dispose() {
        sceneManager.dispose();
        for (SceneAsset asset : assetsByKey.values()) {
            asset.dispose();
        }
        assetsByKey.clear();
        scenesByCharacter.clear();
    }
}
