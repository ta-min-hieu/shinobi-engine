package dev.kage.inventory.domain;

/**
 * Ảnh chụp túi đồ + trang bị của một nhân vật tại MỘT thời điểm (ADR-035 Decision-1).
 *
 * <p>Hai aggregate đi CÙNG nhau chứ không tách hai lời gọi: chúng luôn được mở cùng lúc trên một
 * màn hình, và tách ra thì có cửa sổ thời gian mà client thấy một item vừa nằm trong túi vừa đang
 * mặc (đọc túi ở thời điểm A, đọc trang bị ở thời điểm B).
 */
public record InventorySnapshot(Inventory inventory, Equipment equipment) {}
