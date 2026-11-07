package dev.kage.inventory.api;

import java.util.Optional;

import dev.kage.inventory.domain.PetNamePort;

/** Đặt tên linh thú (ADR-193). Luật tên ở đây; lọc từ cấm ở gateway (bộ lọc thuộc feature-chat). Chạm DB. */
public final class PetNameFacade {

    /** Độ dài tối thiểu (ký tự) của tên riêng. */
    public static final int MIN_LENGTH = 2;

    /** Độ dài tối đa (ký tự) — vừa một dòng ngắn trên bảng nhân vật. */
    public static final int MAX_LENGTH = 12;

    /** Composition không có tính năng này (test). */
    public static final PetNameFacade DISABLED = new PetNameFacade(new PetNamePort() {
        @Override
        public boolean renameEquipped(long characterId, String name) {
            return false;
        }

        @Override
        public Optional<String> equippedName(long characterId) {
            return Optional.empty();
        }
    });

    private final PetNamePort port;

    /** Dựng ở composition root. */
    public PetNameFacade(PetNamePort port) {
        this.port = port;
    }

    /** Đặt (hoặc xoá, khi rỗng) tên linh thú đang mặc; {@code name} đã được strip. */
    public Outcome rename(long characterId, String name) {
        if (!name.isEmpty() && !isValid(name)) {
            return Outcome.INVALID_NAME;
        }
        if (!port.renameEquipped(characterId, name)) {
            return Outcome.NO_PET;
        }
        return name.isEmpty() ? Outcome.CLEARED : Outcome.OK;
    }

    /** Tên riêng của linh thú đang mặc. */
    public Optional<String> equippedName(long characterId) {
        return port.equippedName(characterId);
    }

    /** Chữ cái / chữ số / khoảng trắng đơn, {@link #MIN_LENGTH}..{@link #MAX_LENGTH} ký tự. */
    static boolean isValid(String name) {
        int length = name.codePointCount(0, name.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || name.contains("  ")) {
            return false;
        }
        return name.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ');
    }

    /** Kết quả đặt tên. */
    public enum Outcome {
        OK,
        CLEARED,
        NO_PET,
        INVALID_NAME
    }
}
