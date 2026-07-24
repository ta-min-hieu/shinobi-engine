package dev.kage.party.domain;

import java.time.Instant;
import java.util.Objects;

/** 1 dòng thành viên — {@code characterId} là UNIQUE ở tầng DB (1 nhân vật tối đa 1 party). */
public record PartyMember(long partyId, long characterId, PartyRole role, Instant joinedAt) {

    public PartyMember {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
