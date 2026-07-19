package dev.kage.character.application;

/** Tên nhân vật đã tồn tại — UNIQUE toàn server, không chỉ trong 1 account (DATABASE.md §2.1). */
public final class DuplicateCharacterNameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateCharacterNameException(String name) {
        super("tên nhân vật đã tồn tại: " + name);
    }
}
