package com.gaaiuswallet.app.viewmodel;

import static com.gaaiuswallet.token.tools.TokenDefinition.NO_SCRIPT;
import static com.gaaiuswallet.token.tools.TokenDefinition.UNCHANGED_SCRIPT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.router.SendTokenRouter;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.Erc1155AssetSelectActivity;
import com.gaaiuswallet.token.entity.SigReturnType;
import com.gaaiuswallet.token.entity.XMLDsigDescriptor;
import com.gaaiuswallet.token.tools.TokenDefinition;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;

@HiltViewModel
public class NFTViewModel extends BaseViewModel {
    private final MutableLiveData<XMLDsigDescriptor> sig = new MutableLiveData<>();
    private final MutableLiveData<Boolean> newScriptFound = new MutableLiveData<>();
    private final MutableLiveData<Token> tokenUpdate = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scriptUpdateInProgress = new MutableLiveData<>();
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final TokensService tokensService;
    private int startingBalance;

    @Nullable
    private Disposable nftBalanceCheck;
    @Nullable
    private Disposable nftCheckCycle;
    @Nullable
    private Disposable scriptUpdate;

    @Inject
    public NFTViewModel(FetchTransactionsInteract fetchTransactionsInteract,
                        AssetDefinitionService assetDefinitionService,
                        TokensService tokensService)
    {
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.tokensService = tokensService;
    }

    public LiveData<XMLDsigDescriptor> sig()
    {
        return sig;
    }

    public LiveData<Boolean> newScriptFound()
    {
        return newScriptFound;
    }

    public LiveData<Boolean> scriptUpdateInProgress() { return scriptUpdateInProgress; }

    public LiveData<Token> tokenUpdate()
    {
        return tokenUpdate;
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
        if (token != null)
        {
            disposable = assetDefinitionService.getSignatureData(token)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(sig::postValue, this::onSigCheckError);
        }
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
                .subscribe(this::handleFilename, e -> scriptUpdateInProgress.postValue(false));
    }

    private void handleFilename(TokenDefinition td)
    {
        switch (td.nameSpace)
        {
            case UNCHANGED_SCRIPT:
                newScriptFound.postValue(false);
                break;
            case NO_SCRIPT:
                scriptUpdateInProgress.postValue(false);
                break;
            default:
                newScriptFound.postValue(true);
                break;
        }
    }

    public void restartServices()
    {
        fetchTransactionsInteract.restartTransactionService();
    }

    public Intent openSelectionModeIntent(Context context, Token token, Wallet wallet)
    {
        Intent intent = new Intent(context, Erc1155AssetSelectActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.WALLET, wallet);
        return intent;
    }

    public void checkEventsForToken(final Token token)
    {
        if (token.isERC721() || token.getInterfaceSpec() == ContractType.ERC1155)
        {
            startingBalance = token.getTokenAssets() != null ? token.getTokenAssets().keySet().size() : 0;
            nftCheckCycle = Observable.interval(1, 10, TimeUnit.SECONDS)
                    .doOnNext(l -> doBalanceCheck(token)).subscribe();
        }
    }

    private void doBalanceCheck(Token token)
    {
        nftBalanceCheck = Single.fromCallable(() -> {
                    token.updateBalance(tokensService.getWalletRealmInstance());
                    return tokensService.getToken(token.tokenInfo.chainId, token.getAddress());
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::checkNFTUpdate, this::onError);
    }

    private void checkNFTUpdate(Token token)
    {
        //detect balance change in the tokenID list
        if (token.getTokenAssets() != null && token.getTokenAssets().keySet().size() != startingBalance)
        {
            //trigger view reset
            tokenUpdate.postValue(token);
            startingBalance = token.getTokenAssets().keySet().size();
        }
    }

    public void onDestroy()
    {
        if (nftCheckCycle != null && !nftCheckCycle.isDisposed()) nftCheckCycle.dispose();
        if (nftBalanceCheck != null && !nftBalanceCheck.isDisposed()) nftBalanceCheck.dispose();
        if (disposable != null && !disposable.isDisposed()) disposable.dispose();
        if (scriptUpdate != null && !scriptUpdate.isDisposed()) scriptUpdate.dispose();
    }

    public boolean hasTokenScript(Token token)
    {
        return token != null && assetDefinitionService.getAssetDefinition(token) != null;
    }

    public void updateAttributes(Token token)
    {
        assetDefinitionService.refreshAllAttributes(token)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(b -> { }, this::onError)
                .isDisposed();
    }
}
