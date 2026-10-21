package dev.kage.server.world;

import java.util.List;

import dev.kage.engine.ecs.Component;
import dev.kage.engine.ecs.ComponentTypes;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.TriggerZoneMembershipComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.AttackCooldownComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.CharacterLevelComponent;
import dev.kage.server.world.component.ChiComponent;
import dev.kage.server.world.component.CombatRecencyComponent;
import dev.kage.server.world.component.CombatStatsComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MonsterComponent;
import dev.kage.server.world.component.MonsterIdComponent;
import dev.kage.server.world.component.MoveIntentComponent;
import dev.kage.server.world.component.NpcComponent;
import dev.kage.server.world.component.SkillCooldownComponent;
import dev.kage.server.world.component.SkillRankComponent;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * MỌI kiểu Component mà một World server dùng — đăng ký vào sổ tĩnh của Ashley đúng một lần, lúc lớp
 * {@link WorldFacade} được nạp (trước World đầu tiên), xem {@link ComponentTypes} về lý do (dòng 73(d)).
 *
 * <p>⚠️ Thêm Component mới thì thêm vào đây — {@code WorldComponentTypesTest} đọc thư mục
 * {@code component/} của server-world và engine-ecs rồi đỏ nếu thiếu.
 */
final class WorldComponentTypes {

    static final List<Class<? extends Component>> ALL = List.of(
            PositionComponent.class,
            VelocityComponent.class,
            TriggerZoneMembershipComponent.class,
            AiParamsComponent.class,
            AiStateComponent.class,
            AttackCooldownComponent.class,
            BossAiComponent.class,
            CharacterIdComponent.class,
            CharacterLevelComponent.class,
            ChiComponent.class,
            CombatRecencyComponent.class,
            CombatStatsComponent.class,
            HealthComponent.class,
            MonsterComponent.class,
            MonsterIdComponent.class,
            MoveIntentComponent.class,
            NpcComponent.class,
            SkillCooldownComponent.class,
            SkillRankComponent.class,
            StatusEffectComponent.class,
            TargetComponent.class);

    private WorldComponentTypes() {}

    // rawtypes/unchecked: Java không có mảng generic — mảng Class trần chỉ để chuyển List sang varargs.
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerAll() {
        ComponentTypes.register(ALL.toArray(new Class[0]));
    }
}
