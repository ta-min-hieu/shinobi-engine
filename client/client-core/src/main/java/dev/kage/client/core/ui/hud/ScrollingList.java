package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Một danh sách dài bên trong cửa sổ HUD: vùng cuộn DỌC có chiều cao bị CHẶN, cộng một trần số dòng
 * được DỰNG. Mọi cửa sổ có danh sách do server quyết độ dài (túi đồ, hòm thư, cửa hàng, bang hội,
 * giao dịch, bạn bè, nhật ký nhiệm vụ) dùng chung lớp này.
 *
 * <h2>Vì sao lớp này tồn tại (2026-09-11)</h2>
 *
 * Trước nó, BẢY cửa sổ tự đặt trần riêng — 6, 8, 8, 6, 8, 8, 10 dòng — và sáu trong số đó chép lại
 * CÙNG một lý do: "{@code KageSkin} chưa có style {@code ScrollPane} nào, nên danh sách dài sẽ đội
 * khung cao vượt khỏi màn hình". Lý do đó đã chết từ lúc {@link KageSkin#scrollPaneStyle} ra đời,
 * nhưng các bản chép thì không ai sửa theo, và trần "cho vừa màn hình" âm thầm biến thành trần "giấu
 * dữ liệu": phần vượt trần KHÔNG được dựng, nên không cuộn, không bấm, không thấy được.
 *
 * <ul>
 *   <li>Túi đồ trần 10 giấu mất viên đan dược — món DÙNG ĐƯỢC duy nhất của game (sửa 2026-09-09).
 *   <li>Cửa hàng trần 8 trong khi server bán 19 món ({@code shop_item}, sắp theo
 *       {@code item_template_id}): 11 món cuối — trong đó có chính viên đan dược đó (V36) — không
 *       MUA được bằng bất kỳ cách nào.
 * </ul>
 *
 * Gom về một chỗ để lý do chỉ được viết MỘT lần, và để lần sau nó đổi thì không còn bản chép nào
 * nói điều ngược lại.
 */
public final class ScrollingList {

    /**
     * Trần CỨNG số dòng một danh sách HUD được DỰNG — <b>không phải</b> số dòng nhìn thấy cùng lúc
     * (cái đó do chiều cao TỐI ĐA của vùng cuộn ở từng cửa sổ quyết, xem {@link #wrap}).
     *
     * <p>Vẫn giữ một trần vì một danh sách không giới hạn là một dòng dữ liệu từ server đi thẳng vào
     * số widget phải dựng — và hòm thư, bang hội, bạn bè đều KHÔNG có trần nào phía server (không
     * {@code LIMIT}, không cột sức chứa). Nhưng trần phải nằm TRÊN mọi sức chứa thật mà server có:
     * túi đồ 20 ô ({@code PostgresInventoryAdapter.DEFAULT_CAPACITY}, cũng là trần thật của một lời
     * chào giao dịch), 19 món cửa hàng, 5 nhiệm vụ — {@code ScrollingListTest} gác đúng các ràng
     * buộc đó, đọc thẳng từ migration chứ không chép số. Phần vượt trần vẫn được NÓI RA bằng một dòng
     * "còn N … nữa chưa hiện" ở cuối danh sách.
     */
    public static final int MAX_BUILT_ROWS = 60;

    private ScrollingList() {}

    /**
     * Bọc một danh sách vào vùng cuộn dọc. Chiều cao do Ô CHỨA quyết — ô đó PHẢI đặt
     * {@code maxHeight} (hoặc {@code height}), nếu không vùng cuộn nở theo nội dung và khung lại cao
     * vượt màn hình như cũ. {@code ScrollPane} có chiều cao tối thiểu 0, nên {@code maxHeight} cho
     * danh sách ngắn co lại vừa đủ, danh sách dài thì dừng ở trần và cuộn.
     *
     * <p>{@code setScrollingDisabled(true, false)} khoá hẳn trục ngang: danh sách rộng đúng bằng
     * khung nên cuộn ngang chỉ tạo ra một cú trượt nhầm — và vì trục ngang bị khoá, nội dung bị ép
     * rộng ĐÚNG bằng vùng cuộn, nên dòng bên trong phải dùng {@code growX()} chứ không đặt bề rộng cố
     * định bằng khung (thanh cuộn ăn mất {@code SCROLLBAR_WIDTH_PX} ở mép phải). {@code
     * setFadeScrollBars(false)} giữ thanh cuộn LUÔN hiện — thanh tự mờ đi làm người chơi không biết
     * dưới còn dòng.
     */
    static ScrollPane wrap(Table content, KageSkin skin) {
        ScrollPane pane = new ScrollPane(content, skin.scrollPaneStyle);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        claimScrollFocusOnHover(pane);
        return pane;
    }

    /**
     * Cho lăn chuột ăn ngay khi RÊ vào vùng cuộn, không bắt phải bấm một phát trước.
     *
     * <p>Bẫy của libGDX: {@code Stage} gửi sự kiện lăn tới {@code scrollFocus}, mà {@link ScrollPane}
     * chỉ tự giành {@code scrollFocus} trong {@code touchDown} của chính nó. Chưa bấm vào trong
     * khung thì {@code scrollFocus} còn {@code null} và mọi cú lăn rơi vào {@code root} — người chơi
     * thấy đúng cảnh "có thanh cuộn, có đồ ở dưới, lăn chuột không nhúc nhích". Bắt được lúc
     * PLAYTEST SỐNG 2026-09-05 với túi 10 món trong khi khung chỉ hiện 3 — test không thấy được vì
     * không test nào giả lập con trỏ chuột.
     *
     * <p>Trả {@code scrollFocus} về {@code null} lúc rời khung để lăn chuột ngoài cửa sổ không còn
     * âm thầm cuộn danh sách này.
     */
    // PMD ExcessiveParameterList: chu ky enter/exit do Scene2D quyet dinh, khong sua duoc.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static void claimScrollFocusOnHover(ScrollPane pane) {
        pane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && pane.getStage() != null) { // -1 = con trỏ chuột, không phải ngón chạm
                    pane.getStage().setScrollFocus(pane);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                // Xoa vo dieu kien: Scene2D ban exit cua vung cu TRUOC enter cua vung moi, nen khong
                // co nguy co xoa nham focus mot ScrollPane khac vua gianh duoc.
                if (pointer == -1 && pane.getStage() != null) {
                    pane.getStage().setScrollFocus(null);
                }
            }
        });
    }
}
