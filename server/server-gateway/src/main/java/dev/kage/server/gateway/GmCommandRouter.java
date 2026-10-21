package dev.kage.server.gateway;

import java.net.SocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.chat.api.ChatFacade;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.gm.api.GameEventFacade;
import dev.kage.gm.api.GmFacade;
import dev.kage.gm.domain.GameEvent;
import dev.kage.gm.domain.GmAuditEntry;
import dev.kage.gm.domain.GmCommand;
import dev.kage.gm.domain.GmCommandParser;
import dev.kage.gm.domain.GmOutcome;
import dev.kage.gm.domain.GmRole;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.server.world.WorldFacade;

/**
 * Thi hành một dòng {@code /gm ...} (ADR-052 D1/D4/D6/D10) — chạy trên Virtual Thread của kết nối người
 * ra lệnh, KHÔNG BAO GIỜ trên EventLoop (đọc quyền + ghi audit chạm DB).
 *
 * <p>Thứ tự: hai chốt cấu hình (D3) → quyền (đọc lại MỖI lệnh, D2) → parse → cấp → mục tiêu → không tác
 * động người cấp ≥ mình → MỞ dòng audit (write-ahead, D5) → thi hành → CHỐT dòng audit. Người không
 * có quyền (hoặc bị chốt cấu hình chặn) nhận IM LẶNG — không xác nhận lệnh GM tồn tại (D1).
 *
 * <p>Phản hồi là chữ tiếng Anh thuần ASCII (Q4 theo khuyến nghị): ngoại lệ hẹp của NETWORK.md §107, chỉ
 * gửi cho đúng người có quyền.
 */
final class GmCommandRouter {

    private static final Logger LOG = LoggerFactory.getLogger(GmCommandRouter.class);

    /** Khoá lý do bị đá — client coi là kết thúc phiên, KHÔNG tự đăng nhập lại (ADR-052 D7). */
    static final String KICKED_BY_GM_KEY = "error.kicked_by_gm";

    private static final String GIFT_SUBJECT = "GM";

    /** Trần một dòng trả lời — {@code inspect} của túi đầy đồ có thể dài. */
    private static final int MAX_REPLY_LENGTH = 400;

    private static final String GIFT_BODY = "Quà từ ban quản lý. / A gift from the game staff.";

    /** Những gì router cần — gom để constructor không vượt trần tham số. */
    record Deps(
            GmFacade gm,
            CharacterFacade characters,
            ChatFacade chat,
            MailFacade mail,
            InventoryFacade inventory,
            ConnectedCharacterRegistry registry,
            LongFunction<Optional<WorldFacade>> worldOf,
            LongToIntFunction maxHpOf,
            Relocator relocator,
            GameEventFacade events) {}

    /** Dời một nhân vật tới chỗ người khác đứng trong world chính — {@link MainWorldRelocation#move}. */
    @FunctionalInterface
    interface Relocator {
        MainWorldRelocation.Outcome move(long moverId, long anchorId);
    }

    /** Người ra lệnh — từ SESSION, không từ gói (SECURITY.md §4). */
    record Actor(long accountId, long characterId, SocketAddress remote) {}

    private final Deps deps;
    private final GmConfig config;
    private final Clock clock;

