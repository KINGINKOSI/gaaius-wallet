package com.gaaiuswallet.app.viewmodel;


import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.api.v1.entity.response.ConnectResponse;
import com.gaaiuswallet.app.api.v1.entity.response.SignPersonalMessageResponse;
import com.gaaiuswallet.app.entity.ErrorEnvelope;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.hardware.SignatureFromKey;
import com.gaaiuswallet.hardware.SignatureReturnType;
import com.gaaiuswallet.app.interact.CreateTransactionInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.token.entity.Signable;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@HiltViewModel
public class ApiV1ViewModel extends BaseViewModel
{
    private final GenericWalletInteract genericWalletInteract;
    private final CreateTransactionInteract createTransactionInteract;
    private final KeyService keyService;

    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private final MutableLiveData<byte[]> signature = new MutableLiveData<>();

    protected Disposable disposable;

    @Inject
    public ApiV1ViewModel(GenericWalletInteract genericWalletInteract,
                          CreateTransactionInteract createTransactionInteract,
                          KeyService keyService)
    {
        this.genericWalletInteract = genericWalletInteract;
        this.createTransactionInteract = createTransactionInteract;
        this.keyService = keyService;
    }

    public LiveData<Wallet> defaultWallet()
    {
        return defaultWallet;
    }

    public LiveData<byte[]> signature()
    {
        return signature;
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

    public void signMessage(Signable message)
    {
        disposable = createTransactionInteract.sign(defaultWallet.getValue(), message)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSignSuccess, this::onSignError);
    }

    private void onSignSuccess(SignatureFromKey sig)
    {
        if (sig.sigType == SignatureReturnType.SIGNATURE_GENERATED)
        {
            signature.postValue(sig.signature);
        }
        else if (sig.sigType != SignatureReturnType.SIGNING_POSTPONED)
        {
            if (TextUtils.isEmpty(sig.failMessage))
            {
                onSignError(new Throwable(sig.sigType.name()));
            }
            else
            {
                onSignError(new Throwable(sig.failMessage));
            }
        }
    }

    private void onSignError(Throwable t)
    {
        error.postValue(new ErrorEnvelope(t.getMessage()));
    }

    public void getAuthentication(Activity activity, SignAuthenticationCallback callback)
    {
        genericWalletInteract.find()
                .subscribe(wallet -> keyService.getAuthenticationForSignature(wallet, activity, callback))
                .isDisposed();
    }

    public Uri buildConnectResponse(String redirectUrl, String address)
    {
        return new ConnectResponse(redirectUrl, address).uri();
    }

    public Uri buildSignPersonalMessageResponse(String redirectUrl, String signature)
    {
        return new SignPersonalMessageResponse(redirectUrl, signature).uri();
    }

    public boolean addressMatches(String address, String requestAddress)
    {
        return address.equalsIgnoreCase(requestAddress);
    }
}
