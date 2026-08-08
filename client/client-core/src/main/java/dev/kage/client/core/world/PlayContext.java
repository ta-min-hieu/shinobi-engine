package dev.kage.client.core.world;

import dev.kage.client.core.input.InputMapper;
import dev.kage.engine.ecs.EcsWorld;

/**
 * Gộp 2 phụ thuộc luôn đi cùng nhau qua toàn bộ chuỗi màn hình
 * {@code LoginScreen} → {@code CharacterSelectScreen} → {@link WorldScreen} — {@link EcsWorld} (nơi
 * entity sống) và {@link InputMapper} (nguồn ý định input platform-specific, CLIENT.md §5). Tồn tại
 * THUẦN để tránh lặp lại đúng 2 tham số này ở mọi constructor màn hình (CODING-STANDARDS
 * ParameterNumber max=4) — vẫn truyền tường minh qua constructor từng màn hình, KHÔNG phải Service
 * Locator (ENGINE.md §6: {@code ServiceRegistry.get()} chỉ được gọi ở composition root).
 */
public record PlayContext(EcsWorld world, InputMapper inputMapper) {}
