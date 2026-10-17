package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.Platform;
import dev.kage.protocol.WorldKind;

/**
 * Hình minh hoạ cho bệ nhảy demo ([ADR-033](docs/01-architecture/adr/ADR-033-Ninja-School-Style-Movement.md)
 * "Việc cần làm tiếp theo" #7) — toạ độ HARDCODE khớp {@code server-world.WorldFacade.DEMO_PLATFORMS}.
 *
 * <p><b>Không phải nguồn chân lý</b>: server mới là bên quyết định đáp đất/độ cao (Điều 15), lớp này
 * chỉ vẽ cho người chơi THẤY bệ mà vật lý server đã tính. Chưa có packet nào mô tả địa hình — khi có
 * map pilot thật (ADR-033 #7) thì client nhận toạ độ từ server và lớp này bị xoá, không giữ lại.
 *
 * <p>Tách khỏi {@link WorldScreen} để {@code CharacterOrientationDebugTool} vẽ được ĐÚNG hình học
 * này lúc chụp ảnh kiểm chứng — không phải bản sao chép tay dễ lệch khỏi cái chạy thật.
 */
final class DemoPlatforms {

    /** {@code {minX, minY, maxX, maxY, topZ}} — khớp từng dòng với {@code WorldFacade.DEMO_PLATFORMS}. */
    private static final float[][] PLATFORMS = {
        {3f, -0.75f, 7f, 0.75f, 0.8f},
        {9f, -0.75f, 13f, 0.75f, 1.5f},
        {15f, -0.75f, 19f, 0.75f, 2.2f},
    };

    /** Vẽ ở World không có bệ — hằng số để vòng vẽ mỗi frame không cấp phát (CLAUDE.md §6). */
    private static final float[][] NO_PLATFORMS = new float[0][];

    /** Dày bao nhiêu tính XUỐNG từ mặt bệ — thuần thẩm mỹ, vật lý chỉ biết {@code topZ}. */
    private static final float THICKNESS = 0.3f;

    /**
     * Dải nền đất minh hoạ ({@code z=0}) — {@code minX, maxX}. Vật lý coi mặt đất phẳng {@code z=0}
     * là VÔ HẠN ({@code VerticalMovementSystem.GROUND_Z}), nên dải này chỉ để mắt có mốc, không
     * phải biên thật; đi quá dải vẫn đứng trên đất bình thường.
     *
     * <p><b>Dải này phải PHỦ HẾT mọi điểm spawn</b>, và đó là một luật đã bị vi phạm trong im lặng
     * hai lần: bản đầu để {@code -30}, rồi V32 dời thạch vệ sang {@code -33}/{@code -20} và V33
     * thêm sân khảo hạch ở {@code -47}. Vật lý không hề gì (mặt đất vô hạn), nhưng người chơi đi
     * sang tây thì thấy mình và lũ quái đứng lơ lửng trong khoảng đen — nhìn y như lỗi render.
     * Playtest 2026-09-06 bắt được. Đầu tây nay là {@code -55}: phủ sân khảo hạch {@code -47} còn
     * dư 8m. Đầu đông {@code 60} vẫn phủ quỷ đá {@code 47}.
     *
     * <p>⚠️ Thêm điểm spawn ngoài đoạn {@code [-55, 85]} thì phải nới dải này CÙNG migration —
     * `monster_spawn` sống trong DB còn hai số này là hằng số client, không có gì nối chúng lại
     * ngoài dòng ghi chú này. Khi có map thật (ADR-033 #7) thì cả lớp này biến mất.
     *
     * <p><b>Đầu đông nới 60 → 85 (2026-09-12, V40).</b> Không phải cho đẹp: trục chơi đã HẾT chỗ
     * đặt điểm spawn mới. Đo bằng máy với một loài {@code aggro = 6} và luật giãn V29
     * ({@code |A−B| > aggroA + aggroB}), cộng vùng an toàn V35 và ba bệ nhảy, cả đoạn
     * {@code [-55, 60]} chỉ còn đúng hai khe hợp lệ: một khe rộng 1m ngay chỗ hình nộm đang đứng
     * (x≈8) và đúng một điểm {@code x = 59.5} sát mép. Hang bỏ hoang của arc 2 vì thế cần đất mới,
     * và chủ dự án chọn nới về đông thay vì dựng hầm ngục thứ hai (hiện chỉ có MỘT hầm ngục
     * hardcode trong Java) — xem ROADMAP mục 73.
     */
    // V57 (2026-10-17) nới -55 → -61: cỗ khảo hạch dời về -52 khi giãn cả trục theo vế tuần tra, biên 9m.
    private static final float GROUND_MIN_X = -61f;

