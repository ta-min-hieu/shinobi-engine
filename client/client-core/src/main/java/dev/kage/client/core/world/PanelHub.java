package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Disposable;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.HudWindow;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Sổ đăng ký MỘT chỗ cho mọi khung HUD của {@link WorldScreen}: vẽ, đổi kích thước, trả tài nguyên,
 * xâu input, định tuyến gói, và trả lời câu hỏi "có cửa sổ nào đang chiếm bàn phím không".
 *
 * <p><b>Vì sao tồn tại.</b> Trước lớp này, mỗi tính năng có cửa sổ riêng phải được nhắc tên ở CHÍN
 * chỗ rời rạc trong {@link WorldScreen}: khai báo field, dựng coordinator, {@code hide()},
 * {@code resize()}, {@code render()}, {@code dispose()}, {@code dispatch()}, chuỗi
 * {@code isTextInputActive()}, và {@code InputMultiplexer}. Chín danh sách song song là chín cơ hội
 * quên đúng một cái, và mỗi lần quên là một lỗi khác nhau (rò texture, khung vẽ lệch sau lần kéo cửa
 * sổ đầu tiên, phím gõ trong ô chat lại mở cửa sổ khác). Nó cũng là thứ đã đẩy {@link WorldScreen}
 * chạm trần {@code MethodLength} của Checkstyle bốn lần trong hai ngày và tổng
 * {@code CyclomaticComplexity} của lớp lên ĐÚNG ngưỡng 80 của PMD — thêm một cửa sổ nữa là kẹt cứng.
 * Đăng ký ở đây, cửa sổ thứ mười trở đi không tốn thêm dòng nào ở {@link WorldScreen}.
 *
 * <p><b>Thứ tự đăng ký là thứ tự nuốt input</b>, từ trên xuống: khung đăng ký TRƯỚC nhận sự kiện
 * trước. Từ đó suy ra hai thứ tự còn lại, không cần khai báo thêm:
 *
 * <ul>
 *   <li>{@link #render} chạy NGƯỢC lại — khung nuốt input trước là khung nằm TRÊN CÙNG, mà vẽ sau
 *       thì nằm trên. Hai câu hỏi "ai nhận click" và "ai che ai" luôn phải cùng một đáp án.
 *   <li>{@link #dispose} chạy XUÔI — panel mượn skin của {@code CombatHud}, nên chúng phải trả tài
 *       nguyên trước chủ skin. {@code CombatHud} đăng ký cuối cùng chính vì vậy.
 * </ul>
 *
 * <p>Lớp này KHÔNG giữ tham chiếu có kiểu tới từng panel: {@link WorldScreen} vẫn giữ field riêng
 * cho những lời gọi mang tính đặc thù ({@code chat.showSystemNotice(...)}). Ở đây chỉ có phần vòng
 * đời giống nhau — xem {@link HudWindow} và {@link WindowFlow}.
 */
final class PanelHub implements Disposable {

    /** Theo thứ tự đăng ký = thứ tự nuốt input. */
    private final List<HudWindow> windows = new ArrayList<>();

    /** Theo thứ tự đăng ký; {@link #dispatch} hỏi TẤT CẢ nên thứ tự không đổi kết quả. */
    private final List<WindowFlow> flows = new ArrayList<>();

    /**
     * Một tính năng đầy đủ: cửa sổ để nhìn + luồng để nói chuyện với server.
     *
     * @param window panel Scene2D của tính năng
     * @param flow coordinator của tính năng
     */
    void register(HudWindow window, WindowFlow flow) {
        windows.add(window);
        flows.add(flow);
    }

    /**
     * Luồng KHÔNG có cửa sổ riêng — vẽ nhờ vào panel của luồng khác ({@code DungeonCoordinator} dùng
     * nút trong khung Nhóm) hoặc chỉ vào HUD ({@code QuestCoordinator}).
     */
    void register(WindowFlow flow) {
        flows.add(flow);
    }

    /** Cửa sổ KHÔNG có luồng gói riêng — HUD combat, gói của nó do {@link WorldScreen} tự đọc. */
    void register(HudWindow window) {
        windows.add(window);
    }

    /** Các {@code Stage} theo đúng thứ tự nuốt input, để nhét vào {@code InputMultiplexer}. */
    InputProcessor[] stages() {
        InputProcessor[] processors = new InputProcessor[windows.size()];
        for (int i = 0; i < windows.size(); i++) {
            processors[i] = windows.get(i).stage();
        }
        return processors;
    }

    void resize(int width, int height) {
        for (HudWindow window : windows) {
            window.resize(width, height);
        }
    }

    /**
     * ADR-308 — thu phóng mọi cửa sổ: viewport màn hình của từng cửa sổ đổi số đơn vị trên một pixel ({@code 1/scale}),
     * rồi dựng lại khung theo kích thước cửa sổ hiện tại.
     */
    void setUiScale(float scale, int width, int height) {
        for (HudWindow window : windows) {
            if (window.stage().getViewport() instanceof com.badlogic.gdx.utils.viewport.ScreenViewport viewport) {
                viewport.setUnitsPerPixel(1f / scale);
            }
        }
        resize(width, height);
    }

    /** Vẽ NGƯỢC thứ tự đăng ký — xem javadoc của lớp về lý do. */
    void render(float deltaTime) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            windows.get(i).render(deltaTime);
        }
    }

    /**
     * Đưa gói cho MỌI luồng, không dừng ở luồng đầu tiên nhận — xem {@link WindowFlow#dispatch}.
     *
     * @return {@code true} nếu có ít nhất một luồng nhận gói này
     */
    boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        boolean handled = false;
        for (WindowFlow flow : flows) {
            handled |= flow.dispatch(frame);
        }
        return handled;
    }

    /**
     * Có cửa sổ nào đang nhận chữ gõ vào, hoặc đang chờ người chơi đọc/bấm hay không.
     *
     * <p><b>Nguồn chân lý DUY NHẤT</b> cho hai câu hỏi vốn là một: "phím vừa bấm là ý định gameplay
     * hay là một ký tự?" và "phím vừa bấm có được phép mở thêm một cửa sổ nữa không?".
     *
     * <p>Phải tập trung ở ĐÂY vì mỗi khung có {@code Stage} riêng, và <b>một {@code Stage} không
     * nhìn thấy keyboard focus của {@code Stage} khác</b>. Hệ quả là một bug có thật, tìm ra lúc gộp
     * ba luồng làm song song: {@code InputMultiplexer} chuyển {@code keyUp} xuống tầng dưới cho tới
     * khi có ai trả {@code true}, mà mỗi panel chỉ nhận ra phím mở của CHÍNH nó — nên gõ chữ "p"
     * trong ô chat sẽ bật cửa sổ Nhóm, và gõ chữ "t" trong ô tên bạn bè sẽ bật ô chat. Panel này
     * không cần biết panel kia tồn tại; tất cả chỉ hỏi cùng một câu ở cùng một chỗ.
     *
     * <p>Khung hội thoại NPC trả {@code true} dù không có ô nhập nào: đang đọc lời thoại thì phím
     * cũng không phải ý định gameplay, và mở đè một cửa sổ khác lên là hỏng.
     */
    boolean isAnyOpen() {
        for (WindowFlow flow : flows) {
            if (flow.isOpen()) {
                return true;
            }
        }
        return false;
    }

    /** Ngược lại của {@link #isAnyOpen()} — cổng {@code setCanOpen} của các panel hỏi câu này. */
    boolean noneOpen() {
        return !isAnyOpen();
    }

    /** Đóng mọi cửa sổ — hai khung nhập liệu cùng mở là một cái bẫy phím, xem {@code WorldScreen}. */
    void closeAll() {
        for (WindowFlow flow : flows) {
            flow.close();
        }
    }

    /** Dọn sạch mọi luồng khi rời màn hình, xem {@link WindowFlow#reset()}. */
    void resetAll() {
        for (WindowFlow flow : flows) {
            flow.reset();
        }
    }

    @Override
    public void dispose() {
        for (HudWindow window : windows) {
            window.dispose();
        }
    }
}
