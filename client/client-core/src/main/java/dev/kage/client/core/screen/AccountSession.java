package dev.kage.client.core.screen;

import dev.kage.client.core.net.GameConnection;

/**
 * Một kết nối đã đăng nhập xong cùng tên đăng nhập đã dùng cho nó.
 *
 * <p>Tên đi kèm để màn world tự đăng nhập lại khi đứt mạng (quyết định chủ dự án 2026-09-29). Giao
 * thức chỉ đăng nhập bằng tên (ADR-017) — không có mật khẩu, và {@code ConnectRequest.token} server
 * chưa đọc — nên đây là thứ DUY NHẤT cần giữ, và chỉ giữ trong bộ nhớ.
 *
 * @param connection kết nối đã nhận {@code ConnectResponse} thành công
 * @param username tên vừa đăng nhập thành công trên {@code connection}
 */
public record AccountSession(GameConnection connection, String username) {}
