package com.gaaiuswallet.app.interact;

import com.gaaiuswallet.app.repository.WalletRepositoryType;
import com.gaaiuswallet.app.entity.Wallet;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Delete and fetchTokens wallets
 */
public class DeleteWalletInteract {
	private final WalletRepositoryType walletRepository;

	public DeleteWalletInteract(WalletRepositoryType walletRepository) {
		this.walletRepository = walletRepository;
	}

	public Single<Wallet[]> delete(Wallet wallet)
	{
		return walletRepository.deleteWalletFromRealm(wallet)
				.flatMapCompletable(deletedWallet -> walletRepository.deleteWallet(deletedWallet.address, ""))
				.andThen(walletRepository.fetchWallets())
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread());
	}
}
