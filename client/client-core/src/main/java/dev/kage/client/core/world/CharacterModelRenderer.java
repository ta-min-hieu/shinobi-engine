package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
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
 *
 * <p><b>Không dùng {@code SceneManager.render()}</b> (RENDERING.md §12.2 câu hỏi runtime #2) —
 * {@code SceneManager} chỉ vẽ MỘT lượt bằng một {@code ShaderProvider} duy nhất, không có điểm chèn
 * lượt vẽ thứ hai cho viền inverted-hull mà không fork nội bộ nó. Lớp này tự quản 2
 * {@link ModelBatch} thô ({@link #outlineBatch}, {@link #bodyBatch}), mỗi cái một
 * {@code ShaderProvider} riêng ({@link CelOutlineShaderProvider}, {@link CelBodyShaderProvider}), và
 * tự lặp {@link #scenes} hai lần mỗi khung. Animation KHÔNG phụ thuộc {@code SceneManager} — chính
 * {@link Scene#update} (gọi ở {@link #updateScenes}) mới là thứ chạy {@code AnimationController} bên
 * trong, {@code SceneManager.update()} nguyên bản chỉ lặp gọi đúng method đó cho từng Scene đã đăng ký.
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
     * Thư mục con cố định chứa toàn bộ file glTF, {@code models/} — {@link #modelDirectory} nối
     * thêm {@link GraphicsTier#folderName} phía sau để ra đường dẫn thật.
     */
    private static final String MODEL_ROOT = "models/";

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
     * <p>⚠️ <b>Dấu là {@code -90f}, KHÔNG PHẢI {@code +90f}</b> — xác nhận bằng ảnh chụp thật qua
     * {@code CharacterModelDebugTool}, không phải suy ra từ quy tắc bàn tay phải: {@code +90f} tính
     * tay có vẻ đúng (Y+ cục bộ → Z+ thế giới) nhưng khi chạy thật (sau khi {@link CelBodyShader}/
     * {@link CelOutlineShader} được sửa để nhân {@code u_worldTrans}, xem javadoc hai lớp đó) nhân
     * vật đứng NGƯỢC ĐẦU XUỐNG ĐẤT — dấu hiệu quy ước xoay/handedness của
     * {@code Matrix4#setToRotation} không khớp suy luận tay. {@code -90f} cho nhân vật đứng đúng
     * chiều, đúng hướng, xác nhận qua idle lẫn run (RENDERING.md §11.1). Đây là nguyên nhân gốc THỨ
     * HAI của bug "nằm ngang" — thứ nhất là thiếu {@code u_worldTrans} ở cel shader; sửa một mình
     * dấu này (khi vẫn thiếu {@code u_worldTrans}) sẽ KHÔNG lộ ra vì cả mesh khi đó gần như không di
     * chuyển đâu cả (skin ≈ identity, world transform bị bỏ qua hoàn toàn).
     *
     * <p>Hằng số, không dựng lại mỗi frame (/CLAUDE.md §6).
     */
    private static final Matrix4 Y_UP_TO_Z_UP = new Matrix4().setToRotation(Vector3.X, -90f);

    /**
     * Thời gian chuyển mượt (giây) giữa 2 clip animation — không cắt cứng, tránh pop khi đổi tư thế
     * ([RENDERING.md §11.1](docs/03-engine/RENDERING.md)). Áp dụng cho cả loop (idle↔run) lẫn
     * one-shot (attack/hit/skill_cast) qua {@code AnimationController.animate}.
     */
    private static final float CLIP_TRANSITION_SECONDS = 0.15f;

    private final OrthographicCamera camera;
    private final ModelBatch outlineBatch;
    private final ModelBatch bodyBatch;

    /**
     * Đường dẫn thư mục model đã suy ra từ tier truyền vào constructor, tính đúng một lần
     * (không phải mỗi lần {@link #addCharacter}). Đọc lại tier lúc chạy KHÔNG được hỗ trợ — xem
     * {@link GraphicsTier} về lý do ({@code ensureAssetsAvailable(tier)} của ADR-028 chưa tồn tại).
     */
    private final String modelDirectory;

    private final Map<String, SceneAsset> assetsByKey = new HashMap<>();
    private final Map<Long, Scene> scenesByCharacter = new HashMap<>();
    private final Map<Long, AnimationState> animationStateByCharacter = new HashMap<>();
    /**
     * Danh sách vẽ song song với {@link #scenesByCharacter}, đồng bộ ở {@link #addCharacter}/
     * {@link #removeCharacter}. Riêng vì {@code render()} lặp nó MỖI KHUNG (2 lượt vẽ + 1 lượt
     * update) — lặp {@code java.util.Map#values()} bằng for-each cấp phát một {@code Iterator} MỚI
     * mỗi lần gọi {@code .iterator()} (hành vi chuẩn của {@code java.util.HashMap}), phạm luật cấm
     * allocation mỗi khung (/CLAUDE.md §6, RENDERING.md §16). {@code com.badlogic.gdx.utils.Array}
     * lặp bằng chỉ số (for cổ điển) nên không cấp phát gì.
     */
    private final Array<Scene> scenes = new Array<>();

    private final Vector3 target = new Vector3();
    private boolean enabled = true;

    /**
     * @param tier tier hình học đọc từ đĩa ({@link GraphicsTier}) — chỉ đọc MỘT LẦN ở đây, không đổi
     *     được sau khi đã dựng ({@link GraphicsTier} giải thích lý do). {@code WorldScreen} truyền
     *     tường minh {@link GraphicsTier#MEDIUM}, khớp "bản cài đóng gói đúng 1 tier mặc định
     *     MEDIUM" của [ADR-028](docs/01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md).
     */
    public CharacterModelRenderer(GraphicsTier tier) {
        this.modelDirectory = resolveModelDirectory(tier);
        this.camera = new OrthographicCamera();
        // Ống nhìn phải bao được cả nhân vật lẫn mặt đất phía sau nó. Orthographic nên near có thể
        // âm; để dương và lùi camera ra xa cho dễ suy luận.
        this.camera.near = 0.1f;
        this.camera.far = CAMERA_DISTANCE * 2f;
        applyLockedAngle();
        // Ánh sáng KHÔNG còn nạp qua Environment/DirectionalLightEx của gdx-gltf PBR nữa — cel
        // shader đọc thẳng CelShadingConstants.LIGHT_DIRECTION qua uniform (RENDERING.md §13, §12.2
        // câu hỏi runtime #2). Cùng giá trị hướng sáng cũ, chỉ đổi chỗ đọc.
        this.outlineBatch = new ModelBatch(new CelOutlineShaderProvider());
        this.bodyBatch = new ModelBatch(new CelBodyShaderProvider());
    }

    /**
     * Suy thư mục model từ tier — thuần hàm, không phụ thuộc GL, tách khỏi constructor để test được
     * thẳng cho cả 3 tier mà không cần dựng {@link ModelBatch}/{@code OrthographicCamera} thật
     * ({@code GraphicsTierTest}).
     */
    static String resolveModelDirectory(GraphicsTier tier) {
        return MODEL_ROOT + tier.folderName + "/";
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
            FileHandle file = Gdx.files.internal(modelDirectory + assetKey + ".glb");
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
        scenes.add(scene);
        // Tư thế ban đầu là idle, đặt thẳng (không crossfade) vì chưa có gì để chuyển tiếp từ đó —
        // tránh model đứng bind pose tĩnh (§6.5 mục 3 của RENDERING.md, giờ đã nối).
        scene.animationController.setAnimation(CharacterAnimationClips.IDLE, -1);
        animationStateByCharacter.put(characterId, new AnimationState(characterId));
    }

    public void removeCharacter(long characterId) {
        Scene scene = scenesByCharacter.remove(characterId);
        if (scene != null) {
            scenes.removeValue(scene, true); // so sánh bằng ==, đúng vì mỗi Scene là 1 instance riêng
        }
        animationStateByCharacter.remove(characterId);
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

    /**
     * Đặt lại clip loop (idle/run) mong muốn của một nhân vật — gọi mỗi frame từ
     * {@code WorldScreen}, giống {@link #setCharacterPosition}. Không cắt ngang một one-shot
     * (attack/hit/die/skill_cast) đang chạy; clip loop chỉ được áp lại khi one-shot đó kết thúc
     * (xem {@link AnimationState#onEnd}).
     *
     * @param moving true = {@link CharacterAnimationClips#RUN}, false = {@link
     *     CharacterAnimationClips#IDLE} (RENDERING.md §11.1).
     */
    public void setCharacterMoving(long characterId, boolean moving) {
        AnimationState state = animationStateByCharacter.get(characterId);
        if (state == null || state.dead) {
            return; // Chưa có Scene, hoặc đã chết — giữ nguyên tư thế die tới khi hồi sinh.
        }
        state.moving = moving;
        if (!state.oneShotPlaying) {
            applyLoopClip(state);
        }
    }

    /** Đòn đánh thường — luôn phát dù trúng hay né (đó là động tác vung đòn, không phải kết quả). */
    public void playAttack(long characterId) {
        playOneShot(characterId, CharacterAnimationClips.ATTACK);
    }

    /** Phản ứng trúng đòn — KHÔNG gọi khi mục tiêu chết (dùng {@link #playDie} thay vào đó). */
    public void playHit(long characterId) {
        playOneShot(characterId, CharacterAnimationClips.HIT);
    }

    /** Dùng kỹ năng — cho cả người dùng skill lẫn (gián tiếp qua {@code WorldScreen}) không áp cho quái. */
    public void playSkillCast(long characterId) {
        playOneShot(characterId, CharacterAnimationClips.SKILL_CAST);
    }

    /**
     * HP về 0 — phát một lần, dừng ở tư thế cuối (ART-PIPELINE §4.2: "không loop"), KHÔNG tự quay
     * lại idle/run như các one-shot khác. Giữ nguyên tới khi {@link #resetAfterRespawn}.
     */
    public void playDie(long characterId) {
        AnimationState state = animationStateByCharacter.get(characterId);
        if (state == null) {
            return;
        }
        state.dead = true;
        playOneShotState(state, CharacterAnimationClips.DIE);
    }

    /**
     * Nhân vật vừa hồi sinh — trả lại idle ngay lập tức (đặt thẳng, không crossfade, giống cách
     * {@code WorldScreen.onCharacterRespawned} snap vị trí thay vì nội suy: hồi sinh là dịch chuyển
     * tức thời, không phải chuyển tiếp mượt từ tư thế die).
     */
    public void resetAfterRespawn(long characterId) {
        AnimationState state = animationStateByCharacter.get(characterId);
        Scene scene = scenesByCharacter.get(characterId);
        if (state == null || scene == null) {
            return;
        }
        state.dead = false;
        state.oneShotPlaying = false;
        state.moving = false;
        state.currentClip = CharacterAnimationClips.IDLE;
        scene.animationController.setAnimation(CharacterAnimationClips.IDLE, -1);
    }

    /**
     * True nếu clip {@code die} đang/đã phát và chưa {@link #resetAfterRespawn}. {@code WorldScreen}
     * dùng cờ này để suy ra "nhân vật KHÁC vừa hồi sinh" từ gói di chuyển đầu tiên sau đó — giao
     * thức không phát {@code CharacterRespawnedEvent} cho ai khác ngoài chính người hồi sinh.
     */
    public boolean isDead(long characterId) {
        AnimationState state = animationStateByCharacter.get(characterId);
        return state != null && state.dead;
    }

    private void applyLoopClip(AnimationState state) {
        String desired = CharacterAnimationClips.loopingClipFor(state.moving);
        if (desired.equals(state.currentClip)) {
            return; // AnimationController restart clip từ đầu mỗi lần setAnimation/animate được gọi
            // — tránh gọi lại khi clip mong muốn không đổi (mỗi frame gọi setCharacterMoving).
        }
        state.currentClip = desired;
        Scene scene = scenesByCharacter.get(state.characterId);
        scene.animationController.animate(desired, -1, 1f, null, CLIP_TRANSITION_SECONDS);
    }

    private void playOneShot(long characterId, String clip) {
        AnimationState state = animationStateByCharacter.get(characterId);
        if (state == null || state.dead) {
            return; // Đã chết — không phát chồng animation khác lên xác tới khi hồi sinh.
        }
        playOneShotState(state, clip);
    }

    private void playOneShotState(AnimationState state, String clip) {
        if (state.oneShotPlaying && clip.equals(state.currentClip)) {
            // Cùng một one-shot đã đang chạy — bỏ qua thay vì restart giữa chừng. Xảy ra thật khi
            // WorldScreen dự đoán skill_cast cục bộ lúc bấm nút rồi SkillResultEvent xác nhận tới
            // ngay sau đó (CLIENT.md "Đúng cách": dự đoán animation chính mình là hợp lệ).
            return;
        }
        state.oneShotPlaying = true;
        state.currentClip = clip;
        Scene scene = scenesByCharacter.get(state.characterId);
        scene.animationController.animate(clip, 1, 1f, state, CLIP_TRANSITION_SECONDS);
    }

    /** Camera bám nhân vật của người chơi, giữ nguyên góc khoá. */
    public void follow(float x, float y) {
        target.set(x, y, 0f);
        camera.position.set(target).mulAdd(camera.direction, -CAMERA_DISTANCE);
        camera.update();
    }

    /**
     * Ma trận chiếu kết hợp của camera 3D — cho {@code WorldScreen} vẽ thêm hình phẳng (VD hộp
     * quái, chưa có model 3D) khớp đúng không gian chiếu với nhân vật thay vì dùng camera 2D phẳng
     * không liên quan tới góc nhìn đã khoá.
     */
    public Matrix4 combined() {
        return camera.combined;
    }

    public void render(float deltaTime) {
        if (!enabled) {
            return;
        }
        updateScenes(deltaTime);

        // Lượt 1 — viền: PHẢI vẽ trước lượt thân. Vỏ đẩy ra ngoài lớn hơn thân nên depth test (LEQUAL,
        // mặc định) tự để thân đè lên phần viền nằm trong silhouette; chỉ còn lại đúng viền rìa ngoài
        // (RENDERING.md §12.2).
        outlineBatch.begin(camera);
        for (int i = 0; i < scenes.size; i++) {
            outlineBatch.render(scenes.get(i).modelInstance);
        }
        outlineBatch.end();

        bodyBatch.begin(camera);
        for (int i = 0; i < scenes.size; i++) {
            bodyBatch.render(scenes.get(i).modelInstance);
        }
        bodyBatch.end();
    }

    /**
     * Chạy {@code AnimationController} (bên trong {@link Scene#update}) VÀ ép tính lại transform
     * xương mỗi khung. {@code Scene#update} một mình đủ để advance animation, nhưng gọi tường minh
     * {@code calculateTransforms()} ở đây đảm bảo thay đổi {@code transform} gốc từ
     * {@link #setCharacterPosition} luôn lan tới ma trận xương trong CÙNG khung hình — rẻ (1 mesh,
     * {@value CelShadingConstants#RIG_BONE_COUNT} xương), không cấp phát, an toàn thừa còn hơn thiếu.
     */
    private void updateScenes(float deltaTime) {
        for (int i = 0; i < scenes.size; i++) {
            Scene scene = scenes.get(i);
            scene.update(camera, deltaTime);
            scene.modelInstance.calculateTransforms();
        }
    }

    @Override
    public void dispose() {
        outlineBatch.dispose();
        bodyBatch.dispose();
        for (SceneAsset asset : assetsByKey.values()) {
            asset.dispose();
        }
        assetsByKey.clear();
        scenesByCharacter.clear();
        scenes.clear();
        animationStateByCharacter.clear();
    }

    /**
     * Trạng thái animation của MỘT nhân vật (clip hiện tại, có đang chạy one-shot không, đã chết
     * chưa). Là {@code AnimationListener} luôn của chính nó thay vì cấp phát 1 listener riêng mỗi
     * lần gọi {@link #playOneShot} — nghe xong 1 one-shot, tự áp lại clip loop mong muốn
     * ({@link #applyLoopClip}), trừ khi vừa mới {@code die} (giữ nguyên tư thế cuối).
     */
    private final class AnimationState implements AnimationController.AnimationListener {
        private final long characterId;
        private String currentClip = CharacterAnimationClips.IDLE;
        private boolean moving;
        private boolean oneShotPlaying;
        private boolean dead;

        AnimationState(long characterId) {
            this.characterId = characterId;
        }

        @Override
        public void onEnd(AnimationController.AnimationDesc animation) {
            oneShotPlaying = false;
            if (!dead) {
                applyLoopClip(this);
            }
        }

        @Override
        public void onLoop(AnimationController.AnimationDesc animation) {
            // One-shot luôn chạy loopCount=1 (playOneShotState) — không bao giờ lặp, không cần xử lý.
        }
    }
}
