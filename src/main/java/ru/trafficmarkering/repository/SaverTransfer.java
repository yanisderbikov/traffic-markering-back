package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.wallet.Transfer;

public interface SaverTransfer {
    Transfer save(Transfer transfer);
}
