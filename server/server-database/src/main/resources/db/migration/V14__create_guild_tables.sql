-- V14 — bang hội (GAMEPLAY.md §11, Phase 7 lát cắt 1). Luồng gia nhập: XIN GIA NHẬP (theo tên
-- bang) -> leader DUYỆT/từ chối (khác friendship V12 là mời trực tiếp). Kho bang + nhiệm vụ bang
-- KHÔNG có ở đây (dời lát cắt sau, xem feature-guild/CLAUDE.md).
CREATE TABLE guild (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    leader_character_id BIGINT NOT NULL REFERENCES character (id),
    created_at TIMESTAMPTZ NOT NULL
);

-- character_id UNIQUE — 1 nhân vật chỉ ở TỐI ĐA 1 bang tại 1 thời điểm, ràng buộc ở tầng DB
-- (không chỉ application/).
CREATE TABLE guild_member (
    guild_id BIGINT NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL UNIQUE REFERENCES character (id),
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (guild_id, character_id)
);

-- 1 dòng = 1 đơn xin gia nhập PENDING. Duyệt/từ chối đều XOÁ dòng (không giữ lịch sử, cùng quyết
-- định "reject = xoá dòng" đã dùng ở friendship V12) — duyệt thì INSERT guild_member rồi DELETE
-- dòng này trong CÙNG transaction (PostgresGuildAdapter#approveJoinRequest).
CREATE TABLE guild_join_request (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guild_id BIGINT NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character (id),
    requested_at TIMESTAMPTZ NOT NULL,
    UNIQUE (guild_id, character_id)
);

CREATE INDEX idx_guild_join_request_guild ON guild_join_request (guild_id);
