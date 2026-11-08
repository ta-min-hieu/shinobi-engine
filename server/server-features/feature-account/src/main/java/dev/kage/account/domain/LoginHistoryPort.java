package dev.kage.account.domain;

import java.time.Instant;
import java.util.List;

/** Nhật ký đăng nhập theo tài khoản (ADR-238). IP đã che bớt TRƯỚC khi tới đây — không lưu IP đầy đủ. */
public interface LoginHistoryPort {

    /** Một lần đăng nhập. */
    record Entry(String maskedIp, Instant at) {}

    /** Ghi một lần đăng nhập và cắt bớt chỉ giữ {@code keep} dòng mới nhất của tài khoản. */
    void record(long accountId, String maskedIp, Instant at, int keep);

    /** {@code limit} lần mới nhất, mới trước. */
    List<Entry> recent(long accountId, int limit);
}
