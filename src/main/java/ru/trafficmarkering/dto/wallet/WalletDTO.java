package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.model.wallet.Wallet;

@Schema(description = "Кошелёк заказчика: свободные деньги и сколько уже распределено по объявлениям")
public record WalletDTO(
        @Schema(description = "ID пользователя-заказчика") Long userId,
        String customerName,
        @Schema(description = "Почта заказчика, она же логин") String customerEmail,
        @Schema(description = "Компания заказчика; null — профиль не заполнен") String customerCompany,
        @Schema(description = "Свободные средства, в копейках") Long balanceKopecks,
        @Schema(description = "Сумма бюджетов всех объявлений заказчика, в копейках") Long allocatedKopecks,
        @Schema(description = "Уже начислено криаторам по всем объявлениям, в копейках") Long spentKopecks,
        @Schema(description = "Адрес TRON платформы для пополнения USDT (TRC-20); null — не настроен") String topUpTronAddress,
        String updatedAt
) {
    public static WalletDTO from(Wallet wallet,
                                 User customer,
                                 CustomerProfile customerProfile,
                                 long allocatedKopecks,
                                 long spentKopecks,
                                 String topUpTronAddress) {
        return new WalletDTO(
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                customer != null ? customer.getUsername() : null,
                customerProfile != null ? customerProfile.getCompany() : null,
                wallet.balance(),
                allocatedKopecks,
                spentKopecks,
                topUpTronAddress,
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString() : null);
    }
}
