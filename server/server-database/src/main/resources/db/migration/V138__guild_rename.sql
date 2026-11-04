-- ADR-172 (2026-11-04) — Đổi tên bang hội. Lúc đổi tên gần nhất — hồi chiêu 30 ngày (code: GuildRenameRules). Bang cũ
-- NULL = chưa đổi lần nào. Tên mới chịu đúng luật tên bang (GuildName) và chỉ mục duy nhất không phân biệt hoa thường.
ALTER TABLE guild ADD COLUMN renamed_at TIMESTAMPTZ;
