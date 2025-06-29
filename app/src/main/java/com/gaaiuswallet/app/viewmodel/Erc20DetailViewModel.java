package com.gaaiuswallet.app.viewmodel;

import static com.gaaiuswallet.token.tools.TokenDefinition.NO_SCRIPT;
import static com.gaaiuswallet.token.tools.TokenDefinition.UNCHANGED_SCRIPT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.ActivityMeta;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.repository.OnRampRepositoryType;
import com.gaaiuswallet.app.router.MyAddressRouter;
import com.gaaiuswallet.app.router.SendTokenRouter;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.token.entity.SigReturnType;
import com.gaaiuswallet.token.entity.XMLDsigDescriptor;
import com.gaaiuswallet.token.tools.TokenDefinition;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;

@HiltViewModel
public class Erc20DetailViewModel extends BaseViewModel {
    private final MutableLiveData<XMLDsigDescriptor> sig = new MutableLiveData<>();
    private final MutableLiveData<TokenDefinition> newScriptFound = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scriptUpdateInProgress = new MutableLiveData<>();

    private final MyAddressRouter myAddressRouter;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final TokensService tokensService;
    private final OnRampRepositoryType onRampRepository;

    @Nullable
    private Disposable scriptUpdate;

    @Inject
    public Erc20DetailViewModel(MyAddressRouter myAddressRouter,
                                FetchTransactionsInteract fetchTransactionsInteract,
                                AssetDefinitionService assetDefinitionService,
                                TokensService tokensService,
                                OnRampRepositoryType onRampRepository,
                                AnalyticsServiceType analyticsService)
    {
        this.myAddressRouter = myAddressRouter;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.tokensService = tokensService;
        this.onRampRepository = onRampRepository;
        setAnalyticsService(analyticsService);
    }

    public LiveData<XMLDsigDescriptor> sig()
    {
        return sig;
    }

    public LiveData<TokenDefinition> newScriptFound()
    {
        return newScriptFound;
    }

    public LiveData<Boolean> scriptUpdateInProgress() { return scriptUpdateInProgress; }

    public void showMyAddress(Context context, Wallet wallet, Token token)
    {
        myAddressRouter.open(context, wallet, token);
    }

    public void showContractInfo(Context ctx, Wallet wallet, Token token)
    {
        myAddressRouter.open(ctx, wallet, token);
    }

    public TokensService getTokensService()
    {
        return tokensService;
    }

    public FetchTransactionsInteract getTransactionsInteract()
    {
        return fetchTransactionsInteract;
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return this.assetDefinitionService;
    }

    public void showSendToken(Activity act, Wallet wallet, Token token)
    {
        if (token != null)
        {
            new SendTokenRouter().open(act, wallet.address, token.getSymbol(), token.tokenInfo.decimals,
                    wallet, token, token.tokenInfo.chainId);
        }
    }

    public Realm getRealmInstance(Wallet wallet)
    {
        return tokensService.getRealmInstance(wallet);
    }

    public void checkTokenScriptValidity(Token token)
    {
        disposable = assetDefinitionService.getSignatureData(token)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sig::postValue, this::onSigCheckError);
    }

    private void onSigCheckError(Throwable throwable)
    {
        XMLDsigDescriptor failSig = new XMLDsigDescriptor();
        failSig.result = "fail";
        failSig.type = SigReturnType.NO_TOKENSCRIPT;
        failSig.subject = throwable.getMessage();
        sig.postValue(failSig);
    }

    public void checkForNewScript(Token token)
    {
        //check server for new tokenscript
        scriptUpdate = assetDefinitionService.checkServerForScript(token, scriptUpdateInProgress)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.single())
                .subscribe(this::handleDefinition, e -> scriptUpdateInProgress.postValue(false));
    }

    private void handleDefinition(TokenDefinition td)
    {
        switch (td.nameSpace)
        {
            case UNCHANGED_SCRIPT:
                td.nameSpace = UNCHANGED_SCRIPT;
                newScriptFound.postValue(td);
                break;
            case NO_SCRIPT:
                scriptUpdateInProgress.postValue(false);
                break;
            default:
                newScriptFound.postValue(td);
                break;
        }
    }

    public void restartServices()
    {
        fetchTransactionsInteract.restartTransactionService();
    }

    public Intent getBuyIntent(String address, Token token)
    {
        Intent intent = new Intent();
        intent.putExtra(C.DAPP_URL_LOAD, onRampRepository.getUri(address, token));
        return intent;
    }

    public OnRampRepositoryType getOnRampRepository() {
        return onRampRepository;
    }

    @Override
    public void onCleared()
    {
        super.onCleared();
        if (scriptUpdate != null && !scriptUpdate.isDisposed())
        {
            scriptUpdate.dispose();
        }
    }
}
