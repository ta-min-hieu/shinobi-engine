-- V180 — lô 54 (ADR-361, ADR-362).
-- ADR-361 lịch sử bị soi: mỗi lần soi thành công một người KHÁC; giữ 20 lần gần nhất mỗi người bị soi.
CREATE TABLE inspect_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    viewer_id BIGINT NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    inspected_at TIMESTAMPTZ NOT NULL,
    CHECK (target_id <> viewer_id)
);
CREATE INDEX idx_inspect_log_target ON inspect_log (target_id, id DESC);

-- ADR-362 chỉ bạn bè được soi: có dòng = bật.
CREATE TABLE inspect_friends_only (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE
);
-- ADR-360 ai đã thích mình: đọc theo người được thích, mới trước.
CREATE INDEX idx_profile_like_target_day ON profile_like (target_id, liked_on DESC);
