-- V176 — lô 50 (ADR-327, ADR-329, ADR-330).
-- ADR-327 chuỗi đăng nhập: last_day là ngày (giờ Việt Nam) lần vào world gần nhất; cách quá một ngày ⇒ chuỗi về 1.
CREATE TABLE login_streak (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    streak INT NOT NULL CHECK (streak > 0),
    best INT NOT NULL CHECK (best >= streak),
    last_day DATE NOT NULL
);

-- ADR-329 người vừa chơi cùng: mỗi nhân vật giữ 10 người gần nhất từng chung nhóm.
CREATE TABLE recent_player (
    character_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    other_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    met_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, other_id),
    CHECK (character_id <> other_id)
);

-- ADR-330 báo khi bị soi: mặc định BẬT; có dòng ở đây = đã tắt.
CREATE TABLE inspect_notice_mute (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE
);
