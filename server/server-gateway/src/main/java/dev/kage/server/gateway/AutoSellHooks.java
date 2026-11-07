package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;

import dev.kage.inventory.api.AutoSellJunkFacade;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Lọc nhặt đồ (ADR-204). Lúc chia đồ rơi (Virtual Thread, {@code grantLootBlocking}): chồng nào là rác CÓ giá thu và
 * người hạ đã bật công tắc thì tách ra khỏi túi ({@link #divertFor}), rồi bán ngay ({@link #sell}). Bán không được
 * (vàng chạm trần) ⇒ cấp món như thường — không bao giờ mất đồ.
 */
final class AutoSellHooks {

    /** Thứ việc này cần. */
    record Deps(AutoSellJunkFacade facade, ConnectedCharacterRegistry registry) {}

    // volatile, không final: registry dựng TRƯỚC DB — bind() sau, cùng khuôn LootRollHooks.
    private volatile Optional<Deps> deps = Optional.empty();
    private volatile Set<Long> junkTemplates = Set.of();

    /** Nối bản thật — một lần, lúc khởi động. Danh mục rác cố định lúc chạy nên chỉ đọc một lần. */
    void bind(Deps realDeps) {
        this.junkTemplates = realDeps.facade().junkTemplates();
        this.deps = Optional.of(realDeps);
    }

    /**
     * Món nào phải tách ra để bán. Hỏi công tắc trong DB tối đa MỘT lần mỗi lượt chia, và chỉ khi thật sự có món rác
     * rơi.
     */
    Predicate<ItemStack> divertFor(long characterId) {
        if (deps.isEmpty() || junkTemplates.isEmpty()) {
            return stack -> false;
        }
        AutoSellJunkFacade facade = deps.get().facade();
        Boolean[] enabled = new Boolean[1];
        return stack -> {
            if (!junkTemplates.contains(stack.itemTemplateId())) {
                return false;
            }
            if (enabled[0] == null) {
                enabled[0] = facade.enabled(characterId);
            }
            return enabled[0];
        };
    }

    /** Bán các chồng rác đã tách; không bán được thì cấp vào túi qua đúng đường đồ rơi. */
    void sell(InventoryFacade inventory, long characterId, String dropTableCode, List<ItemStack> junk) {
        if (junk.isEmpty() || deps.isEmpty()) {
            return;
        }
        Deps d = deps.get();
        OptionalLong gold = d.facade().sell(characterId, junk);
        if (gold.isEmpty()) {
            junk.forEach(stack -> inventory.grantRolledLoot(characterId, dropTableCode, stack));
            return;
        }
        int count = junk.stream().mapToInt(ItemStack::quantity).sum();
        String body = "Tự bán " + count + " món rác: +" + gold.getAsLong() + " linh thạch. / Auto-sold " + count
                + " junk items: +" + gold.getAsLong() + " spirit stones.";
        d.registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE,
                        0,
                        ChatMessageEvent.newBuilder()
                                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                                .setBody(body)
                                .build()
                                .toByteArray());
    }
}
