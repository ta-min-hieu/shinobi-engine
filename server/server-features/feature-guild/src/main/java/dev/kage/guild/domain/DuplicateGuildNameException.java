package dev.kage.guild.domain;

/** Ném khi adapter phát hiện vi phạm UNIQUE {@code guild.name} — race tạo 2 bang trùng tên. */
public final class DuplicateGuildNameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateGuildNameException(String name) {
        super("Tên bang đã tồn tại: " + name);
    }
}
