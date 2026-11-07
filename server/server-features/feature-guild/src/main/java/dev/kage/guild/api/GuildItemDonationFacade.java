package dev.kage.guild.api;

import dev.kage.guild.domain.GuildItemDonationPort;

/** API góp vật phẩm cho bang (ADR-207). */
public final class GuildItemDonationFacade {

    /** Số lượng tối đa một lần góp — chặn số vô lý trước khi chạm DB (không chồng nào lớn hơn). */
    public static final int MAX_QUANTITY = 9_999;

    /** Không ai ở bang nào (composition test). */
    public static final GuildItemDonationFacade DISABLED =
            new GuildItemDonationFacade((characterId, inventoryItemId, quantity) ->
                    GuildItemDonationPort.Donation.refused(GuildItemDonationPort.Outcome.NOT_IN_GUILD));

    private final GuildItemDonationPort port;

    /** Dựng ở composition root. */
    public GuildItemDonationFacade(GuildItemDonationPort port) {
        this.port = port;
    }

    /** Góp {@code quantity} cái (1…{@value #MAX_QUANTITY}) từ chồng {@code inventoryItemId}. */
    public GuildItemDonationPort.Donation donate(long characterId, long inventoryItemId, int quantity) {
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            return GuildItemDonationPort.Donation.refused(GuildItemDonationPort.Outcome.INVALID_QUANTITY);
        }
        return port.donate(characterId, inventoryItemId, quantity);
    }
}
