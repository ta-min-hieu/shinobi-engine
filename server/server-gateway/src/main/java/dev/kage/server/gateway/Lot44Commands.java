package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.Faction;
import dev.kage.inventory.domain.ItemLookupPort;
import dev.kage.protocol.DuelHistoryEntry;
import dev.kage.protocol.FactionCount;
import dev.kage.protocol.GetDuelHistoryResponse;
import dev.kage.protocol.GetOnlineCountResponse;
import dev.kage.protocol.ItemDropSourceView;
import dev.kage.protocol.LookupItemRequest;
import dev.kage.protocol.LookupItemResponse;
import dev.kage.pvp.domain.DuelHistoryPort;

/** Lớp lệnh lô 44 (ADR-279…) — MỘT thành phần của {@link Lot43Commands}. Gọi trên Virtual Thread. */
record Lot44Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Số cảnh giới (Luyện Khí … Hoá Thần). */
    static final int REALMS = 6;

    /** ADR-279 — giá cửa hàng NPC + nguồn rơi của một mẫu vật phẩm (thông tin công khai). */
    LookupItemResponse lookupItem(LookupItemRequest request) {
        ItemLookupPort.Lookup lookup = facades().itemLookup().lookup(request.getItemTemplateId());
        LookupItemResponse.Builder response = LookupItemResponse.newBuilder()
                .setItemTemplateId(request.getItemTemplateId())
                .setBuyPrice(lookup.buyPrice())
                .setSellPrice(lookup.sellPrice());
        for (ItemLookupPort.DropSource drop : lookup.drops()) {
            response.addDrops(ItemDropSourceView.newBuilder()
                    .setMonsterCode(drop.monsterCode())
                    .setMonsterNameKey(drop.monsterNameKey())
                    .setChancePercent(drop.chancePercent()));
        }
        return response.build();
    }

    /** ADR-280 — 10 trận đấu tay đôi gần nhất. */
    GetDuelHistoryResponse duelHistory(long characterId) {
        GetDuelHistoryResponse.Builder response = GetDuelHistoryResponse.newBuilder();
        for (DuelHistoryPort.Duel duel : facades().duelHistory().recent(characterId)) {
            response.addEntries(DuelHistoryEntry.newBuilder()
                    .setOpponentName(duel.opponentName())
                    .setWon(duel.won())
                    .setRatingAfter(duel.ratingAfter())
                    .setAtEpochSeconds(duel.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-281 — số người online, theo linh căn và cảnh giới. Không lộ tên. */
    GetOnlineCountResponse onlineCount() {
        Map<Faction, Integer> byFaction = new EnumMap<>(Faction.class);
        int[] byRealm = new int[REALMS];
        int noFaction = 0;
        int total = 0;
        for (long id : registry.onlineCharacterIds()) {
            Optional<Character> character = gateway.characterFacade().findById(id);
            if (character.isEmpty()) {
                continue;
            }
            total++;
            byRealm[realmOf(character.get().level())]++;
            if (character.get().faction() == null) {
                noFaction++;
            } else {
                byFaction.merge(character.get().faction(), 1, Integer::sum);
            }
        }
        GetOnlineCountResponse.Builder response =
                GetOnlineCountResponse.newBuilder().setTotal(total);
        byFaction.forEach((faction, count) -> response.addByFaction(
                FactionCount.newBuilder().setFaction(faction.name()).setCount(count)));
        if (noFaction > 0) {
            response.addByFaction(FactionCount.newBuilder().setFaction("NONE").setCount(noFaction));
        }
        for (int count : byRealm) {
            response.addByRealm(count);
        }
        return response.build();
    }

    /** Cảnh giới 0…5 của một cấp (cùng bảng {@link BreakthroughHooks}). Tĩnh để test. */
    static int realmOf(int level) {
        return Math.clamp((Math.max(level, 1) - 1) / BreakthroughHooks.LEVELS_PER_REALM, 0, REALMS - 1);
    }

    /** ADR-286 — bật / tắt (hoặc hỏi) tự nhận lời mời nhóm từ bạn bè. */
    dev.kage.protocol.SetPartyAutoAcceptResponse setAutoAccept(
            long characterId, dev.kage.protocol.SetPartyAutoAcceptCommand command) {
        if (!command.getQueryOnly()) {
            facades().partyAutoAccept().setEnabled(characterId, command.getEnabled());
        }
        return dev.kage.protocol.SetPartyAutoAcceptResponse.newBuilder()
                .setEnabled(facades().partyAutoAccept().enabled(characterId))
                .build();
    }

    /**
     * ADR-286 — ngay sau một lời mời nhóm thành công: người được mời bật tự nhận và người mời là BẠN của họ ⇒ chấp nhận
     * thay họ (đúng đường chấp nhận thường, cùng mọi luật đầy nhóm/đã có nhóm) và báo người được mời một dòng chat.
     *
     * @return {@code true} nếu đã vào nhóm (người gọi phát sự kiện đổi thành viên như đường chấp nhận thường)
     */
    boolean autoAcceptIfFriend(long inviterId, long targetId) {
        if (!facades().partyAutoAccept().enabled(targetId)
                || !gateway.contentFacades()
                        .socialFacades()
                        .friendFacade()
                        .listFriends(targetId)
                        .contains(inviterId)) {
            return false;
        }
        dev.kage.party.api.PartyFacade parties =
                gateway.contentFacades().socialFacades().partyFacade();
        Optional<Long> partyId =
                parties.findMyParty(inviterId).map(dev.kage.party.api.PartyFacade.MyPartyView::partyId);
        Optional<dev.kage.party.api.PartyFacade.PendingInviteView> invite =
                parties.listPendingInvites(targetId).stream()
                        .filter(pending -> partyId.isPresent() && pending.partyId() == partyId.get())
                        .findFirst();
        boolean joined = invite.isPresent()
                && parties.acceptInvite(invite.get().inviteId(), targetId)
                        == dev.kage.party.api.PartyFacade.AcceptResult.OK;
        if (joined) {
            String inviter = gateway.characterFacade()
                    .findById(inviterId)
                    .map(Character::name)
                    .orElse("?");
            registry.sendToCharacterIfOnline(
                    targetId, dev.kage.protocol.Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, autoAcceptNotice(inviter));
        }
        return joined;
    }

    /** Dòng chat song ngữ báo đã tự vào nhóm. Tĩnh để test. */
    static byte[] autoAcceptNotice(String inviterName) {
        return dev.kage.protocol.ChatMessageEvent.newBuilder()
                .setChannel(dev.kage.protocol.ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Đã tự vào nhóm của bạn bè " + inviterName + ". / Automatically joined your friend "
                        + inviterName + "'s party.")
                .build()
                .toByteArray();
    }

    private Lot44Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44();
    }
}
