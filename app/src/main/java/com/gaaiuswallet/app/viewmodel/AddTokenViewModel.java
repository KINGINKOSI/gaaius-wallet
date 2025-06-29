package com.gaaiuswallet.app.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.entity.QRResult;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.tokens.TokenCardMeta;
import com.gaaiuswallet.app.entity.tokens.TokenInfo;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.ImportTokenActivity;
import com.gaaiuswallet.app.ui.SendActivity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@HiltViewModel
public class AddTokenViewModel extends BaseViewModel
{

    private final MutableLiveData<Wallet> wallet = new MutableLiveData<>();
    private final MutableLiveData<Long> switchNetwork = new MutableLiveData<>();
    private final MutableLiveData<Token> finalisedToken = new MutableLiveData<>();
    private final MutableLiveData<Token> tokenType = new MutableLiveData<>();
    private final MutableLiveData<Boolean> noContract = new MutableLiveData<>();
    private final MutableLiveData<Integer> scanCount = new MutableLiveData<>();

    private final MutableLiveData<Token> onToken = new MutableLiveData<>();
    private final MutableLiveData<Token[]> allTokens = new MutableLiveData<>();

    private final EthereumNetworkRepositoryType ethereumNetworkRepository;
    private final GenericWalletInteract genericWalletInteract;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final TokensService tokensService;

    private boolean foundNetwork;
    private int networkCount;
    private long primaryChainId = 1;
    private final List<Token> discoveredTokenList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public MutableLiveData<Wallet> wallet()
    {
        return wallet;
    }

    public MutableLiveData<Token> tokenType()
    {
        return tokenType;
    }

    public LiveData<Long> switchNetwork()
    {
        return switchNetwork;
    }

    public LiveData<Integer> chainScanCount()
    {
        return scanCount;
    }

    public LiveData<Token> onToken()
    {
        return onToken;
    }

    public LiveData<Token[]> allTokens()
    {
        return allTokens;
    }

    @Nullable
    Disposable scanNetworksDisposable;

    private final List<Disposable> scanThreads = new ArrayList<>();

    @Inject
    AddTokenViewModel(
            GenericWalletInteract genericWalletInteract,
            EthereumNetworkRepositoryType ethereumNetworkRepository,
            FetchTransactionsInteract fetchTransactionsInteract,
            AssetDefinitionService assetDefinitionService,
            TokensService tokensService)
    {
        this.genericWalletInteract = genericWalletInteract;
        this.ethereumNetworkRepository = ethereumNetworkRepository;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.tokensService = tokensService;
    }

    public void saveTokens(List<Token> toSave)
    {
        tokensService.addTokens(toSave);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();
        if (scanNetworksDisposable != null && !scanNetworksDisposable.isDisposed()) scanNetworksDisposable.dispose();
    }

    public void setPrimaryChain(long chainId)
    {
        primaryChainId = chainId;
    }

    public long getSelectedChain()
    {
        return primaryChainId;
    }

    private void checkType(Throwable throwable, long chainId, String address, ContractType type)
    {
        if (type == ContractType.ERC1155)
        {
            onTokensSetup(new TokenInfo(address, "Holding Contract", "", 0, true, chainId));
        }
        else
        {
            onError(throwable);
        }
    }

