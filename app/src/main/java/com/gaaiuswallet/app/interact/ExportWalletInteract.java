package com.gaaiuswallet.app.interact;

import android.util.Log;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.repository.WalletRepositoryType;
import com.gaaiuswallet.app.entity.Wallet;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import timber.log.Timber;

public class ExportWalletInteract {

    private final WalletRepositoryType walletRepository;

    public ExportWalletInteract(WalletRepositoryType walletRepository) {
        this.walletRepository = walletRepository;
    }

    public Single<String> export(Wallet wallet, String keystorePassword, String backupPassword) {
        Timber.tag("RealmDebug").d("export + %s", wallet.address);
        return walletRepository
                    .exportWallet(wallet, keystorePassword, backupPassword)
                .observeOn(AndroidSchedulers.mainThread());
    }
}
