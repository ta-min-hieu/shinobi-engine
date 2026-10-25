package dev.kage.gm.domain;

import java.time.Instant;
import java.util.Optional;

/** Thông báo server một dòng (ADR-103) — hiện thực ở server-database (V89). */
public interface ServerNoticePort {

    /** Độ dài tối đa — khớp cột {@code server_notice.body}. */
    int MAX_LENGTH = 300;

    /** Không bao giờ có thông báo (test). */
    ServerNoticePort NONE = new ServerNoticePort() {
        @Override
        public Optional<String> current() {
            return Optional.empty();
        }

        @Override
        public void set(String body, long updatedBy, Instant at) {
            // Không lưu gì.
        }

        @Override
        public void clear() {
            // Không có gì để xoá.
        }
    };

    /** Thông báo đang đặt; rỗng nếu không có. */
    Optional<String> current();

    /** Đặt (thay) thông báo. */
    void set(String body, long updatedBy, Instant at);

    /** Xoá thông báo. */
    void clear();
}
