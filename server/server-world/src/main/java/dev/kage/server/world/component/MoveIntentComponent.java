package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Hướng ngang người chơi ĐANG GIỮ (-1..1, đã kẹp), ghi lúc nhận {@code MoveCommand}. Tách khỏi
 * {@code VelocityComponent} vì vận tốc là KẾT QUẢ của hướng này nhân tốc độ hiện hành — tốc độ đổi
 * theo stun/slow/buff ngay cả khi người chơi không gửi lệnh mới (client chỉ gửi khi hướng đổi), nên
 * server phải nhớ hướng để tính lại mỗi tick ({@code CharacterLocomotionSystem}). Chỉ nhân vật mang
 * component này; quái tự tính vận tốc trong {@code AiSystem}.
 */
public final class MoveIntentComponent implements Component {
    public float dirX;
}
