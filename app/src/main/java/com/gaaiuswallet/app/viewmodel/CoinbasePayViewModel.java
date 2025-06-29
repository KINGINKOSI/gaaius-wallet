package com.gaaiuswallet.app.viewmodel;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.coinbasepay.DestinationWallet;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.repository.CoinbasePayRepositoryType;
import com.gaaiuswallet.app.service.AnalyticsServiceType;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.disposables.Disposable;

@HiltViewModel
public class CoinbasePayViewModel extends BaseViewModel
{
    private final GenericWalletInteract genericWalletInteract;
    private final CoinbasePayRepositoryType coinbasePayRepository;
    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private final MutableLiveData<byte[]> signature = new MutableLiveData<>();

    protected Disposable disposable;

    @Inject
    public CoinbasePayViewModel(GenericWalletInteract genericWalletInteract,
                                CoinbasePayRepositoryType coinbasePayRepository,
                                AnalyticsServiceType analyticsService)
    {
        this.genericWalletInteract = genericWalletInteract;
        this.coinbasePayRepository = coinbasePayRepository;
        setAnalyticsService(analyticsService);
    }

    public LiveData<Wallet> defaultWallet()
    {
        return defaultWallet;
    }

    public void prepare()
    {
        progress.postValue(false);
        disposable = genericWalletInteract
                .find()
                .subscribe(this::onDefaultWallet, this::onError);
    }

    private void onDefaultWallet(final Wallet wallet)
    {
        defaultWallet.setValue(wallet);
    }

    public String getUri(DestinationWallet.Type type, String address, List<String> list)
    {
        return coinbasePayRepository.getUri(type, address, list);
    }
}
