package ru.trafficmarkering.service.wallet.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.model.wallet.WalletTransaction;
import ru.trafficmarkering.model.wallet.WalletTransactionStatus;
import ru.trafficmarkering.model.wallet.WalletTransactionType;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.GetterWalletTransaction;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.repository.SaverWalletTransaction;
import ru.trafficmarkering.service.wallet.WalletLedger;
import ru.trafficmarkering.util.MoneyUtil;
import ru.trafficmarkering.util.PublicIdGenerator;

@Service
@RequiredArgsConstructor
class WalletLedgerImpl implements WalletLedger {

    private final GetterWallet getterWallet;
    private final SaverWallet saverWallet;
    private final GetterWalletTransaction getterWalletTransaction;
    private final SaverWalletTransaction saverWalletTransaction;

    @Override
    public Wallet walletOf(User owner) {
        return getterWallet.getByUserId(owner.getId())
                .orElseGet(() -> saverWallet.save(Wallet.builder().user(owner).build()));
    }

    @Override
    public Wallet lockWallet(User owner) {
        return getterWallet.getByUserIdForUpdate(owner.getId())
                .orElseGet(() -> saverWallet.save(Wallet.builder().user(owner).build()));
    }

    @Override
    public WalletTransaction post(Wallet wallet,
                                  WalletTransactionType type,
                                  long signedAmountKopecks,
                                  WalletTransactionStatus status,
                                  Campaign campaign,
                                  User actor,
                                  String comment) {
        long next = apply(wallet, signedAmountKopecks);
        return saverWalletTransaction.save(WalletTransaction.builder()
                .publicId(PublicIdGenerator.generateUnique(getterWalletTransaction::existsByPublicId))
                .wallet(wallet)
                .type(type)
                .amountKopecks(signedAmountKopecks)
                .balanceAfterKopecks(next)
                .status(status)
                .campaign(campaign)
                .actor(actor)
                .comment(comment)
                .build());
    }

    @Override
    public WalletTransaction defer(Wallet wallet,
                                   WalletTransactionType type,
                                   long signedAmountKopecks,
                                   WalletTransactionStatus status,
                                   User actor) {
        return saverWalletTransaction.save(WalletTransaction.builder()
                .publicId(PublicIdGenerator.generateUnique(getterWalletTransaction::existsByPublicId))
                .wallet(wallet)
                .type(type)
                .amountKopecks(signedAmountKopecks)
                .balanceAfterKopecks(wallet.balance())
                .status(status)
                .actor(actor)
                .build());
    }

    @Override
    public void settle(WalletTransaction transaction, WalletTransactionStatus finalStatus) {
        Wallet wallet = lockWalletOf(transaction);
        transaction.setBalanceAfterKopecks(apply(wallet, transaction.amount()));
        transaction.setStatus(finalStatus);
        saverWalletTransaction.save(transaction);
    }

    @Override
    public void restore(WalletTransaction transaction, WalletTransactionStatus finalStatus) {
        if (!transaction.getStatus().countsTowardBalance()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Операция уже закрыта: " + transaction.getStatus().getDescription().toLowerCase());
        }
        Wallet wallet = lockWalletOf(transaction);
        long next = wallet.balance() - transaction.amount();
        if (next < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя отменить: деньги уже распределены, свободно " + MoneyUtil.formatRubles(wallet.balance())
                            + ", а вернуть нужно " + MoneyUtil.formatRubles(transaction.amount()));
        }
        wallet.setBalanceKopecks(next);
        saverWallet.save(wallet);
        transaction.setStatus(finalStatus);
        saverWalletTransaction.save(transaction);
    }

    private Wallet lockWalletOf(WalletTransaction transaction) {
        return getterWallet.getByIdForUpdate(transaction.getWallet().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Кошелёк не найден для операции " + transaction.getPublicId()));
    }

    private long apply(Wallet wallet, long signedAmountKopecks) {
        long next = wallet.balance() + signedAmountKopecks;
        if (next < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Свободных средств не хватает: доступно " + MoneyUtil.formatRubles(wallet.balance())
                            + ", нужно ещё " + MoneyUtil.formatRubles(-next));
        }
        wallet.setBalanceKopecks(next);
        saverWallet.save(wallet);
        return next;
    }
}
