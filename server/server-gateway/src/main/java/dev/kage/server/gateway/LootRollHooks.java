package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.LootRollChoice;
import dev.kage.protocol.LootRollChoiceCommand;
import dev.kage.protocol.LootRollChoiceOutcome;
import dev.kage.protocol.LootRollChoiceResponse;
import dev.kage.protocol.LootRollEntry;
import dev.kage.protocol.LootRollResultEvent;
import dev.kage.protocol.LootRollStartedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.WorldFacade;

/**
 * Tung xúc xắc chia đồ hiếm trong nhóm (ADR-150). Món TRANG BỊ rơi khi người ra đòn cuối có đồng đội online cùng World
 * không vào túi ai ngay: mỗi món mở một lượt tung {@value #ROLL_SECONDS} giây trong RAM, mỗi người chọn Cần / Tham /
 * Bỏ, server tung 1–100 — Cần thắng mọi Tham, cao nhất thắng; cả nhóm Bỏ ⇒ về người ra đòn cuối. Người thắng nhận qua
 * đúng đường đồ rơi (audit {@code LOOT_GRANT}); túi đầy ⇒ thư hệ thống.
 *
 * <p>Mất khi khởi động lại = món đang tung mất — cùng chấp nhận như mọi trạng thái RAM khác của gateway (đồ rơi khi
 * túi đầy cũng mất). Nối sau khi DB đã dựng, cùng khuôn {@link EncounterHooks}.
 */
final class LootRollHooks {

    private static final Logger LOG = LoggerFactory.getLogger(LootRollHooks.class);

    /** Thời gian chọn của một lượt tung. */
    static final int ROLL_SECONDS = 15;

    private static final int DIE_FACES = 100;
    private static final String MAIL_BODY =
            "Túi đầy — chiến lợi phẩm gửi kèm thư này. / Your bag was full — your loot is attached.";
    private static final int TIE_BREAK_SPACE = 1_000;

    // Việc chốt lượt tung (cấp đồ chạm DB) chạy trên Virtual Thread; bộ đếm là luồng hẹn giờ dùng chung của JDK.
    private static final Executor RESOLVES = Executors.newVirtualThreadPerTaskExecutor();

    /** Thứ việc này cần. */
    record Deps(
            ConnectedCharacterRegistry registry,
            InventoryFacade inventory,
            CharacterFacade characters,
            LongFunction<List<Long>> partyMembers,
            LongFunction<Optional<WorldFacade>> worldOf,
            PlayerNotices notices) {}

    /** Thứ mở một lượt tung: ai hạ, bảng rơi nào, món gì, ai được chia. */
    private record Opening(long killerId, String dropTableCode, ItemStack stack, List<Long> participants) {}

    /** Một lượt tung đang mở. {@code choices} giữ thứ tự người tham gia. */
    private static final class Roll {
        final long id;
        final long killerId;
        final String dropTableCode;
        final ItemStack stack;
        final Map<Long, LootRollChoice> choices = new LinkedHashMap<>();
        boolean closed;

        Roll(long id, Opening opening) {
            this.id = id;
            this.killerId = opening.killerId();
            this.dropTableCode = opening.dropTableCode();
            this.stack = opening.stack();
            opening.participants().forEach(member -> choices.put(member, LootRollChoice.LOOT_ROLL_CHOICE_UNSPECIFIED));
        }
    }

    private final Map<Long, Roll> rolls = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();
    private final SplittableRandom random = new SplittableRandom();
    private volatile Set<Long> equipmentTemplates = Set.of();

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau.
    private volatile Optional<Deps> deps = Optional.empty();

    /** Nối bản thật — một lần, lúc khởi động. Danh mục vật phẩm cố định lúc chạy nên chỉ đọc một lần. */
    void bind(Deps realDeps) {
        Set<Long> equipment = new HashSet<>();
        for (ItemCatalogEntry entry : realDeps.inventory().itemCatalog().entries()) {
            if (entry.equipSlot() != null) {
                equipment.add(entry.id());
            }
        }
        this.equipmentTemplates = Set.copyOf(equipment);
        this.deps = Optional.of(realDeps);
    }

