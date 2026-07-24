package dev.kage.party.domain;

/** LEADER tự động chuyển cho thành viên vào sớm nhất khi leader rời ({@link PartyPort#leaveParty}). */
public enum PartyRole {
    LEADER,
    MEMBER
}
