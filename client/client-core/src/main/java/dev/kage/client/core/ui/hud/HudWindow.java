package dev.kage.client.core.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;

/**
 * Phần vòng đời GIỐNG NHAU của mọi khung HUD: có {@code Stage} riêng, đổi kích thước theo cửa sổ, vẽ
 * mỗi khung hình, và trả tài nguyên khi rời màn hình.
 *
 * <p>Tồn tại để {@code WorldScreen} không phải liệt kê từng panel bằng tên ở bốn chỗ khác nhau
 * ({@code resize}/{@code render}/{@code dispose}/{@code InputMultiplexer}). Bốn danh sách song song
 * đó là thứ đã đẩy lớp kia chạm trần {@code MethodLength} của Checkstyle bốn lần trong hai ngày, và
 * mỗi lần thêm một khung là một cơ hội quên đúng một danh sách — quên {@code dispose} thì rò
 * texture, quên {@code resize} thì khung mới vẽ lệch sau lần đầu kéo cửa sổ.
 *
 * <p>Giao diện này KHÔNG mô tả việc đóng/mở: có khung không đóng mở được (nhật ký chat luôn hiện,
 * chỉ ô nhập mới đóng mở). Đó là việc của {@code WindowFlow} phía {@code world}.
 */
public interface HudWindow extends Disposable {

    /** {@code Stage} của riêng khung này — {@code WorldScreen} xâu vào {@code InputMultiplexer}. */
    Stage stage();

    /**
     * Cập nhật viewport của khung theo kích thước cửa sổ mới.
     *
     * @param width chiều rộng cửa sổ tính bằng pixel
     * @param height chiều cao cửa sổ tính bằng pixel
     */
    void resize(int width, int height);

    /**
     * Vẽ khung này của một khung hình.
     *
     * @param deltaTime giây trôi qua kể từ khung hình trước
     */
    void render(float deltaTime);
}
