package dev.kage.character.domain;

/**
 * Tên nhân vật đã có người dùng, phát hiện ở TẦNG DB (vi phạm UNIQUE {@code character.name}) chứ
 * không phải ở phép kiểm {@code existsByName} của use case. Hai người tạo cùng một tên ĐÚNG cùng
 * lúc đều qua được phép kiểm đó; chỉ ràng buộc DB mới chặn được người thứ hai. Adapter dịch lỗi SQL
 * sang tín hiệu này (cùng cách {@link IdempotencyKeyReusedException}), use case đổi nó thành đúng
 * lỗi "tên trùng" mà người chơi vẫn nhận ở đường thường.
 */
public final class CharacterNameTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CharacterNameTakenException(String name) {
        super("Tên nhân vật đã có người dùng: " + name);
    }
}