    GmCommandRouter(Deps deps, GmConfig config, Clock clock) {
        this.deps = deps;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Thi hành một dòng {@code /gm}.
     *
     * @return các dòng trả lời cho người ra lệnh; RỖNG = im lặng (không có quyền / bị chốt cấu hình chặn)
     */
    List<String> execute(Actor actor, String line) {
        if (!config.enabled() || !config.allows(actor.remote())) {
            LOG.warn("Lệnh GM bị chốt cấu hình chặn: accountId={}, remote={}", actor.accountId(), actor.remote());
            return List.of();
        }
        Optional<GmRole> role = deps.gm().roleOf(actor.accountId());
        if (role.isEmpty()) {
            LOG.warn("Lệnh GM từ tài khoản không có quyền: accountId={}", actor.accountId());
            return List.of();
        }
        return switch (GmCommandParser.parse(line)) {
            case GmCommandParser.Parsed.Invalid invalid ->
                List.of(audited(new Request(actor, role.get(), "invalid", line), OptionalLong.empty(), ticket -> {
                    ticket.finish(GmOutcome.REJECTED, "{}");
                    return invalid.usage();
                }));
            case GmCommandParser.Parsed.Ok ok -> List.of(authorizedAndRun(actor, role.get(), ok.command(), line));
        };
    }

    private String authorizedAndRun(Actor actor, GmRole role, GmCommand command, String line) {
        if (!role.atLeast(command.requiredRole())) {
            return audited(new Request(actor, role, command.name(), line), OptionalLong.empty(), ticket -> {
                ticket.finish(GmOutcome.DENIED, "{}");
                return "Denied: /gm " + command.name() + " needs " + command.requiredRole();
            });
        }
        if (command.targetName().isEmpty()) {
            return audited(
                    new Request(actor, role, command.name(), line),
                    OptionalLong.empty(),
                    ticket -> run(actor, command, Optional.empty(), ticket));
        }
        Optional<Character> target = deps.characters().findByName(command.targetName());
        if (target.isEmpty()) {
            return audited(new Request(actor, role, command.name(), line), OptionalLong.empty(), ticket -> {
                ticket.finish(GmOutcome.REJECTED, "{}");
                return "No character named '" + ascii(command.targetName()) + "'";
            });
        }
        Character who = target.get();
        OptionalLong targetId = OptionalLong.of(who.id());
        if (who.id() != actor.characterId() && outranksOrEquals(who, role)) {
            return audited(new Request(actor, role, command.name(), line), targetId, ticket -> {
                ticket.finish(GmOutcome.DENIED, "{}");
                return "Denied: target has GM rank equal to or above yours";
            });
        }
        return audited(
                new Request(actor, role, command.name(), line),
                targetId,
                ticket -> run(actor, command, target, ticket));
    }

    /** D4 — không tác động lên người có cấp ≥ mình (chặn GM bị chiếm quay sang khoá người thu hồi nó). */
    private boolean outranksOrEquals(Character target, GmRole role) {
        return deps.gm()
                .roleOf(target.accountId())
                .map(targetRole -> targetRole.atLeast(role))
                .orElse(false);
    }

    /** D10 — lối DUY NHẤT tới hàm thi hành: phải có ticket, tức phải có dòng audit PENDING. */
    // PMD AvoidCatchingGenericException: đúng ý — lệnh hỏng giữa chừng phải CHỐT dòng audit thành FAILED
    // (D5: không dòng nào PENDING mãi vì một lỗi đã biết), rồi ném tiếp nguyên lỗi cho executeBlocking.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String audited(Request request, OptionalLong target, TicketedRun run) {
        Actor actor = request.actor();
        String command = request.command();
        GmFacade.AuditTicket ticket = deps.gm()
                .begin(new GmAuditEntry(
                        actor.accountId(), actor.characterId(), request.role(), command, request.line(), target));
        try {
            String reply = run.run(ticket);
            LOG.info(
                    "Lệnh GM: actor={}, command={}, target={}, auditId={}",
                    actor.characterId(),
                    command,
                    target,
                    ticket.auditId());
            return reply;
        } catch (RuntimeException e) {
            ticket.finish(GmOutcome.FAILED, "{}");
            throw e;
        }
    }

    /** Ai, cấp gì, lệnh gì, nguyên văn — gom để {@link #audited} không vượt trần tham số. */
    private record Request(Actor actor, GmRole role, String command, String line) {}

    @FunctionalInterface
    private interface TicketedRun {
        String run(GmFacade.AuditTicket ticket);
    }

    private String run(Actor actor, GmCommand command, Optional<Character> target, GmFacade.AuditTicket ticket) {
        return switch (command) {
            case GmCommand.Help _ -> help(ticket);
            case GmCommand.Who _ -> who(target.orElseThrow(), ticket);
            case GmCommand.Kick _ -> kick(target.orElseThrow(), ticket);
            case GmCommand.Mute mute -> mute(target.orElseThrow(), mute.minutes(), ticket);
            case GmCommand.Unmute _ -> unmute(target.orElseThrow(), ticket);
            case GmCommand.Announce announce -> announce(announce.text(), ticket);
            case GmCommand.Heal _ -> heal(target.orElseThrow(), ticket);
            case GmCommand.Inspect _ -> inspect(target.orElseThrow(), ticket);
            case GmCommand.Goto _ ->
                relocate(actor.characterId(), target.orElseThrow().id(), ticket);
            case GmCommand.Summon _ -> relocate(target.orElseThrow().id(), actor.characterId(), ticket);
            case GmCommand.Give give -> give(target.orElseThrow(), give.itemCode(), give.quantity(), ticket);
            case GmCommand.Gold gold -> gold(target.orElseThrow(), gold.amount(), ticket);
            case GmCommand.Event event -> event(event, ticket);
        };
    }

