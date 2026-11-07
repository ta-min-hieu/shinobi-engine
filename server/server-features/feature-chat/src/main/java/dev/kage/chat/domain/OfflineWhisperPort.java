package dev.kage.chat.domain;

import java.time.Instant;
import java.util.List;

/** Nhắn riêng khi offline (ADR-187) — hộp tin chờ của từng người nhận. Chạm DB. */
public interface OfflineWhisperPort {

    /** Một tin chờ giao; {@code senderName} là tên HIỆN TẠI của người gửi. */
    record Pending(long senderId, String senderName, String body, Instant sentAt) {}

    /** Ghi một tin nếu hộp của {@code recipientId} còn dưới {@code maxPending} tin; trả {@code false} nếu đầy. */
    boolean store(long senderId, long recipientId, String body, Instant at, int maxPending);

    /** Lấy RA (và xoá) mọi tin gửi từ {@code since} trở đi, cũ trước; tin cũ hơn bị bỏ. */
    List<Pending> takeAll(long recipientId, Instant since);
}
