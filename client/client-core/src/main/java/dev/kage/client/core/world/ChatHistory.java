package dev.kage.client.core.world;

import java.util.List;

/**
 * Nhật ký chat mang được từ màn world cũ sang màn world mới khi TỰ đăng nhập lại sau chớp mạng
 * (README dòng 40(v), chủ dự án duyệt 2026-10-17). Trước đó mỗi lần tự vào lại dựng một màn mới với
 * nhật ký trắng — người chơi mất câu đang đọc dở chỉ vì mạng chập một nhịp.
 *
 * <p>Kiểu mờ: ngoài gói {@code world} chỉ cầm và chuyền nó đi, không đọc được bên trong. Chỉ mang dòng
 * nhật ký — kênh đang chọn và trạng thái nhóm về mặc định như mọi lần vào world, vì server dựng lại
 * cả hai cho phiên mới.
 */
public final class ChatHistory {

    /** Không có gì để mang — lần vào world đầu tiên. */
    public static final ChatHistory EMPTY = new ChatHistory(List.of());

    private final List<ChatController.Line> lines;

    ChatHistory(List<ChatController.Line> lines) {
        this.lines = List.copyOf(lines);
    }

    List<ChatController.Line> lines() {
        return lines;
    }
}
