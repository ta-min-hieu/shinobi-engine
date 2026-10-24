-- ADR-084 — tặng quà bạn bè. Thân mật là MỘT con số cho cả cặp (dòng friendship là một dòng mỗi cặp).
ALTER TABLE friendship ADD COLUMN intimacy INT NOT NULL DEFAULT 0 CHECK (intimacy >= 0);

-- Một món quà mỗi người tặng mỗi người nhận mỗi ngày (giờ Việt Nam) — PK chặn tặng lần hai cả khi hai lệnh song song.
CREATE TABLE friend_gift_log (
    sender_id    BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    gift_date    DATE   NOT NULL,
    PRIMARY KEY (sender_id, recipient_id, gift_date)
);
