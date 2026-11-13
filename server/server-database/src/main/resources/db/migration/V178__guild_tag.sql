-- V178 — lô 52 (ADR-344). Ký hiệu bang 2–4 ký tự, không trùng (không phân biệt hoa thường); NULL = chưa đặt.
ALTER TABLE guild ADD COLUMN tag VARCHAR(4);
CREATE UNIQUE INDEX uq_guild_tag ON guild (lower(tag)) WHERE tag IS NOT NULL;
-- ADR-343 tra tên cũ theo tên.
CREATE INDEX idx_character_name_history_old ON character_name_history (lower(old_name));
