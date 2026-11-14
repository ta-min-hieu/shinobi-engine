package dev.kage.friend.domain;

import java.util.Optional;

/** Câu chào khi vào game (ADR-425) — bảng {@code login_greeting}. */
public interface LoginGreetingPort {

    /** Câu chào đang đặt. */
    Optional<String> greeting(long characterId);

    /** Đặt câu chào; rỗng = xoá. */
    void setGreeting(long characterId, String message);
}