    /**
     * <b>Đầu đông nới 170 → 210 (2026-09-16, V48).</b> Lần thứ TƯ, và là lần cuối của v1: V48 mở
     * quãng Lv28–36 — nhịp ĐÓNG arc 2 — với ba điểm spawn ở {@code x = 175, 188, 201}. Sau quỷ đá
     * {@code @161} chỉ còn 9m trống, không đủ cho một điểm nào vì luật giãn đòi khe {@code > 12m}.
     * 210 = 201 + 9m biên, giữ đúng quy ước ba lần nới trước tự đặt.
     *
     * <p>⚠️ Bốn lần nới trong hai tháng là một tín hiệu, không phải một tiện tay: trục chơi MỘT
     * chiều đang cạn, và arc 3–6 (Lv37–100) đòi "toàn bộ Hoang Vực"/"toàn bản đồ" theo STORY §6.
     * Nới lần thứ năm không phải câu trả lời — cần hệ thống zone, và đó là quyết định kiến trúc
     * nằm NGOÀI phạm vi v1.
     *
     * <p><b>Đầu đông nới 115 → 170 (2026-09-14, V47).</b> Lần thứ BA, và vẫn cùng một lý do: trục chơi
     * hết chỗ. V47 mở quãng Lv22–27 của arc 2 với ba điểm spawn ở {@code x = 120, 134, 148} (hai tế
     * đồ Huyết Sa và một đàn chủ), trong khi sau quỷ đá {@code @106} chỉ còn đúng 9m trống — không
     * đủ cho một điểm nào, vì luật giãn V29/V32 đòi khe {@code > 12m}.
     *
     * <p><b>Và cùng đợt, quỷ đá đi tiếp {@code 106 → 161}</b> — playtest bằng {@code gameProbe} đo
     * được nó lấy 27 máu mỗi lượt người chơi đi từ khe nghỉ @80 ra mạch chết @120, kéo biên trận tế
     * đồ từ +2 xuống +1. Nên điểm xa nhất nay là CHÍNH NÓ, không phải đàn chủ @148.
     *
     * <p>170 = 161 + 9m biên, giữ nguyên khoảng dư mà V45 để lại sau {@code @106}: phủ hết điểm xa
     * nhất nhưng không để nó sát mép (ràng buộc (a) V40 tự đặt).
     *
     * <p><b>Đầu đông nới 85 → 115 (2026-09-13, V45).</b> Lần thứ hai: V45 thêm sào huyệt đoạt mạch
     * ở {@code x = 93} và DỜI quỷ đá {@code 80 → 106} để dọn đường đông (nó là con đánh đau nhất
     * trục chơi, atk 40, và V40 đã phải dời nó một lần rồi vì đúng chuyện đó).
     */
    // V57 (2026-10-17) nới 210 → 225: cỗ khảo hạch Trúc Cơ dời 201 → 216, biên 9m (lần thứ NĂM — xem cảnh báo trên).
    private static final float GROUND_MAX_X = 225f;

    private DemoPlatforms() {}

    /**
     * Cùng bộ toạ độ trên, đóng gói cho {@code VerticalMovementSystem} phía CLIENT dự đoán cục bộ.
     *
     * <p>⚠️ Client PHẢI nạp đúng danh sách này thì dự đoán nhảy mới khớp server. Thiếu nó, client
     * tính "không có bệ nào" và luôn kéo nhân vật về {@code z=0}, trong khi server báo đang đứng
     * trên bệ — độ lệch (0.8m ở bệ thấp nhất) NHỎ HƠN {@code WorldConstants.RECONCILE_SNAP_THRESHOLD}
     * (1.0) nên cơ chế sửa sai không kích hoạt, và người chơi thấy nhân vật rơi xuyên qua bệ mãi
     * mãi. Đây là bug thật chủ dự án phát hiện khi test tay (2026-08-09).
     */
    static List<Platform> physics() {
        return physics(WorldKind.WORLD_KIND_MAIN);
    }

