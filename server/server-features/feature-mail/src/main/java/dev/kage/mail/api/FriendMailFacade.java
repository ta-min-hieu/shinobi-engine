package dev.kage.mail.api;

import java.time.Instant;

import dev.kage.mail.domain.FriendMailQuotaPort;

/** API giới hạn thư gửi mọi bạn bè (ADR-312): {@value #DAILY_LIMIT} đợt mỗi người mỗi ngày. */
public final class FriendMailFacade {

    /** Số đợt mỗi ngày. */
    public static final int DAILY_LIMIT = 1;

    /** Không giới hạn, không ghi (composition test). */
    public static final FriendMailFacade DISABLED = new FriendMailFacade(new FriendMailQuotaPort() {
        @Override
        public int sentSince(long senderId, Instant since) {
            return 0;
        }

        @Override
        public void record(long senderId, int recipients, Instant at) {
            // không lưu gì
        }
    });

    private final FriendMailQuotaPort port;

    /** Dựng ở composition root. */
    public FriendMailFacade(FriendMailQuotaPort port) {
        this.port = port;
    }

    /** Số đợt còn được gửi kể từ đầu ngày {@code dayStart}. */
    public int remaining(long senderId, Instant dayStart) {
        return Math.max(0, DAILY_LIMIT - port.sentSince(senderId, dayStart));
    }

    /** Ghi một đợt đã gửi. */
    public void record(long senderId, int recipients, Instant at) {
        port.record(senderId, recipients, at);
    }
}