    private static String help(GmFacade.AuditTicket ticket) {
        ticket.finish(GmOutcome.OK, "{}");
        return "GM: help who inspect kick mute unmute announce heal goto summon | ADMIN: give gold event";
    }

    private String who(Character target, GmFacade.AuditTicket ticket) {
        ticket.finish(GmOutcome.OK, "{}");
        return "id=%d account=%d level=%d gold=%d online=%s"
                .formatted(
                        target.id(),
                        target.accountId(),
                        target.level(),
                        deps.characters().loadGold(target.id()),
                        deps.registry().isOnline(target.id()) ? "yes" : "no");
    }

    private String kick(Character target, GmFacade.AuditTicket ticket) {
        Optional<CharacterSessionRegistry.Session> session =
                deps.registry().sessions().ownerOf(target.id());
        if (session.isEmpty()) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "Not online";
        }
        // Không chờ: lần dọn của phiên bị đá chạy trên EventLoop của NÓ; lỗi ở đó đã được finishRelease log.
        session.get().evict(target.id(), KICKED_BY_GM_KEY).exceptionally(error -> {
            LOG.warn("Đá phiên bằng lệnh GM lỗi lúc dọn: characterId={}", target.id(), error);
            return null;
        });
        ticket.finish(GmOutcome.OK, "{}");
        return "Kicked " + target.id();
    }

    private String mute(Character target, int minutes, GmFacade.AuditTicket ticket) {
        Instant until = clock.instant().plus(Duration.ofMinutes(minutes));
        deps.chat().mute(target.accountId(), until, ticket.auditId());
        ticket.finish(GmOutcome.OK, "{\"mutedUntil\":\"" + until + "\"}");
        return "Muted " + target.id() + " until " + until;
    }

    private String unmute(Character target, GmFacade.AuditTicket ticket) {
        boolean wasMuted = deps.chat().unmute(target.accountId());
        ticket.finish(wasMuted ? GmOutcome.OK : GmOutcome.REJECTED, "{}");
        return wasMuted ? "Unmuted " + target.id() : "Was not muted";
    }

    private String announce(String text, GmFacade.AuditTicket ticket) {
        String body = ChatMessage.singleLine(text);
        if (ProfanityFilter.containsBannedWord(body)) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "Announcement contains a banned word";
        }
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build();
        deps.registry().broadcastToAll(Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, event.toByteArray());
        ticket.finish(GmOutcome.OK, "{}");
        return "Announced";
    }

    private String heal(Character target, GmFacade.AuditTicket ticket) {
        Optional<WorldFacade> world = deps.worldOf().apply(target.id());
        if (world.isEmpty()) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "Not in a world";
        }
        // Đúng maxHp, KHÔNG Integer.MAX_VALUE: handleHealCharacter cộng rồi kẹp — MAX_VALUE tràn số (D6).
        world.get().enqueueHealCharacter(target.id(), deps.maxHpOf().applyAsInt(target.id()));
        ticket.finish(GmOutcome.OK, "{}");
        return "Healed " + target.id();
    }

    /**
     * Túi + đồ đang mặc, một dòng: {@code mã xSố +cường hoá}, món đang mặc đánh dấu {@code *}. Chỉ đọc
     * qua {@link InventoryFacade#listInventory} — không đụng gì của người bị xem.
     */
    private String inspect(Character target, GmFacade.AuditTicket ticket) {
        InventorySnapshot snapshot = deps.inventory().listInventory(target.id());
        Map<Long, String> codes = new HashMap<>();
        for (ItemCatalogEntry entry : deps.inventory().itemCatalog().entries()) {
            codes.put(entry.id(), entry.code());
        }
        Set<Long> equipped = new HashSet<>(snapshot.equipment().slots().values());
        StringBuilder line = new StringBuilder("items=")
                .append(snapshot.inventory().items().size())
                .append(':');
        for (ItemStack stack : snapshot.inventory().items()) {
            line.append(' ')
                    .append(equipped.contains(stack.id()) ? "*" : "")
                    .append(codes.getOrDefault(stack.itemTemplateId(), "#" + stack.itemTemplateId()));
            if (stack.quantity() > 1) {
                line.append('x').append(stack.quantity());
            }
            if (stack.enhanceLevel() > 0) {
                line.append('+').append(stack.enhanceLevel());
            }
        }
        ticket.finish(GmOutcome.OK, "{}");
        return line.length() <= MAX_REPLY_LENGTH ? line.toString() : line.substring(0, MAX_REPLY_LENGTH - 3) + "...";
    }

    /** goto/summon (D4): chỉ trong world chính; dời chính mình tới chỗ mình là không làm gì. */
    private String relocate(long moverId, long anchorId, GmFacade.AuditTicket ticket) {
        if (moverId == anchorId) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "Already there";
        }
        MainWorldRelocation.Outcome outcome = deps.relocator().move(moverId, anchorId);
        ticket.finish(
                outcome == MainWorldRelocation.Outcome.OK ? GmOutcome.OK : GmOutcome.REJECTED,
                "{\"result\":\"" + outcome + "\"}");
        return switch (outcome) {
            case OK -> "Moved " + moverId + " to " + anchorId;
            case NOT_IN_MAIN_WORLD -> "Both characters must be online in the main world";
            case NO_POSITION -> "Position of " + anchorId + " not known yet, try again";
        };
    }

    /** ADR-067 — bật/tắt sự kiện nhân đôi rồi báo cho MỌI người đang online (cả khi tắt: banner phải tắt). */
    private String event(GmCommand.Event command, GmFacade.AuditTicket ticket) {
        String reply;
        if (command.stops()) {
            boolean wasActive = deps.events().stop(command.kind());
            ticket.finish(wasActive ? GmOutcome.OK : GmOutcome.REJECTED, "{}");
            reply = wasActive ? "Stopped " + command.kind() + " event" : "No " + command.kind() + " event running";
        } else {
            GameEvent started = deps.events()
                    .start(
                            command.kind(),
                            command.multiplier(),
                            Duration.ofMinutes(command.minutes()),
                            ticket.auditId());
            ticket.finish(GmOutcome.OK, "{\"endsAt\":\"" + started.endsAt() + "\"}");
            reply = "Started " + command.kind() + " x" + command.multiplier() + " until " + started.endsAt();
        }
        deps.registry()
                .broadcastToAll(
                        Opcode.OPCODE_GAME_EVENTS_CHANGED_EVENT_VALUE,
                        GameEventNotices.of(deps.events().active()).toByteArray());
        return reply;
    }

    private String give(Character target, String itemCode, int quantity, GmFacade.AuditTicket ticket) {
        Optional<ItemCatalogEntry> item = deps.inventory().itemCatalog().entries().stream()
                .filter(entry -> entry.code().equals(itemCode))
                .findFirst();
        if (item.isEmpty()) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "No item '" + itemCode + "'";
        }
        return sendGiftMail(
                target, 0L, List.of(new MailFacade.AttachmentRequest(item.get().id(), quantity)), ticket);
    }

    private String gold(Character target, long amount, GmFacade.AuditTicket ticket) {
        return sendGiftMail(target, amount, List.of(), ticket);
    }

    /** give/gold đi THƯ HỆ THỐNG (D4): túi đầy không mất đồ, offline vẫn nhận, lĩnh qua MAIL_CLAIM. */
    private String sendGiftMail(
            Character target, long gold, List<MailFacade.AttachmentRequest> items, GmFacade.AuditTicket ticket) {
        MailFacade.SendOutcome outcome = deps.mail()
                .sendMail(new MailFacade.SendMailRequest(null, target.id(), GIFT_SUBJECT, GIFT_BODY, gold, items));
        if (outcome.result() != MailFacade.SendResult.OK) {
            ticket.finish(GmOutcome.FAILED, "{\"result\":\"" + outcome.result() + "\"}");
            return "Mail failed: " + outcome.result();
        }
        deps.registry()
                .sendToCharacterIfOnline(
                        target.id(),
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
        ticket.finish(GmOutcome.OK, "{\"mailId\":" + outcome.mailId() + "}");
        return "Sent mail " + outcome.mailId() + " to " + target.id();
    }

    /** Tên do người chơi đặt có thể không phải ASCII — phản hồi GM giữ ASCII (Q4), thay ký tự lạ bằng '?'. */
    private static String ascii(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= ' ' && c < 0x7F ? c : '?');
        }
        return out.toString();
    }
}