    /**
     * Bộ bệ của ĐÚNG World server nói nhân vật đang đứng — rỗng ở mọi World không phải world chính.
     *
     * <p>Server chỉ nạp {@code DEMO_PLATFORMS} khi dựng world chính ({@code spawnOverworldContent});
     * hầm ngục và đấu trường KHÔNG có bệ nào. Trước 2026-09-30 client vẫn nạp đủ ba bệ ở mọi nơi —
     * đúng bug của javadoc {@link #physics()} nhưng theo chiều NGƯỢC: trong bí cảnh client dự đoán
     * đứng trên bệ cao 0,8m còn server nói {@code z=0}, lệch dưới ngưỡng snap nên không bao giờ được
     * sửa ⇒ nhân vật đi trên một cái bệ không tồn tại. Boss bí cảnh đứng ở {@code x=2,95}, ngay mép
     * bệ đầu tiên, nên đó là chỗ người chơi đánh nhau.
     */
    static List<Platform> physics(WorldKind kind) {
        if (!hasPlatforms(kind)) {
            return List.of();
        }
        List<Platform> result = new ArrayList<>(PLATFORMS.length);
        for (float[] platform : PLATFORMS) {
            result.add(new Platform(new Aabb(platform[0], platform[1], platform[2], platform[3]), platform[4]));
        }
        return result;
    }

    /**
     * Vẽ khối cho từng bệ bằng {@code projection} của nhánh render đang hoạt động (giống
     * {@code MonsterPresenter.render} — dùng sai ma trận là lệch hẳn không gian chiếu).
     *
     * <p>⚠️ {@link ShapeRenderer#box} nhận toạ độ GÓC (không phải tâm) và tự đảo dấu {@code depth}
     * (khối kéo dài xuống {@code -Z}). Nên truyền thẳng {@code topZ} làm {@code z} là đúng: mặt TRÊN
     * của khối nằm đúng mặt bệ mà nhân vật đứng lên.
     */
    static void render(ShapeRenderer shapeRenderer, Matrix4 projection) {
        render(shapeRenderer, projection, WorldKind.WORLD_KIND_MAIN);
    }

    /**
     * Như {@link #render(ShapeRenderer, Matrix4)} nhưng chỉ vẽ bệ ở World CÓ bệ — cùng luật với
     * {@link #physics(WorldKind)}, để thứ người chơi THẤY khớp thứ vật lý tính. Nền đất thì vẽ ở mọi
     * World: mặt đất {@code z=0} là vô hạn ở cả server lẫn client, bí cảnh cũng vậy.
     */
    static void render(ShapeRenderer shapeRenderer, Matrix4 projection, WorldKind kind) {
        // Depth test BẮT BUỘC — đây là hình khối 3D thật phải che/bị che đúng với nhân vật (ADR-031
        // §9 "thứ tự che nhau: depth buffer"). Thiếu dòng này, ShapeRenderer vẽ SAU nên tô đè phẳng
        // lên nhân vật, nhân vật đứng trên bệ biến mất hoàn toàn — đã thấy thật qua ảnh chụp.
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Nền đất trước — mặt trên đúng z=0, khớp GROUND_Z của VerticalMovementSystem. Không có nó
        // thì nhân vật trông như đứng lơ lửng giữa nền trời, không có mốc nào để thấy mình cao/thấp.
        shapeRenderer.setColor(Color.FOREST);
        shapeRenderer.box(
                GROUND_MIN_X,
                PLATFORMS[0][1],
                0f,
                GROUND_MAX_X - GROUND_MIN_X,
                PLATFORMS[0][3] - PLATFORMS[0][1],
                THICKNESS);

        shapeRenderer.setColor(Color.OLIVE);
        for (float[] platform : hasPlatforms(kind) ? PLATFORMS : NO_PLATFORMS) {
            shapeRenderer.box(
                    platform[0],
                    platform[1],
                    platform[4],
                    platform[2] - platform[0],
                    platform[3] - platform[1],
                    THICKNESS);
        }
        shapeRenderer.end();
        // Trả lại trạng thái cho HUD Scene2D vẽ sau (UI phẳng, không cần depth) — không để rò rỉ
        // trạng thái GL sang lớp khác.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /** Khớp {@code WorldFacade}: chỉ world chính được nạp {@code DEMO_PLATFORMS}. */
    private static boolean hasPlatforms(WorldKind kind) {
        return kind == WorldKind.WORLD_KIND_MAIN;
    }
}
