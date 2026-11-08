package dev.kage.character.api;

import dev.kage.character.domain.InspectPrivacyPort;

/** Soi trang bị (ADR-171) — công tắc riêng tư. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class InspectPrivacyFacade {

    /** Composition không có công tắc (test): ai cũng xem được. */
    public static final InspectPrivacyFacade DISABLED = new InspectPrivacyFacade(new InspectPrivacyPort() {
        @Override
        public boolean hidden(long characterId) {
            return false;
        }

        @Override
        public void setHidden(long characterId, boolean hidden) {
            // không lưu
        }

        @Override
        public String bio(long characterId) {
            return "";
        }

        @Override
        public void setBio(long characterId, String bio) {
            // không lưu
        }
    });

    /** ADR-218 — độ dài tối đa dòng giới thiệu (cột {@code character.bio}, V164). */
    public static final int MAX_BIO_LENGTH = 60;

    private final InspectPrivacyPort port;

    /** Dựng ở composition root. */
    public InspectPrivacyFacade(InspectPrivacyPort port) {
        this.port = port;
    }

    /** Người này đã tắt cho xem chưa. */
    public boolean hidden(long characterId) {
        return port.hidden(characterId);
    }

    /** Bật/tắt cho xem. */
    public void setHidden(long characterId, boolean hidden) {
        port.setHidden(characterId, hidden);
    }

    /** ADR-218 — dòng giới thiệu ("" = chưa viết). */
    public String bio(long characterId) {
        return port.bio(characterId);
    }

    /**
     * ADR-218 — ghi dòng giới thiệu (cắt khoảng trắng hai đầu; rỗng = xoá).
     *
     * @return {@code false} nếu dài quá {@value #MAX_BIO_LENGTH} ký tự hoặc chứa ký tự điều khiển (không ghi gì)
     */
    public boolean setBio(long characterId, String bio) {
        String trimmed = bio.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_BIO_LENGTH
                || trimmed.codePoints().anyMatch(java.lang.Character::isISOControl)) {
            return false;
        }
        port.setBio(characterId, trimmed);
        return true;
    }
}
