-- ADR-179 (2026-11-06) — Thông báo bang. Một dòng bang chủ ghi, thành viên thấy khi vào game / mở bảng bang.
ALTER TABLE guild ADD COLUMN notice VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE guild ADD COLUMN notice_updated_at TIMESTAMPTZ;
