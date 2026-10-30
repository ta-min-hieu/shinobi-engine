package dev.kage.shop.domain;

/** Một món thương nhân du hành có thể mang (ADR-118, bảng {@code traveling_merchant_offer}). */
public record MerchantOffer(long itemTemplateId, long price, int quantityPerVisit, int weight) {}
