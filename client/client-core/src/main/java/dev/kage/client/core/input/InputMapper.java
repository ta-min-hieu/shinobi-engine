package dev.kage.client.core.input;

/**
 * Nguồn của sự thật về "action nào đang được kích hoạt" — implementation cụ thể (bàn phím,
 * touch joystick...) nằm ở launcher platform (CLIENT.md §5). client-core chỉ phụ thuộc interface
 * này, không biết Desktop hay Android đứng sau nó (Điều 28).
 */
public interface InputMapper {
    boolean isPressed(GameAction action);
}