    public void fetchToken(long chainId, String addr)
    {
        tokensService.update(addr, chainId, ContractType.NOT_SET)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::gotTokenUpdate, this::onError).isDisposed();
    }

    private void gotTokenUpdate(TokenInfo tokenInfo)
    {
        disposable = tokensService.addToken(tokenInfo, wallet.getValue().address)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::resumeSend, this::onError);
    }

    private void resumeSend(Token token)
    {
        finalisedToken.postValue(token);
    }

    public NetworkInfo getNetworkInfo(long chainId)
    {
        return ethereumNetworkRepository.getNetworkByChain(chainId);
    }

    private void findWallet()
    {
        disposable = genericWalletInteract.find()
                .subscribe(wallet::setValue, this::onError);
    }

    private void onTokensSetup(TokenInfo info)
    {
        disposable = tokensService.addToken(info, wallet.getValue().address)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::finaliseToken, error -> tokenTypeError(error, info));
    }

    private void finaliseToken(Token token)
    {
        checkNetworkCount();
        discoveredTokenList.add(token);
        onToken.postValue(token);
    }

    private void tokenTypeError(Throwable throwable, TokenInfo data)
    {
        checkNetworkCount();
        Token badToken = new Token(data, BigDecimal.ZERO, 0, "", ContractType.NOT_SET);
        tokenType.postValue(badToken);
    }

    public void prepare()
    {
        findWallet();
    }

    public void showSend(Context ctx, QRResult result, Token token)
    {
        Intent intent = new Intent(ctx, SendActivity.class);
        boolean sendingTokens = (result.getFunction() != null && result.getFunction().length() > 0);
        String address = wallet.getValue().address;
        int decimals = 18;

        if (sendingTokens)
        {
            address = result.getAddress();
            decimals = token.tokenInfo.decimals;
        }

        intent.putExtra(C.EXTRA_SENDING_TOKENS, sendingTokens);
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address);
        intent.putExtra(C.EXTRA_NETWORKID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_SYMBOL, ethereumNetworkRepository.getNetworkByChain(result.chainId).symbol);
        intent.putExtra(C.EXTRA_DECIMALS, decimals);
        intent.putExtra(C.Key.WALLET, wallet.getValue());
        intent.putExtra(C.EXTRA_AMOUNT, result);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ctx.startActivity(intent);
    }

    private List<Long> getNetworkIds()
    {
        List<Long> networkIds = new ArrayList<>();
        networkIds.add(primaryChainId); //test selected chain first
        for (long chainId : tokensService.getNetworkFilters())
        {
            if (!networkIds.contains(chainId)) networkIds.add(chainId);
        }

        //Now scan unselected networks
        for (NetworkInfo networkInfo : ethereumNetworkRepository.getAvailableNetworkList())
        {
            if (!networkIds.contains(networkInfo.chainId)) networkIds.add(networkInfo.chainId);
        }

        return networkIds;
    }

    public void testNetworks(String address)
    {
        foundNetwork = false;
        discoveredTokenList.clear();
        networkCount = ethereumNetworkRepository.getAvailableNetworkList().length;
        scanCount.postValue(networkCount);

        ethereumNetworkRepository.getAllActiveNetworks();
        scanCount.postValue(networkCount);

        for (long networkId : getNetworkIds())
        {
            TokenInfo tokenInfo = new TokenInfo(address, "", "", 0, true, networkId);
            Disposable d = fetchTransactionsInteract.queryInterfaceSpec(tokenInfo)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(type -> testNetworkResult(tokenInfo, type), this::onTestError);

            scanThreads.add(d);
        }

        handler.postDelayed(this::stopScan, 60 * DateUtils.SECOND_IN_MILLIS);
    }

    private void testNetworkResult(final TokenInfo info, final ContractType type)
    {
        if (type != ContractType.OTHER)
        {
            foundNetwork = true;
            disposable = tokensService
                    .update(info.address, info.chainId, type)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onTokensSetup, error -> checkType(error, info.chainId, info.address, type));
        }
        else
        {
            checkNetworkCount();
        }
    }

    public void stopScan()
    {
        for (Disposable d : scanThreads)
        {
            if (!d.isDisposed()) d.dispose();
        }
        scanThreads.clear();
        scanCount.postValue(0);
        handler.removeCallbacksAndMessages(null);
    }

    private void onTestError(Throwable throwable)
    {
        checkNetworkCount();
        onError(throwable);
    }

    private void checkNetworkCount()
    {
        networkCount--;
        scanCount.postValue(networkCount);
        if (networkCount == 0 && !foundNetwork)
        {
            noContract.postValue(true);
        }
        if (networkCount == 0 && discoveredTokenList.size() > 0)
        {
            allTokens.postValue(discoveredTokenList.toArray(new Token[0]));
        }
    }

    public void showImportLink(Context context, String importTxt)
    {
        Intent intent = new Intent(context, ImportTokenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(C.IMPORT_STRING, importTxt);
        context.startActivity(intent);
    }

    public Token getToken(long chainId, String address)
    {
        return tokensService.getToken(chainId, address);
    }

    public TokensService getTokensService()
    {
        return tokensService;
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }

    public EthereumNetworkRepositoryType ethereumNetworkRepository()
    {
        return ethereumNetworkRepository;
    }

    public void selectExtraChains(List<Long> selectedChains)
    {
        //add new chains to chain selection
        //get current list and add it on
        HashSet<Long> uniqueList = new HashSet<>(selectedChains);
        uniqueList.addAll(ethereumNetworkRepository.getFilterNetworkList());
        ethereumNetworkRepository.setFilterNetworkList(uniqueList.toArray(new Long[0]));
        ethereumNetworkRepository.commitPrefs();
        tokensService.setupFilter(true);
    }

    /**
     * Set all selected tokens enabled and visible.
     * Note that we need to update the 'visibility changed' setting to mark the token as having explicitly been set visible.
     * @param selected list of selected TCMs
     */
    public void markTokensEnabled(List<TokenCardMeta> selected)
    {
        if (wallet.getValue() == null) return;
        Observable.fromIterable(selected)
                .forEach(tcm -> tokensService.enableToken(wallet.getValue().address, tcm.getContractAddress())
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe())
                .isDisposed();
    }
}
