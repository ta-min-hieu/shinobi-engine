package dev.kage.inventory.domain;

/** Luật kho đồ tông môn (ADR-061). */
public final class StoragePolicy {

    /** Số dòng tối đa mỗi nhân vật (D2, Q1 theo khuyến nghị). */
    public static final int CAPACITY = 40;

    /** NPC giữ kho — gửi/rút chỉ khi đứng gần (D3). */
    public static final String KEEPER_NPC_CODE = "senior_sister";

    private StoragePolicy() {}
}
