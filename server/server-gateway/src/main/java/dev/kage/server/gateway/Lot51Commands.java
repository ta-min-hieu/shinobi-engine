package dev.kage.server.gateway;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.DeathLogPort;
import dev.kage.chat.api.BulletinFacade;
import dev.kage.chat.domain.BulletinPort;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.friend.api.StatusMessageFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.BulletinPost;
import dev.kage.protocol.CastVoteKickCommand;
import dev.kage.protocol.CastVoteKickResponse;
import dev.kage.protocol.DeathEntry;
import dev.kage.protocol.GetDeathHistoryResponse;
import dev.kage.protocol.ListBulletinResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PostBulletinCommand;
import dev.kage.protocol.PostBulletinOutcome;
import dev.kage.protocol.PostBulletinResponse;
import dev.kage.protocol.SetStatusMessageCommand;
import dev.kage.protocol.SetStatusMessageOutcome;
import dev.kage.protocol.SetStatusMessageResponse;
import dev.kage.protocol.StartVoteKickCommand;
import dev.kage.protocol.StartVoteKickResponse;
import dev.kage.protocol.VoteKickEvent;
import dev.kage.protocol.VoteKickOutcome;

/** Lớp lệnh lô 51 (ADR-335…) — MỘT thành phần của {@link Lot50Commands}. Gọi trên Virtual Thread. */
record Lot51Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-335 — hỏi / đặt / xoá lời nhắn trạng thái của mình. */
    SetStatusMessageResponse setStatusMessage(long characterId, SetStatusMessageCommand command) {
        StatusMessageFacade statuses = facades().statusMessages();
        SetStatusMessageOutcome outcome = SetStatusMessageOutcome.SET_STATUS_MESSAGE_OUTCOME_OK;
        if (!command.getQueryOnly()) {
            String text = command.getMessage().strip();
            if (validText(text, StatusMessageFacade.MAX_LENGTH)) {
                statuses.set(characterId, text);
            } else {
                outcome = SetStatusMessageOutcome.SET_STATUS_MESSAGE_OUTCOME_INVALID_TEXT;
            }
        }
        return SetStatusMessageResponse.newBuilder()
                .setOutcome(outcome)
                .setMessage(statuses.messageOf(characterId))
                .build();
    }

    /** ADR-335 — lời nhắn của các bạn bè (cho danh sách bạn bè). */
    Map<Long, String> statusesOf(List<Long> friendIds) {
        return facades().statusMessages().messagesOf(friendIds);
    }

    /** ≤ {@code max} ký tự, không ký tự điều khiển, không từ cấm. Tĩnh để test. */
    static boolean validText(String text, int max) {
        return text.length() <= max
                && text.chars().noneMatch(java.lang.Character::isISOControl)
                && !ProfanityFilter.containsBannedWord(text);
    }

    /** ADR-336 — đăng (thay) tin rao vặt của mình; rỗng = gỡ. */
    PostBulletinResponse postBulletin(long characterId, PostBulletinCommand command) {
        String body = command.getBody().strip();
        PostBulletinOutcome outcome;
        if (body.isEmpty()) {
            facades().bulletin().remove(characterId);
            outcome = PostBulletinOutcome.POST_BULLETIN_OUTCOME_REMOVED;
        } else if (!validText(body, BulletinFacade.MAX_LENGTH)) {
            outcome = PostBulletinOutcome.POST_BULLETIN_OUTCOME_INVALID_TEXT;
        } else {
            outcome = facades().bulletin().post(characterId, body, Instant.now()) == BulletinFacade.PostResult.OK
                    ? PostBulletinOutcome.POST_BULLETIN_OUTCOME_OK
                    : PostBulletinOutcome.POST_BULLETIN_OUTCOME_TOO_SOON;
        }
        return PostBulletinResponse.newBuilder().setOutcome(outcome).build();
    }

    /** ADR-336 — tin rao vặt còn hạn, mới trước. */
    ListBulletinResponse listBulletin() {
        ListBulletinResponse.Builder response = ListBulletinResponse.newBuilder();
        for (BulletinPort.Post post : facades().bulletin().active(Instant.now())) {
            gateway.characterFacade()
                    .findById(post.characterId())
                    .ifPresent(author -> response.addPosts(BulletinPost.newBuilder()
                            .setName(author.name())
                            .setBody(post.body())
                            .setPostedAtEpochSeconds(post.postedAt().getEpochSecond())
                            .setOnline(registry.isOnline(author.id()))));
        }
        return response.build();
    }

    /** ADR-337 — mười lần bị hạ gần nhất, mới trước. */
    GetDeathHistoryResponse deathHistory(long characterId) {
        GetDeathHistoryResponse.Builder response = GetDeathHistoryResponse.newBuilder();
        for (DeathLogPort.Entry entry : facades().deathLog().recent(characterId)) {
            response.addEntries(DeathEntry.newBuilder()
                    .setKillerName(entry.killerName())
                    .setMonsterCode(entry.monsterCode())
                    .setX(entry.x())
                    .setAtEpochSeconds(entry.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-338 — khởi xướng biểu quyết đuổi {@code target_name} khỏi nhóm (mình tính một phiếu đồng ý). */
    StartVoteKickResponse startVoteKick(long characterId, StartVoteKickCommand command) {
        Optional<PartyFacade.MyPartyView> party = parties().findMyParty(characterId);
        if (party.isEmpty()) {
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_NOT_IN_PARTY, null);
        }
        List<PartyFacade.MemberView> members = parties().listMembers(characterId);
        Optional<Character> target =
                gateway.characterFacade().findByName(command.getTargetName().strip());
        Optional<PartyFacade.MemberView> targetMember = target.flatMap(found -> members.stream()
                .filter(member -> member.characterId() == found.id())
                .findFirst());
        if (targetMember.isEmpty()) {
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_TARGET_NOT_MEMBER, null);
        }
        long targetId = targetMember.get().characterId();
        if (targetId == characterId) {
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_SELF, null);
        }
        if (targetMember.get().isLeader()) {
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_TARGET_IS_LEADER, null);
        }
        Set<Long> voters = new HashSet<>();
        members.stream().filter(member -> member.characterId() != targetId).forEach(m -> voters.add(m.characterId()));
        long now = System.currentTimeMillis();
        PartyVoteKicks.Vote vote = new PartyVoteKicks.Vote(targetId, characterId, voters, now);
        if (!registry.voteKicks.start(party.get().partyId(), vote, now)) {
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_ALREADY_RUNNING, null);
        }
        if (vote.passed()) {
            kick(members, targetId, vote);
            return started(VoteKickOutcome.VOTE_KICK_OUTCOME_PASSED, vote);
        }
        announce(vote, now, false);
        return started(VoteKickOutcome.VOTE_KICK_OUTCOME_STARTED, vote);
    }

    /** ADR-338 — bỏ phiếu cho cuộc bầu đang chạy của nhóm mình. */
    CastVoteKickResponse castVoteKick(long characterId, CastVoteKickCommand command) {
        Optional<PartyFacade.MyPartyView> party = parties().findMyParty(characterId);
        if (party.isEmpty()) {
            return cast(VoteKickOutcome.VOTE_KICK_OUTCOME_NOT_IN_PARTY, Optional.empty());
        }
        long partyId = party.get().partyId();
        long now = System.currentTimeMillis();
        Optional<PartyVoteKicks.Vote> vote = registry.voteKicks.active(partyId, now);
        PartyVoteKicks.Cast result = registry.voteKicks.cast(partyId, characterId, command.getYes(), now);
        return switch (result) {
            case NO_VOTE -> cast(VoteKickOutcome.VOTE_KICK_OUTCOME_NO_VOTE, Optional.empty());
            case ALREADY_VOTED -> cast(VoteKickOutcome.VOTE_KICK_OUTCOME_ALREADY_VOTED, vote);
            case COUNTED -> cast(VoteKickOutcome.VOTE_KICK_OUTCOME_COUNTED, vote);
            case PASSED -> {
                kick(parties().listMembers(characterId), vote.orElseThrow().targetId, vote.get());
                yield cast(VoteKickOutcome.VOTE_KICK_OUTCOME_PASSED, vote);
            }
        };
    }

    /** Thông qua: trưởng nhóm "ra lệnh" đuổi (đúng đường kick hiện có), mọi người được báo. */
    private void kick(List<PartyFacade.MemberView> members, long targetId, PartyVoteKicks.Vote vote) {
        Optional<Long> leader = members.stream()
                .filter(PartyFacade.MemberView::isLeader)
                .map(PartyFacade.MemberView::characterId)
                .findFirst();
        if (leader.isEmpty()) {
            return;
        }
        List<Long> before =
                members.stream().map(PartyFacade.MemberView::characterId).toList();
        if (parties().kickMember(leader.get(), targetId) == PartyFacade.KickResult.OK) {
            new MembershipNotices(registry).partyKick(leader.get(), targetId, before);
            announce(vote, System.currentTimeMillis(), true);
        }
    }

    /** Báo cuộc bầu (mới mở hoặc vừa thông qua) cho những người được bầu, trừ người khởi xướng khi mới mở. */
    private void announce(PartyVoteKicks.Vote vote, long now, boolean passed) {
        byte[] event = VoteKickEvent.newBuilder()
                .setTargetName(nameOf(vote.targetId))
                .setInitiatorName(nameOf(vote.initiatorId))
                .setYes(vote.yes())
                .setNeeded(vote.needed())
                .setSecondsLeft(vote.secondsLeft(now))
                .setPassed(passed)
                .build()
                .toByteArray();
        for (long voter : vote.voters) {
            if (passed || voter != vote.initiatorId) {
                registry.sendToCharacterIfOnline(voter, Opcode.OPCODE_VOTE_KICK_EVENT_VALUE, 0, event);
            }
        }
    }

    private String nameOf(long characterId) {
        return gateway.characterFacade()
                .findById(characterId)
                .map(Character::name)
                .orElse("?");
    }

    private static StartVoteKickResponse started(VoteKickOutcome outcome, PartyVoteKicks.Vote vote) {
        StartVoteKickResponse.Builder response =
                StartVoteKickResponse.newBuilder().setOutcome(outcome);
        if (vote != null) {
            response.setYes(vote.yes()).setNeeded(vote.needed());
        }
        return response.build();
    }

    private static CastVoteKickResponse cast(VoteKickOutcome outcome, Optional<PartyVoteKicks.Vote> vote) {
        CastVoteKickResponse.Builder response =
                CastVoteKickResponse.newBuilder().setOutcome(outcome);
        vote.ifPresent(found -> response.setYes(found.yes()).setNeeded(found.needed()));
        return response.build();
    }

    private PartyFacade parties() {
        return gateway.contentFacades().socialFacades().partyFacade();
    }

    private Lot51Facades facades() {
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
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .lot49()
                .lot50()
                .lot51();
    }
}
