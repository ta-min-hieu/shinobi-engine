package dev.kage.shop.domain;

/** ADR-202 — một lần bán cho cửa hàng NPC, đủ để mua lại đúng món đó với đúng giá đã nhận. */
public record SoldItem(long characterId, long itemTemplateId, int quantity, int enhanceLevel, long goldEarned) {}
