-- V15 — party (GAMEPLAY.md §11 mở rộng, Phase 7 lát cắt 2). Luồng gia nhập: MỜI + CHẤP NHẬN (khác
-- guild_join_request V14 là "xin gia nhập"). Không có cột name (party không đặt tên, khác guild).
CREATE TABLE party (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leader_character_id BIGINT NOT NULL REFERENCES character (id),
    created_at TIMESTAMPTZ NOT NULL
);

-- character_id UNIQUE — 1 nhân vật chỉ ở TỐI ĐA 1 party tại 1 thời điểm, ràng buộc ở tầng DB
-- (không chỉ application/). Sức chứa tối đa MAX_MEMBERS=5 (GDD "Bí Cảnh party 2–5") KHÔNG enforce
-- được bằng constraint đơn giản (cần đếm số dòng cùng party_id) — PostgresPartyAdapter khoá dòng
-- `party` FOR UPDATE rồi đếm lại trước khi insert (xem feature-party/CLAUDE.md).
CREATE TABLE party_member (
    party_id BIGINT NOT NULL REFERENCES party (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL UNIQUE REFERENCES character (id),
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (party_id, character_id)
);

-- 1 dòng = 1 lời mời PENDING, hướng leader -> invitee. Chấp nhận/từ chối đều XOÁ dòng (không giữ
-- lịch sử, cùng quyết định friendship V12/guild_join_request V14).
CREATE TABLE party_invite (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    party_id BIGINT NOT NULL REFERENCES party (id) ON DELETE CASCADE,
    invitee_character_id BIGINT NOT NULL REFERENCES character (id),
    invited_at TIMESTAMPTZ NOT NULL,
    UNIQUE (party_id, invitee_character_id)
);

CREATE INDEX idx_party_invite_invitee ON party_invite (invitee_character_id);
