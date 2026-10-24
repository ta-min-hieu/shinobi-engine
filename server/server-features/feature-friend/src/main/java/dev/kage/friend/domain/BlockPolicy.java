package dev.kage.friend.domain;

/** Chặn người chơi (ADR-091) — tham số ở một chỗ. */
public final class BlockPolicy {

    /** Trần số người một nhân vật chặn được — đủ cho người bị quấy rối, không để bảng phình vô hạn. */
    public static final int MAX_BLOCKED = 50;

    private BlockPolicy() {}
}
