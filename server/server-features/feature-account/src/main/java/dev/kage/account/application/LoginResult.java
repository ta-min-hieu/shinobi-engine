package dev.kage.account.application;

/** Kết quả đăng nhập/tạo account — gồm session token để gọi các Command tiếp theo. */
public record LoginResult(long accountId, String username, String sessionToken) {
}
