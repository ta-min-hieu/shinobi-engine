package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.FormationChangedEvent;
import dev.kage.protocol.FormationTier;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.EquipmentBonusSnapshot;
import dev.kage.server.world.WorldFacade;

/**
 * Trận pháp nhóm (ADR-128) — nhịp {@value #TICK_SECONDS} giây trong {@link ExpansionJobs}: với mỗi
 * nhóm có người online, đọc danh sách thành viên MỘT lần, rồi mỗi người tự tính trận của mình.
 * Ở world chính: thành viên cùng kênh trong {@link FormationRules#RADIUS_METERS} mét (vị trí lấy
 * từ bộ nhớ registry). Ở phó bản/đấu trường vị trí registry đã đóng băng (toạ độ world chính),
 * nên cả nhóm cùng một instance tính là cùng trận — instance vốn nhỏ.
 *
 * <p>Trận chỉ ở RAM. Chỉ khi trận của một người ĐỔI mới gửi lại chỉ số vào World và báo client (1327).
 */
final class FormationHooks {

    private static final long TICK_SECONDS = 3L;
    /** Linh căn đổi hiếm (ADR-066) — đọc lại sau chừng này nhịp thay vì mỗi nhịp một lượt DB mỗi người. */
    private static final int FACTION_CACHE_TICKS = 20;

    /** Thứ việc này cần. */
    record Deps(
            PartyFacade parties,
            CharacterFacade characters,
            WorldRouteLookup routes,
            LongFunction<Optional<WorldFacade>> mainWorldOf,
            ConnectedCharacterRegistry registry) {}

    private final Map<Long, FormationRules.Formation> active = new ConcurrentHashMap<>();
    private final Map<Long, Faction> factions = new ConcurrentHashMap<>();
    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();
    private int ticks;

    /** Nối bản thật — một lần, lúc khởi động. */
    void bind(Deps realDeps) {
        this.deps = Optional.of(realDeps);
    }

    /** Nhịp tính trận cho {@link ExpansionJobs}. */
    ExpansionJobs.Job job() {
        return new ExpansionJobs.Job("trận pháp", TICK_SECONDS, () -> deps.ifPresent(this::tick));
    }

    /** {@code bonus} cộng thưởng trận đang đứng — mắt xích cuối của chuỗi chỉ số ngoài trang bị. */
    EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, long characterId) {
        return FormationRules.plus(bonus, Optional.ofNullable(active.get(characterId)));
    }

    private void tick(Deps d) {
        ticks++;
        if (ticks % FACTION_CACHE_TICKS == 0) {
            factions.clear();
        }
        Map<Long, PositionSnapshot> positions = d.registry().snapshotPositions();
        Map<Long, FormationRules.Formation> next = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        for (long characterId : positions.keySet()) {
            if (visited.add(characterId)) {
                List<Long> members = d.parties().listMembers(characterId).stream()
                        .map(PartyFacade.MemberView::characterId)
                        .toList();
                visited.addAll(members);
                formParty(d, members, positions, next);
            }
        }
        Set<Long> touched = new HashSet<>(active.keySet());
        touched.addAll(next.keySet());
        for (long characterId : touched) {
            Optional<FormationRules.Formation> now = Optional.ofNullable(next.get(characterId));
            if (!now.equals(Optional.ofNullable(active.get(characterId)))) {
                changed(d, characterId, now);
            }
        }
    }

    private void formParty(
            Deps d,
            List<Long> members,
            Map<Long, PositionSnapshot> positions,
            Map<Long, FormationRules.Formation> into) {
        List<Placed> online = new ArrayList<>();
        for (long member : members) {
            Optional<WorldFacade> world = d.routes().find(member);
            Optional<Faction> faction = factionOf(d, member);
            if (world.isPresent() && faction.isPresent() && positions.containsKey(member)) {
                boolean main = d.mainWorldOf().apply(member).isPresent();
                online.add(new Placed(member, world.get(), main, positions.get(member), faction.get()));
            }
        }
        for (Placed self : online) {
            Set<Faction> present = EnumSet.noneOf(Faction.class);
            int count = 0;
            for (Placed other : online) {
                if (self.standsWith(other)) {
                    present.add(other.faction());
                    count++;
                }
            }
            FormationRules.of(count, present).ifPresent(formation -> into.put(self.characterId(), formation));
        }
    }

    private Optional<Faction> factionOf(Deps d, long characterId) {
        Faction cached = factions.get(characterId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Faction> loaded = d.characters().findById(characterId).map(Character::faction);
        loaded.ifPresent(faction -> factions.put(characterId, faction));
        return loaded;
    }

    private void changed(Deps d, long characterId, Optional<FormationRules.Formation> now) {
        if (now.isPresent()) {
            active.put(characterId, now.get());
        } else {
            active.remove(characterId);
        }
        d.routes()
                .find(characterId)
                .ifPresent(world ->
                        world.applyEquipmentBonus(characterId, d.registry().equipmentBonusOf(characterId)));
        d.registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_FORMATION_CHANGED_EVENT_VALUE,
                        0,
                        event(now).toByteArray());
    }

    static FormationChangedEvent event(Optional<FormationRules.Formation> formation) {
        FormationChangedEvent.Builder event = FormationChangedEvent.newBuilder();
        formation.ifPresent(f -> {
            event.setMembers(f.members())
                    .setTier(
                            switch (f.tier()) {
                                case FIVE_ELEMENTS -> FormationTier.FORMATION_TIER_FIVE_ELEMENTS;
                                case THREE_TALENTS -> FormationTier.FORMATION_TIER_THREE_TALENTS;
                                case PLAIN -> FormationTier.FORMATION_TIER_PLAIN;
                            })
                    .setBonusAtk(f.bonus().atk())
                    .setBonusDef(f.bonus().def())
                    .setBonusRes(f.bonus().res())
                    .setBonusHp(f.bonus().hp());
            for (Faction faction : Faction.values()) {
                if (f.factions().contains(faction)) {
                    event.addFactions(toProto(faction));
                }
            }
        });
        return event.build();
    }

    private static dev.kage.protocol.Faction toProto(Faction faction) {
        return switch (faction) {
            case HOA -> dev.kage.protocol.Faction.FACTION_HOA;
            case THUY -> dev.kage.protocol.Faction.FACTION_THUY;
            case PHONG -> dev.kage.protocol.Faction.FACTION_PHONG;
            case LOI -> dev.kage.protocol.Faction.FACTION_LOI;
            case THO -> dev.kage.protocol.Faction.FACTION_THO;
        };
    }

    /** Một thành viên đang ở trong một World. */
    private record Placed(
            long characterId, WorldFacade world, boolean mainWorld, PositionSnapshot at, Faction faction) {

        boolean standsWith(Placed other) {
            if (!world.equals(other.world())) {
                return false;
            }
            if (!mainWorld) {
                return true;
            }
            float dx = at.x() - other.at().x();
            float dy = at.y() - other.at().y();
            return dx * dx + dy * dy <= FormationRules.RADIUS_METERS * FormationRules.RADIUS_METERS;
        }
    }
}
