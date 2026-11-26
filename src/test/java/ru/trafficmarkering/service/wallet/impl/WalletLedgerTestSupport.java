package ru.trafficmarkering.service.wallet.impl;

import ru.trafficmarkering.repository.GetterTransfer;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.wallet.OperationReader;
import ru.trafficmarkering.service.wallet.WalletLedger;

public final class WalletLedgerTestSupport {

    private WalletLedgerTestSupport() {
    }

    public static WalletLedger ledger(GetterWallet getterWallet,
                                      SaverWallet saverWallet,
                                      GetterWalletTransaction getterWalletTransaction,
                                      SaverWalletTransaction saverWalletTransaction) {
        return new WalletLedgerImpl(getterWallet, saverWallet, getterWalletTransaction, saverWalletTransaction);
    }

    public static OperationReader reader(GetterTransfer getterTransfer, FileStorage fileStorage) {
        return new OperationReaderImpl(getterTransfer, fileStorage);
    }
}
