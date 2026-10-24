-- Lô 21 phần 1 (2026-10-24): chặn người chơi (ADR-091), sư đồ (ADR-093), đạo lữ (ADR-094). Biểu cảm (ADR-092) không
-- ghi DB.

-- ADR-091 — một dòng mỗi lượt chặn MỘT CHIỀU. Luật "bị chặn" thi hành theo cả hai chiều ở gateway.
CREATE TABLE character_block (
    blocker_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    blocked_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_character_block_blocked ON character_block (blocked_id);

-- ADR-093 — khoá chính là ĐỒ ĐỆ: mỗi đồ đệ tối đa một sư phụ. Trần 3 đồ đệ/sư phụ giữ bằng khoá dòng sư phụ.
CREATE TABLE mentorship (
    disciple_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    mentor_id   BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (disciple_id <> mentor_id)
);

CREATE INDEX idx_mentorship_mentor ON mentorship (mentor_id);

-- Mỗi mốc cấp của một đồ đệ thưởng MỘT lần trong đời nhân vật — đổi sư phụ hay chuyển thế không nhặt lại được.
CREATE TABLE mentorship_milestone (
    disciple_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    milestone   INT         NOT NULL CHECK (milestone > 0),
    mentor_id   BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    rewarded    BOOLEAN     NOT NULL,
    reached_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (disciple_id, milestone)
);

-- ADR-094 — HAI dòng mỗi cặp (mỗi chiều một dòng): khoá chính là người ⇒ mỗi người tối đa một đạo lữ.
CREATE TABLE dao_lu (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    partner_id   BIGINT      NOT NULL UNIQUE REFERENCES character (id) ON DELETE CASCADE,
    bound_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (character_id <> partner_id)
);
