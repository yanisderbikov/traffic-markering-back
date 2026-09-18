package ru.trafficmarkering.dto.wallet;

import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.wallet.Transfer;
import ru.trafficmarkering.model.wallet.WalletTransaction;

public record MoneyFlowDTO(FlowPointDTO source, FlowPointDTO destination) {

    public static MoneyFlowDTO of(WalletTransaction transaction, Transfer transfer) {
        User owner = transaction.getWallet() != null ? transaction.getWallet().getUser() : null;
        String tronAddress = transfer != null ? transfer.getTronAddress() : null;
        return switch (transaction.getType()) {
            case TOP_UP -> new MoneyFlowDTO(FlowPointDTO.external(), FlowPointDTO.customerWallet(owner));
            case WITHDRAWAL -> new MoneyFlowDTO(FlowPointDTO.customerWallet(owner),
                    tronAddress != null ? FlowPointDTO.tron(tronAddress) : FlowPointDTO.external());
            case ALLOCATION -> new MoneyFlowDTO(FlowPointDTO.customerWallet(owner),
                    FlowPointDTO.campaign(transaction.getCampaign()));
            case RELEASE -> new MoneyFlowDTO(FlowPointDTO.campaign(transaction.getCampaign()),
                    FlowPointDTO.customerWallet(owner));
            case EARNING -> new MoneyFlowDTO(FlowPointDTO.campaign(transaction.getCampaign()),
                    FlowPointDTO.creatorWallet(owner));
            case PAYOUT -> new MoneyFlowDTO(FlowPointDTO.creatorWallet(owner), FlowPointDTO.tron(tronAddress));
        };
    }
}
