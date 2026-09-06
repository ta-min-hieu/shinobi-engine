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
 * Vẽ mọi thứ có model glTF 3D trong thế giới — nhân vật, quái, NPC — qua camera orthographic khoá góc
 * ([ADR-031](docs/01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)).
 *
 * <p>Khoá là {@code entity_id} của combat.proto, một không gian id DUY NHẤT đã tách sẵn dấu:
 * <b>dương = nhân vật</b> (sequence DB), <b>âm = quái/NPC</b> ({@code WorldFacade.nextMonsterOrNpcId}
 * đếm lùi từ −1), <b>0 = chính người chơi</b> (sentinel {@code WorldScreen.LOCAL_CHARACTER_ID} — id
 * thật của mình không bao giờ đi qua đây). Nhờ vậy một map duy nhất chứa được cả hai loại mà không
 * cần khoá ghép, và mọi thao tác sau khi đã gắn model (dời, quay mặt, phát animation, xoá) dùng
 * chung đúng một API — chỉ lúc GẮN model mới phân biệt, xem {@link #addMonsterOrNpc} về lý do.
 *
 * <p>Góc camera là <b>hằng số runtime</b>, không nướng vào tài sản — đó là điều ADR-031 mua được khi
 * bỏ pre-render sprite. Đổi {@link #VIEW_YAW_DEGREES} là đổi cả game, không phải render lại asset nào.
 *
 * <p>Lớp này <b>tự tắt</b> khi không tìm thấy model NHÂN VẬT và để {@link WorldScreen} vẽ hộp như cũ.
 * Đó không phải phòng thủ thừa: model nằm ở {@code assets/exported/models/}, sinh ra từ pipeline art
 * và <b>không commit</b> ([ART-PIPELINE §7](docs/14-art/ART-PIPELINE.md)), nên một bản checkout sạch
 * chưa chạy pipeline sẽ không có chúng. Crash lúc khởi động ở trường hợp đó là sai. Thiếu model
 * QUÁI thì chỉ con đó vẽ hộp — xem {@link #addMonsterOrNpc}.
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
public final class WorldModelRenderer implements Disposable {

    private static final Logger LOG = LoggerFactory.getLogger(WorldModelRenderer.class);

    /**
     * Góc quay của CAMERA quanh trục đứng, độ.
     *
     * <p>⚠️ <b>PHẢI là 180°</b> kể từ [ADR-033](docs/01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md)
     * (2026-08-09) — đây KHÔNG còn là lựa chọn thẩm mỹ tự do như thời ADR-031. Người chơi giờ chỉ di
     * chuyển trên trục world {@code X}, nên trục đó BẮT BUỘC phải nằm ngang trên màn hình, đúng
     * chiều: {@code right = direction × up} phải có thành phần {@code x} DƯƠNG.
     *
     * <p>Với công thức {@link #applyLockedAngle}, {@code right = (-cos(yaw)·cos(pitch), sin(yaw)·cos(pitch), 0)}
     * ⇒ điều kiện trên thành {@code cos(yaw) < 0}. Giá trị cũ <b>35°</b> vi phạm
     * ({@code cos35 = +0.82}): world {@code +X} chiếu thành hướng sang TRÁI màn hình — bấm phải thì
     * nhân vật chạy sang trái, và còn trôi chéo xuống ~19° vì {@code +X} cũng có thành phần dọc.
     * Chủ dự án phát hiện ngay khi test tay. {@code 180°} cho {@code right = (+1, 0, 0)} chuẩn xác:
     * đi ngang tuyệt đối, nhảy lên tuyệt đối — đúng cảm giác side-scroller của ADR-033.
     *
     * <p>Góc 3/4 của NHÂN VẬT mà chủ dự án duyệt ở
     * [ART-QUALITY-STANDARD §7.1](docs/14-art/ART-QUALITY-STANDARD.md) không mất đi — nó chuyển sang
     * {@link #MODEL_YAW_DEGREES} (xoay MODEL thay vì xoay camera), tách bạch hai mối quan tâm vốn bị
     * gộp làm một: "nhân vật trông nghiêng bao nhiêu" vs "trục gameplay nằm đâu trên màn hình".
     */
    public static final float VIEW_YAW_DEGREES = 180f;

    /**
     * Hiệu chỉnh độ nghiêng CÓ SẴN trong chính model, độ — xoay thêm góc này thì nhân vật mới nhìn
     * THẲNG vào camera.
     *
     * <p><b>Đo bằng ảnh chụp, không suy từ quy ước glTF</b>: render ở yaw {@code 0°} cho thấy nhân
     * vật đã nghiêng sẵn ~45° sang phải chứ KHÔNG chính diện (suy luận Blender→glTF "nhân vật hướng
     * −Y" không đúng với bộ model của dự án này — pipeline art dựng nhân vật ở tư thế nghiêng sẵn).
     * Nhầm điểm gốc này làm mọi phép đối xứng trái/phải lệch đi 45°, sinh ra triệu chứng khó hiểu:
     * {@code −35°} ra gần chính diện còn {@code +35°} ra LƯNG, dù hai góc chỉ cách nhau 70°.
     */
    private static final float MODEL_BASE_YAW_CORRECTION = -45f;

    /**
     * Góc 3/4 tính TỪ hướng chính diện (sau khi đã trừ {@link #MODEL_BASE_YAW_CORRECTION}) — giữ
     * dáng đã duyệt ở ART-QUALITY-STANDARD §7.1 sau khi {@link #VIEW_YAW_DEGREES} bị ADR-033 khoá
     * cứng vào 180°. Ở góc nghiêng thuần (90°), khối tóc của mesh sinh từ Text→3D che kín mặt — đó
     * là lý do bản pilot chọn 3/4 ngay từ đầu.
     */
    private static final float MODEL_YAW_DEGREES = 35f;

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
     * <p>⚠️ <b>Dấu là {@code +90f}</b> — khớp ĐÚNG quy tắc bàn tay phải: xoay quanh {@code X} một
     * góc {@code +90°} đưa {@code +Y} cục bộ (đầu nhân vật, quy ước Y-up của glTF) về {@code +Z} thế
     * giới (trời); {@code -90f} đưa nó về {@code -Z} (đất) tức LỘN NGƯỢC.
     *
     * <p><b>Lịch sử — đừng lặp lại</b>: bản 2026-08-08 từng đổi hằng số này thành {@code -90f} và
     * ghi rằng "ảnh chụp thật bác bỏ quy tắc bàn tay phải". Kết luận đó SAI, và sai vì <b>công cụ
     * chụp</b> chứ không phải vì engine: {@code ScreenUtils.getFrameBufferPixmap} trả dữ liệu THÔ
     * của GL (dòng 0 = đáy màn hình) còn PNG quy ước dòng 0 = đỉnh, nên ảnh lưu ra bị LẬT DỌC —
     * nhân vật đang lộn ngược trên màn hình lại hiện "đứng thẳng" trong ảnh. Chủ dự án phát hiện
     * bằng mắt trên game thật (2026-08-09); chụp lại với {@code flipY=true} xác nhận {@code -90f}
     * cho nhân vật chúc đầu xuống đất. Bài học lặp lại lần thứ hai của dự án (lần đầu:
     * script chụp không nhận biết DPI, RENDERING.md §11.1) — <b>ảnh chụp chỉ đáng tin khi đường
     * chụp đã được kiểm chứng</b>; khi ảnh mâu thuẫn với phép toán rõ ràng, nghi ngờ cái chụp trước.
     *
     * <p>Hằng số, không dựng lại mỗi frame (/CLAUDE.md §6).
     */
    private static final Matrix4 Y_UP_TO_Z_UP = new Matrix4().setToRotation(Vector3.X, 90f);

    /**
     * Phép xoay áp cho model khi nhân vật hướng SANG PHẢI: đổi hệ trục ({@link #Y_UP_TO_Z_UP}) RỒI
     * xoay yaw quanh trục đứng world ({@link #MODEL_YAW_DEGREES}). Thứ tự nhân {@code Rz · Rx} —
     * đọc từ phải sang: đỉnh mesh đi qua {@code Rx} trước (về Z-up), rồi mới {@code Rz} (quay mặt).
     * Đảo thứ tự là xoay quanh trục sai hoàn toàn.
     *
     * <p>Hằng số, dựng 1 lần — không tạo {@code Matrix4} mỗi frame (/CLAUDE.md §6).
     */
    private static final Matrix4 ORIENTATION_FACING_RIGHT = new Matrix4()
            .setToRotation(Vector3.Z, MODEL_BASE_YAW_CORRECTION + MODEL_YAW_DEGREES)
            .mul(Y_UP_TO_Z_UP);

    /**
     * Hướng SANG TRÁI — ảnh gương của {@link #ORIENTATION_FACING_RIGHT} qua mặt phẳng màn hình,
     * tức <b>{@code −yaw}</b>.
     *
     * <p>Đối xứng phải LẤY QUANH HƯỚNG CHÍNH DIỆN, tức quanh {@link #MODEL_BASE_YAW_CORRECTION},
     * chứ không quanh 0: {@code base + yaw} ↔ {@code base − yaw}. Lấy đối xứng quanh 0 (sai lầm ban
     * đầu) làm một bên thành 3/4 mặt trước còn bên kia thành LƯNG, vì điểm gốc lệch 45°.
     *
     * <p>Không dùng scale âm để lật: nó đảo chiều pháp tuyến, hỏng cel shading và viền inverted-hull
     * (RENDERING.md §12.2).
     */
    private static final Matrix4 ORIENTATION_FACING_LEFT = new Matrix4()
            .setToRotation(Vector3.Z, MODEL_BASE_YAW_CORRECTION - MODEL_YAW_DEGREES)
            .mul(Y_UP_TO_Z_UP);

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
    private final Map<Long, Scene> scenesById = new HashMap<>();
    private final Map<Long, AnimationState> animationStateById = new HashMap<>();
    /**
     * Danh sách vẽ song song với {@link #scenesById}, đồng bộ ở {@link #addCharacter}/
     * {@link #remove}. Riêng vì {@code render()} lặp nó MỖI KHUNG (2 lượt vẽ + 1 lượt
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
    public WorldModelRenderer(GraphicsTier tier) {
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
    public void addCharacter(long id, String assetKey) {
        if (!enabled || scenesById.containsKey(id)) {
            return;
        }
        if (!attach(id, assetKey)) {
            // Lần đầu thiếu model NHÂN VẬT là tắt hẳn nhánh 3D, không phải cảnh báo mỗi lần spawn:
            // bộ 45 file nhân vật ra lò cùng một mẻ (ART-PIPELINE §7), thiếu một cái gần như luôn có
            // nghĩa là chưa chạy pipeline art chứ không phải hỏng đúng một file. Quái thì ngược lại —
            // xem {@link #addMonsterOrNpc}.
            LOG.warn("Không thấy model nhân vật '{}' — nhánh 3D tắt, quay về hình thay thế", assetKey);
            enabled = false;
        }
    }

    /**
     * Gắn model cho một con quái hoặc một NPC (2026-08-09). Cùng máy móc {@link #addCharacter} nhưng
     * khác đúng một điều: <b>thiếu model ở đây KHÔNG tắt nhánh 3D</b>.
     *
     * <p>Tên method gộp quái với NPC theo đúng cách server gộp chúng
     * ({@code WorldFacade.nextMonsterOrNpcId} cấp id cho cả hai từ một bộ đếm): với lớp này chúng
     * giống hệt nhau — id âm, art làm từng con, không nhảy.
     *
     * <p>Lý do nới luật là thực tế của chính kho asset này: art quái/NPC chạy sau art nhân vật và
     * chạy từng con một, nên "có model cho {@code training_dummy} mà chưa có cho {@code sample_boss}"
     * là trạng thái BÌNH THƯỜNG chứ không phải dấu hiệu hỏng. Áp luật của {@link #addCharacter} vào
     * đây sẽ biến một con boss chưa được vẽ thành lệnh tắt 3D cho toàn bộ nhân vật đang trên màn
     * hình — hỏng nhiều hơn hẳn thứ nó bảo vệ.
     *
     * @return {@code true} nếu thực thể này giờ có model thật để vẽ. {@code false} thì người gọi
     *     phải tự vẽ hình thay thế CHO RIÊNG nó ({@link MonsterPresenter}/{@link NpcPresenter} vẽ
     *     hộp), phần còn lại của thế giới vẫn 3D.
     */
    public boolean addMonsterOrNpc(long entityId, String assetKey) {
        if (!enabled) {
            return false; // Không có nhánh 3D nào để gắn vào — người gọi vẽ hộp như trước ADR-031.
        }
        if (scenesById.containsKey(entityId)) {
            return true;
        }
        boolean attached = attach(entityId, assetKey);
        if (!attached) {
            LOG.info("Chưa có model '{}' — thực thể này vẽ bằng hộp, nhánh 3D giữ nguyên", assetKey);
        }
        return attached;
    }

    /**
     * Nạp (hoặc dùng lại) {@code assetKey} rồi dựng một {@link Scene} riêng cho {@code id}.
     *
     * @return {@code false} khi không có file {@code .glb} — người gọi quyết định điều đó nghĩa là
     *     gì, vì hậu quả khác nhau giữa nhân vật và quái.
     */
    private boolean attach(long id, String assetKey) {
        SceneAsset asset = assetsByKey.get(assetKey);
        if (asset == null) {
            FileHandle file = Gdx.files.internal(modelDirectory + assetKey + ".glb");
            if (!file.exists()) {
                return false;
            }
            asset = new GLBLoader().load(file);
            assetsByKey.put(assetKey, asset);
        }
        // Mỗi thực thể một Scene riêng nhưng dùng chung SceneAsset: model instance tách nhau để có
        // transform và trạng thái animation riêng, còn mesh/texture chỉ nạp một lần.
        Scene scene = new Scene(asset.scene);
        scenesById.put(id, scene);
        scenes.add(scene);
        // Tư thế ban đầu là idle, đặt thẳng (không crossfade) vì chưa có gì để chuyển tiếp từ đó —
        // tránh model đứng bind pose tĩnh (§6.5 mục 3 của RENDERING.md, giờ đã nối).
        scene.animationController.setAnimation(CharacterAnimationClips.IDLE, -1);
        animationStateById.put(id, new AnimationState(id));
        return true;
    }

    /**
     * True nếu {@code id} đang có model 3D thật trên màn hình. Người gọi dùng để quyết định vẽ hình
     * thay thế ({@code MonsterPresenter}) hay phát animation ({@link CombatAnimationTrigger}) —
     * false có thể vì nhánh 3D đang tắt, vì con quái đó chưa có art, hoặc vì id không thuộc thực thể
     * nào đang hiển thị.
     */
    public boolean hasModel(long id) {
        return scenesById.containsKey(id);
    }

    /**
     * Thay model của một nhân vật ĐANG hiển thị bằng model khác, giữ nguyên chỗ đứng của nó trong
     * thế giới (ADR-045 — đổi hệ phái giữa phiên chơi).
     *
     * <p>Khác {@link #remove} + {@link #addCharacter} ở đúng chỗ quan trọng: hướng mặt và trạng
     * thái "đang nằm chết" được chép sang model mới. Gỡ rồi gắn lại trần sẽ làm nhân vật đang quay
     * trái bật về quay phải, và làm một xác chết đứng dậy tư thế idle.
     *
     * <p>Vị trí KHÔNG cần chép: {@code WorldScreen} gọi {@link #setPosition} cho mọi thực thể mỗi
     * khung hình, nên model mới vào đúng chỗ ngay khung kế tiếp.
     *
     * @return {@code true} nếu đã thay thật. {@code false} khi nhánh 3D đang tắt, khi {@code id}
     *     không có model nào trên màn hình (không có gì để thay), hoặc khi model mới thiếu file —
     *     trường hợp cuối giữ nguyên model CŨ thay vì bỏ nhân vật khỏi màn hình.
     */
    public boolean replaceCharacterModel(long id, String assetKey) {
        AnimationState previous = animationStateById.get(id);
        if (!enabled || previous == null) {
            return false;
        }
        boolean facingRight = previous.facingRight;
        boolean dead = previous.dead;
        Scene existing = scenesById.get(id);
        remove(id);
        if (!attach(id, assetKey)) {
            LOG.warn("Không thấy model nhân vật '{}' — giữ nguyên model đang hiện", assetKey);
            scenesById.put(id, existing);
            scenes.add(existing);
            animationStateById.put(id, previous);
            return false;
        }
        animationStateById.get(id).facingRight = facingRight;
        if (dead) {
            playDie(id); // tự đặt lại cờ dead + dừng ở tư thế cuối của clip die.
        }
        return true;
    }

    public void remove(long id) {
        Scene scene = scenesById.remove(id);
        if (scene != null) {
            scenes.removeValue(scene, true); // so sánh bằng ==, đúng vì mỗi Scene là 1 instance riêng
        }
        animationStateById.remove(id);
    }

    /**
     * Dời model của một nhân vật tới vị trí đã nội suy trong frame này. {@code z} thêm ở ADR-033
     * (2026-08-08) — chiều cao lơ lửng khi nhảy; nhân vật KHÁC (chưa broadcast z qua
     * {@code CharacterMovedEvent}, xem movement.proto) luôn truyền {@code 0f}, hiện đúng như trước.
     */
    public void setPosition(long id, float x, float y, float z) {
        Scene scene = scenesById.get(id);
        if (scene != null) {
            AnimationState state = animationStateById.get(id);
            // Gốc model nằm ở chân (bước E của pipeline art đảm bảo điều đó) nên z=0 nghĩa là đứng
            // trên đất — chỉ xoay về Z-up (kèm yaw theo hướng mặt) rồi tịnh tiến. `trn` ghi thẳng
            // vào cột tịnh tiến, giữ nguyên phần xoay và không cấp phát gì.
            Matrix4 orientation =
                    state == null || state.facingRight ? ORIENTATION_FACING_RIGHT : ORIENTATION_FACING_LEFT;
            scene.modelInstance.transform.set(orientation).trn(x, y, z);
        }
    }

    /**
     * Hướng mặt nhân vật theo chiều di chuyển (ADR-033 — side-scroller 1 trục). Người gọi tự giữ
     * "hướng gần nhất" khi đứng yên: hàm này KHÔNG suy ra từ vận tốc, vì dừng lại phải giữ nguyên
     * mặt chứ không quay về mặc định.
     */
    public void setFacingRight(long id, boolean facingRight) {
        AnimationState state = animationStateById.get(id);
        if (state != null) {
            state.facingRight = facingRight;
        }
    }

    /**
     * Đặt lại clip loop (idle/run) mong muốn của một nhân vật — gọi mỗi frame từ
     * {@code WorldScreen}, giống {@link #setPosition}. Không cắt ngang một one-shot
     * (attack/hit/die/skill_cast) đang chạy; clip loop chỉ được áp lại khi one-shot đó kết thúc
     * (xem {@link AnimationState#onEnd}).
     *
     * @param moving true = {@link CharacterAnimationClips#RUN}, false = {@link
     *     CharacterAnimationClips#IDLE} (RENDERING.md §11.1).
     */
    public void setMoving(long id, boolean moving) {
        AnimationState state = animationStateById.get(id);
        if (state == null || state.dead) {
            return; // Chưa có Scene, hoặc đã chết — giữ nguyên tư thế die tới khi hồi sinh.
        }
        state.moving = moving;
        if (!state.oneShotPlaying) {
            applyLoopClip(state);
        }
    }

    /**
     * Đặt lại trạng thái lơ lửng (ADR-033) — {@link CharacterAnimationClips#JUMP} ưu tiên cao nhất
     * trong {@link #applyLoopClip}, cùng cơ chế/lý do với {@link #setMoving}.
     */
    public void setAirborne(long id, boolean airborne) {
        AnimationState state = animationStateById.get(id);
        if (state == null || state.dead) {
            return;
        }
        state.airborne = airborne;
        if (!state.oneShotPlaying) {
            applyLoopClip(state);
        }
    }

    /** Đòn đánh thường — luôn phát dù trúng hay né (đó là động tác vung đòn, không phải kết quả). */
    public void playAttack(long id) {
        playOneShot(id, CharacterAnimationClips.ATTACK);
    }

    /** Phản ứng trúng đòn — KHÔNG gọi khi mục tiêu chết (dùng {@link #playDie} thay vào đó). */
    public void playHit(long id) {
        playOneShot(id, CharacterAnimationClips.HIT);
    }

    /** Dùng kỹ năng — cho cả người dùng skill lẫn (gián tiếp qua {@code WorldScreen}) không áp cho quái. */
    public void playSkillCast(long id) {
        playOneShot(id, CharacterAnimationClips.SKILL_CAST);
    }

    /**
     * HP về 0 — phát một lần, dừng ở tư thế cuối (ART-PIPELINE §4.2: "không loop"), KHÔNG tự quay
     * lại idle/run như các one-shot khác. Giữ nguyên tới khi {@link #resetAfterRespawn}.
     */
    public void playDie(long id) {
        AnimationState state = animationStateById.get(id);
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
    public void resetAfterRespawn(long id) {
        AnimationState state = animationStateById.get(id);
        Scene scene = scenesById.get(id);
        if (state == null || scene == null) {
            return;
        }
        state.dead = false;
        state.oneShotPlaying = false;
        state.moving = false;
        state.airborne = false;
        state.currentClip = CharacterAnimationClips.IDLE;
        scene.animationController.setAnimation(CharacterAnimationClips.IDLE, -1);
    }

    /**
     * True nếu clip {@code die} đang/đã phát và chưa {@link #resetAfterRespawn}. {@code WorldScreen}
     * dùng cờ này để suy ra "nhân vật KHÁC vừa hồi sinh" từ gói di chuyển đầu tiên sau đó — giao
     * thức không phát {@code CharacterRespawnedEvent} cho ai khác ngoài chính người hồi sinh.
     */
    public boolean isDead(long id) {
        AnimationState state = animationStateById.get(id);
        return state != null && state.dead;
    }

    private void applyLoopClip(AnimationState state) {
        Scene scene = scenesById.get(state.id);
        String desired = clipOrIdle(scene, CharacterAnimationClips.loopingClipFor(state.airborne, state.moving));
        if (desired.equals(state.currentClip)) {
            return; // AnimationController restart clip từ đầu mỗi lần setAnimation/animate được gọi
            // — tránh gọi lại khi clip mong muốn không đổi (mỗi frame gọi setMoving).
        }
        state.currentClip = desired;
        scene.animationController.animate(desired, -1, 1f, null, CLIP_TRANSITION_SECONDS);
    }

    /**
     * Đổi clip mong muốn thành clip model NÀY thật sự có, nếu không thì {@code idle}.
     *
     * <p>Bộ 7 clip của ART-PIPELINE §4.2 là chuẩn cho NHÂN VẬT; model khác hoàn toàn có thể mang ít
     * hơn một cách chính đáng. {@code mob_training-dummy} có đúng 5 ({@code idle run attack hit die})
     * — không {@code jump}, không {@code skill_cast}, vì không có đường code nào phía server làm quái
     * nhảy hay niệm chiêu, và dựng animation chết là lãng phí công art.
     *
     * <p>Bắt buộc phải lọc chứ không thể "cứ gọi rồi tính": {@code AnimationController.animate} với
     * tên clip không tồn tại ném {@code GdxRuntimeException} giữa game loop — một model thiếu clip sẽ
     * đánh sập cả khung hình, không chỉ con quái đó. {@code idle} là lựa chọn thay thế an toàn duy
     * nhất vì nó là clip DUY NHẤT mọi model đều phải có ({@link #attach} đặt nó ngay lúc gắn).
     */
    private static String clipOrIdle(Scene scene, String desired) {
        return scene.modelInstance.getAnimation(desired) == null ? CharacterAnimationClips.IDLE : desired;
    }

    private void playOneShot(long id, String clip) {
        AnimationState state = animationStateById.get(id);
        if (state == null || state.dead) {
            return; // Đã chết — không phát chồng animation khác lên xác tới khi hồi sinh.
        }
        playOneShotState(state, clip);
    }

    private void playOneShotState(AnimationState state, String requestedClip) {
        Scene scene = scenesById.get(state.id);
        String clip = clipOrIdle(scene, requestedClip);
        if (state.oneShotPlaying && clip.equals(state.currentClip)) {
            // Cùng một one-shot đã đang chạy — bỏ qua thay vì restart giữa chừng. Xảy ra thật khi
            // WorldScreen dự đoán skill_cast cục bộ lúc bấm nút rồi SkillResultEvent xác nhận tới
            // ngay sau đó (CLIENT.md "Đúng cách": dự đoán animation chính mình là hợp lệ).
            return;
        }
        state.oneShotPlaying = true;
        state.currentClip = clip;
        scene.animationController.animate(clip, 1, 1f, state, CLIP_TRANSITION_SECONDS);
    }

    /** Camera bám nhân vật của người chơi, giữ nguyên góc khoá. */
    public void follow(float x, float y) {
        target.set(x, y, 0f);
        camera.position.set(target).mulAdd(camera.direction, -CAMERA_DISTANCE);
        camera.update();
    }

    /**
     * Ma trận chiếu kết hợp của camera 3D — cho người gọi vẽ thêm hình không phải model (bệ demo,
     * dấu mục tiêu, hộp thay thế cho quái chưa có art) khớp đúng không gian chiếu với model thay vì
     * dùng camera 2D phẳng không liên quan tới góc nhìn đã khoá.
     */
    public Matrix4 combined() {
        return camera.combined;
    }

    /**
     * Chiếu một điểm THẾ GIỚI sang toạ độ MÀN HÌNH, ghi đè tại chỗ (không cấp phát — Điều 31).
     * Dùng cho thứ neo theo world nhưng phải vẽ ở không gian màn hình để chữ sắc nét, VD số damage
     * bay lên ({@code FloatingCombatText}).
     */
    public void project(Vector3 worldPosition) {
        camera.project(worldPosition);
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
     * {@link #setPosition} luôn lan tới ma trận xương trong CÙNG khung hình — rẻ (1 mesh,
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
        scenesById.clear();
        scenes.clear();
        animationStateById.clear();
    }

    /**
     * Trạng thái animation của MỘT nhân vật (clip hiện tại, có đang chạy one-shot không, đã chết
     * chưa). Là {@code AnimationListener} luôn của chính nó thay vì cấp phát 1 listener riêng mỗi
     * lần gọi {@link #playOneShot} — nghe xong 1 one-shot, tự áp lại clip loop mong muốn
     * ({@link #applyLoopClip}), trừ khi vừa mới {@code die} (giữ nguyên tư thế cuối).
     */
    private final class AnimationState implements AnimationController.AnimationListener {
        private final long id;
        private String currentClip = CharacterAnimationClips.IDLE;
        private boolean moving;
        private boolean airborne;
        private boolean oneShotPlaying;
        private boolean dead;
        // Mặc định quay phải — khớp hướng đi mặc định của side-scroller, và là giá trị hợp lý lúc
        // spawn khi chưa có lệnh di chuyển nào để suy ra hướng.
        private boolean facingRight = true;

        AnimationState(long id) {
            this.id = id;
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
