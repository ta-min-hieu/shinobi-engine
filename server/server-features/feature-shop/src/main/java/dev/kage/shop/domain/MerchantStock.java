package dev.kage.shop.domain;

/** Một dòng hàng của MỘT lần ghé — {@code remaining} chung cả server (ADR-118). */
public record MerchantStock(long itemTemplateId, long price, int remaining) {}