    /**
     * Người được chia đồ của lượt hạ quái này: người ra đòn cuối + đồng đội ĐANG online cùng World. Rỗng (tức đồ đi
     * thẳng vào túi như cũ) nếu không có đồng đội nào như vậy. Virtual Thread.
     */
    List<Long> rollersFor(long killerId) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return List.of();
        }
        Deps d = current.get();
        Optional<WorldFacade> killerWorld = d.worldOf().apply(killerId);
        if (killerWorld.isEmpty()) {
            return List.of();
        }
        List<Long> rollers = new ArrayList<>();
        rollers.add(killerId);
        for (long member : d.partyMembers().apply(killerId)) {
            if (member != killerId
                    && d.registry().isOnline(member)
                    && d.worldOf().apply(member).equals(killerWorld)) {
                rollers.add(member);
            }
        }
        return rollers.size() > 1 ? List.copyOf(rollers) : List.of();
    }

    /**
     * Cấp đồ rơi của một lượt hạ quái (Virtual Thread): không có đồng đội cùng World ⇒ như cũ; có ⇒ trang bị tách ra mở
     * lượt tung, phần còn lại vào túi người ra đòn cuối.
     *
     * @return biên lai phần đã vào túi người ra đòn cuối
     */
    List<ItemStack> grantLoot(InventoryFacade inventory, long killerId, String dropTableCode) {
        List<Long> rollers = rollersFor(killerId);
        if (rollers.isEmpty()) {
            return inventory.grantLoot(killerId, dropTableCode);
        }
        GrantLootUseCase.Loot loot = inventory.grantLoot(killerId, dropTableCode, this::isRare);
        loot.diverted().forEach(stack -> start(new Opening(killerId, dropTableCode, stack, rollers)));
        return loot.granted();
    }

    /** Món này có phải đồ hiếm (trang bị) phải tung không. */
    boolean isRare(ItemStack stack) {
        return equipmentTemplates.contains(stack.itemTemplateId());
    }

    /** Mở một lượt tung và báo mọi người được chia. */
    private void start(Opening opening) {
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        ConnectedCharacterRegistry registry = current.get().registry();
        Roll roll = new Roll(nextId.incrementAndGet(), opening);
        ItemStack stack = opening.stack();
        rolls.put(roll.id, roll);
        byte[] event = LootRollStartedEvent.newBuilder()
                .setRollId(roll.id)
                .setItemTemplateId(stack.itemTemplateId())
                .setQuantity(stack.quantity())
                .setSeconds(ROLL_SECONDS)
                .build()
                .toByteArray();
        opening.participants()
                .forEach(member -> registry.sendToCharacterIfOnline(
                        member, Opcode.OPCODE_LOOT_ROLL_STARTED_EVENT_VALUE, 0, event));
        CompletableFuture.delayedExecutor(ROLL_SECONDS, TimeUnit.SECONDS, RESOLVES)
                .execute(() -> resolve(roll));
    }

    /** Lệnh chọn (Virtual Thread); {@code characterId} từ session. Đủ người chọn thì chốt ngay. */
    LootRollChoiceResponse choose(long characterId, LootRollChoiceCommand command) {
        LootRollChoiceResponse.Builder response =
                LootRollChoiceResponse.newBuilder().setRollId(command.getRollId());
        LootRollChoice choice = command.getChoice();
        if (choice != LootRollChoice.LOOT_ROLL_CHOICE_NEED
                && choice != LootRollChoice.LOOT_ROLL_CHOICE_GREED
                && choice != LootRollChoice.LOOT_ROLL_CHOICE_PASS) {
            return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_INVALID_CHOICE)
                    .build();
        }
        Roll roll = rolls.get(command.getRollId());
        if (roll == null) {
            return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_NOT_FOUND)
                    .build();
        }
        boolean everyoneChose;
        synchronized (roll) {
            if (roll.closed) {
                return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_NOT_FOUND)
                        .build();
            }
            LootRollChoice previous = roll.choices.get(characterId);
            if (previous == null) {
                return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_NOT_ELIGIBLE)
                        .build();
            }
            if (previous != LootRollChoice.LOOT_ROLL_CHOICE_UNSPECIFIED) {
                return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_ALREADY_CHOSEN)
                        .build();
            }
            roll.choices.put(characterId, choice);
            everyoneChose = !roll.choices.containsValue(LootRollChoice.LOOT_ROLL_CHOICE_UNSPECIFIED);
        }
        if (everyoneChose) {
            RESOLVES.execute(() -> resolve(roll));
        }
        return response.setOutcome(LootRollChoiceOutcome.LOOT_ROLL_CHOICE_OUTCOME_OK)
                .build();
    }

    // PMD AvoidCatchingGenericException: ranh giới task nền — lượt tung PHẢI được gỡ khỏi bảng dù cấp đồ lỗi gì.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void resolve(Roll roll) {
        Map<Long, LootRollChoice> choices;
        synchronized (roll) {
            if (roll.closed) {
                return;
            }
            roll.closed = true;
            choices = new LinkedHashMap<>(roll.choices);
        }
        rolls.remove(roll.id);
        Optional<Deps> current = deps;
        if (current.isEmpty()) {
            return;
        }
        try {
            award(current.get(), roll, choices);
        } catch (RuntimeException e) {
            LOG.warn("Chốt lượt tung đồ lỗi: rollId={}, killer={}", roll.id, roll.killerId, e);
        }
    }

    private void award(Deps d, Roll roll, Map<Long, LootRollChoice> choices) {
        LootRollChoice tier = choices.containsValue(LootRollChoice.LOOT_ROLL_CHOICE_NEED)
                ? LootRollChoice.LOOT_ROLL_CHOICE_NEED
                : LootRollChoice.LOOT_ROLL_CHOICE_GREED;
        LootRollResultEvent.Builder result = LootRollResultEvent.newBuilder()
                .setRollId(roll.id)
                .setItemTemplateId(roll.stack.itemTemplateId())
                .setQuantity(roll.stack.quantity());
        long winner = roll.killerId; // cả nhóm Bỏ ⇒ về người ra đòn cuối
        long best = -1;
        for (Map.Entry<Long, LootRollChoice> entry : choices.entrySet()) {
            LootRollChoice choice = entry.getValue() == LootRollChoice.LOOT_ROLL_CHOICE_UNSPECIFIED
                    ? LootRollChoice.LOOT_ROLL_CHOICE_PASS
                    : entry.getValue();
            int face = 0;
            long score = -1;
            if (choice == tier) {
                synchronized (random) {
                    face = 1 + random.nextInt(DIE_FACES);
                    score = (long) face * TIE_BREAK_SPACE + random.nextInt(TIE_BREAK_SPACE);
                }
            }
            if (score > best) {
                best = score;
                winner = entry.getKey();
            }
            result.addEntries(LootRollEntry.newBuilder()
                    .setCharacterId(entry.getKey())
                    .setName(nameOf(d, entry.getKey()))
                    .setChoice(choice)
                    .setRoll(face));
        }
        boolean byMail = !deliver(d, roll, winner);
        byte[] event = result.setWinnerCharacterId(winner)
                .setWinnerName(nameOf(d, winner))
                .setByMail(byMail)
                .build()
                .toByteArray();
        choices.keySet().forEach(member -> d.registry()
                .sendToCharacterIfOnline(member, Opcode.OPCODE_LOOT_ROLL_RESULT_EVENT_VALUE, 0, event));
    }

    /** Cấp cho người thắng; {@code false} nếu túi đầy và món đi bằng thư hệ thống. */
    private boolean deliver(Deps d, Roll roll, long winner) {
        List<ItemStack> granted = d.inventory().grantRolledLoot(winner, roll.dropTableCode, roll.stack);
        if (granted.isEmpty()) {
            d.notices()
                    .mail(
                            winner,
                            new PlayerNotices.SystemMail(
                                    "Chiến lợi phẩm / Loot",
                                    MAIL_BODY,
                                    0L,
                                    List.of(new MailFacade.AttachmentRequest(
                                            roll.stack.itemTemplateId(), roll.stack.quantity()))));
            return false;
        }
        for (ItemStack stack : granted) {
            d.registry()
                    .sendToCharacterIfOnline(
                            winner,
                            Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE,
                            0,
                            ItemGrantedEvent.newBuilder()
                                    .setItemTemplateId(stack.itemTemplateId())
                                    .setQuantity(stack.quantity())
                                    .setItemId(stack.id())
                                    .build()
                                    .toByteArray());
        }
        return true;
    }

    private static String nameOf(Deps d, long characterId) {
        return d.characters().findById(characterId).map(Character::name).orElse("#" + characterId);
    }

    /** Số lượt tung đang mở — cho test. */
    int openRolls() {
        return rolls.size();
    }
}
