package com.gaaiuswallet.app.service;

import static com.gaaiuswallet.app.repository.TokensRealmSource.databaseKey;
import static com.gaaiuswallet.ethereum.EthereumNetworkBase.KLAYTN_ID;
import static com.gaaiuswallet.ethereum.EthereumNetworkBase.MAINNET_ID;

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.entity.ContractLocator;
import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.CustomViewSettings;
import com.gaaiuswallet.app.entity.ImageEntry;
import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.entity.ServiceSyncCallback;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.okx.OkTokenCheck;
import com.gaaiuswallet.app.entity.tokendata.TokenGroup;
import com.gaaiuswallet.app.entity.tokendata.TokenTicker;
import com.gaaiuswallet.app.entity.tokendata.TokenUpdateType;
import com.gaaiuswallet.app.entity.okx.OkProtocolType;
import com.gaaiuswallet.app.entity.okx.OkToken;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.tokens.TokenCardMeta;
import com.gaaiuswallet.app.entity.tokens.TokenFactory;
import com.gaaiuswallet.app.entity.tokens.TokenInfo;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.token.entity.ContractAddress;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import okhttp3.OkHttpClient;
import timber.log.Timber;

public class TokensService
{
    private static final String TAG = "TOKENSSERVICE";
    public static final String UNKNOWN_CONTRACT = "[Unknown Contract]";
    public static final String EXPIRED_CONTRACT = "[Expired Contract]";
    public static final long PENDING_TIME_LIMIT = 3*DateUtils.MINUTE_IN_MILLIS; //cut off pending chain after 3 minutes

    private static final Map<Long, Long> pendingChainMap = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Token> tokenStoreList = new ConcurrentLinkedDeque<>(); //used to hold tokens that will be stored
    private final Map<String, Long> pendingTokenMap = new ConcurrentHashMap<>(); //used to determine which token to update next
    private String currentAddress = null;
    private final EthereumNetworkRepositoryType ethereumNetworkRepository;
    private final TokenRepositoryType tokenRepository;
    private final TickerService tickerService;
    private final OpenSeaService openseaService;
    private final OkHttpClient httpClient;
    private final AnalyticsServiceType<AnalyticsProperties> analyticsService;
    private final List<Long> networkFilter;
    private ContractLocator focusToken;
    private final ConcurrentLinkedDeque<ContractAddress> unknownTokens;
    private final ConcurrentLinkedQueue<Long> baseTokenCheck;
    private final ConcurrentLinkedQueue<ImageEntry> imagesForWrite;
    private final ConcurrentLinkedQueue<OkTokenCheck> chainCheckList;
    private long openSeaCheckId;
    private boolean appHasFocus;
    private static boolean walletStartup = false;
    private long transferCheckChain;
    private final TokenFactory tokenFactory = new TokenFactory();
    private long syncTimer;
    private long syncStart;
    private ServiceSyncCallback completionCallback;
    private int syncCount = 0;

    @Nullable
    private Disposable eventTimer;
    @Nullable
    private Disposable checkUnknownTokenCycle;
    @Nullable
    private Disposable queryUnknownTokensDisposable;
    @Nullable
    private Disposable balanceCheckDisposable;
    @Nullable
    private Disposable erc20CheckDisposable;
    @Nullable
    private Disposable tokenStoreDisposable;
    @Nullable
    private Disposable openSeaQueryDisposable;
    @Nullable
    private Disposable imageWriter;
    @Nullable
    private Disposable okDisposable;

    private static boolean done = false;

    public TokensService(EthereumNetworkRepositoryType ethereumNetworkRepository,
                         TokenRepositoryType tokenRepository,
                         TickerService tickerService,
                         OpenSeaService openseaService,
                         AnalyticsServiceType<AnalyticsProperties> analyticsService,
                         OkHttpClient httpClient) {
        this.ethereumNetworkRepository = ethereumNetworkRepository;
        this.tokenRepository = tokenRepository;
        this.tickerService = tickerService;
        this.openseaService = openseaService;
        this.analyticsService = analyticsService;
        networkFilter = new ArrayList<>();
        setupFilter(ethereumNetworkRepository.hasSetNetworkFilters());
        focusToken = null;
        this.unknownTokens = new ConcurrentLinkedDeque<>();
        this.baseTokenCheck = new ConcurrentLinkedQueue<>();
        this.imagesForWrite = new ConcurrentLinkedQueue<>();
        this.chainCheckList = new ConcurrentLinkedQueue<>();
        this.httpClient = httpClient;
        setCurrentAddress(ethereumNetworkRepository.getCurrentWalletAddress()); //set current wallet address at service startup
        appHasFocus = true;
        transferCheckChain = 0;
        completionCallback = null;
    }

    private void checkUnknownTokens()
    {
        if (queryUnknownTokensDisposable == null || queryUnknownTokensDisposable.isDisposed())
        {
            ContractAddress t = unknownTokens.pollFirst();
            Token cachedToken = t != null ? getToken(t.chainId, t.address) : null;

            if (t != null && !t.address.isEmpty() && (cachedToken == null || TextUtils.isEmpty(cachedToken.tokenInfo.name)))
            {
                ContractType type = tokenRepository.determineCommonType(new TokenInfo(t.address, "", "", 18, false, t.chainId)).blockingGet();

                queryUnknownTokensDisposable = tokenRepository.update(t.address, t.chainId, type) //fetch tokenInfo
                        .map(tokenInfo -> tokenFactory.createToken(tokenInfo, type, ethereumNetworkRepository.getNetworkByChain(t.chainId).getShortName()))
                        .flatMap(token -> tokenRepository.updateTokenBalance(currentAddress, token))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe(this::finishAddToken, err -> onCheckError(err, t));
            }
            else if (t == null)
            {
                //stop the check
                if (checkUnknownTokenCycle != null && !checkUnknownTokenCycle.isDisposed())
                    checkUnknownTokenCycle.dispose();
            }
        }
    }

    private void onCheckError(Throwable throwable, ContractAddress t)
    {
        Timber.e(throwable);
    }

    private void finishAddToken(BigDecimal balance)
    {
        queryUnknownTokensDisposable = null;
    }

    public Token getToken(long chainId, String addr)
    {
        if (TextUtils.isEmpty(currentAddress) || TextUtils.isEmpty(addr)) return null;
        else return tokenRepository.fetchToken(chainId, currentAddress, addr.toLowerCase());
    }

    public void storeToken(Token token)
    {
        if (TextUtils.isEmpty(currentAddress) || token == null || token.getInterfaceSpec() == ContractType.OTHER) return;
        tokenStoreDisposable = tokenRepository.checkInterface(token, new Wallet(token.getWallet()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tokenStoreList::add, this::onERC20Error);
    }

    public TokenTicker getTokenTicker(Token token)
    {
        return tokenRepository.getTokenTicker(token);
    }

    public Single<TokenCardMeta[]> getAllTokenMetas(String searchString)
    {
        return tokenRepository.fetchAllTokenMetas(new Wallet(currentAddress), networkFilter, searchString);
    }

    public List<Token> getAllAtAddress(String addr)
    {
        List<Token> tokens = new ArrayList<>();
        if (addr == null) return tokens;
        for (long chainId : networkFilter)
        {
            tokens.add(getToken(chainId, addr));
        }

        return tokens;
    }

    public void setCurrentAddress(String newWalletAddr)
    {
        if (newWalletAddr != null && (currentAddress == null || !currentAddress.equalsIgnoreCase(newWalletAddr)))
        {
            currentAddress = newWalletAddr.toLowerCase();
            stopUpdateCycle();
            addLockedTokens();
            if (openseaService != null) openseaService.resetOffsetRead(networkFilter);
            tokenRepository.updateLocalAddress(newWalletAddr);
            lastStartCycleTime = 0;
        }
    }

    private void updateCycle(boolean val)
    {
        syncStart = System.currentTimeMillis();
        syncTimer = syncStart + 5*DateUtils.SECOND_IN_MILLIS;

        eventTimer = Observable.interval(1, 500, TimeUnit.MILLISECONDS)
                .doOnNext(l -> checkTokensBalance())
                .observeOn(Schedulers.newThread()).subscribe();
    }

    private long lastStartCycleTime = 0;

    public void restartUpdateCycle()
    {
        stopUpdateCycle();
        lastStartCycleTime = 0;
        startUpdateCycle();
    }

    public void startUpdateCycleIfRequired()
    {
        if (eventTimer == null || eventTimer.isDisposed())
        {
            startUpdateCycle();
        }
    }

    public void startUpdateCycle()
    {
        if ((eventTimer != null && !eventTimer.isDisposed() && (lastStartCycleTime + 10000) > System.currentTimeMillis())
                || (lastStartCycleTime + 2000) > System.currentTimeMillis())
        {
            return; // Block this refresh - we need to ensure the cycle restarts but within 1 second no need to restart
        }
        else
        {
            lastStartCycleTime = System.currentTimeMillis();
        }

        if (!Utils.isAddressValid(currentAddress))
        {
            return;
        }

        stopUpdateCycle();

        syncCount = 0;

        setupFilters();

        eventTimer = Single.fromCallable(() -> {
            startupPass();
            checkIssueTokens();
            pendingTokenMap.clear();
            checkTokensOnOKx();
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateCycle, this::onError);
    }

    // Constructs a map of tokens requiring update
    private TokenCardMeta[] buildUpdateMap()
    {
        int unSynced = 0;
        TokenCardMeta[] tokenList = tokenRepository.fetchTokenMetasForUpdate(new Wallet(currentAddress), networkFilter);
        for (TokenCardMeta meta : tokenList)
        {
            meta.lastTxUpdate = meta.lastUpdate;
            String key = databaseKey(meta.getChain(), meta.getAddress());
            if (!pendingTokenMap.containsKey(key))
            {
                if (meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM) unSynced++;
                pendingTokenMap.put(key, meta.lastUpdate);
            }
            else if (meta.lastUpdate <= pendingTokenMap.get(key))
            {
                meta.lastUpdate = pendingTokenMap.get(key);
                if ((meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM)
                        && meta.lastUpdate < syncStart && meta.isEnabled && meta.hasValidName()) { unSynced++; }
            }
            else if ((meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM)
                    && meta.lastUpdate < syncStart && meta.isEnabled && meta.hasValidName()) { unSynced++; }
        }

        checkSyncStatus(unSynced, tokenList);

        return tokenList;
    }

    private void checkSyncStatus(int unSynced, TokenCardMeta[] tokenList)
    {
        if (syncTimer > 0 && System.currentTimeMillis() > syncTimer)
        {
            if (unSynced > 0)
            {
                syncTimer = System.currentTimeMillis() + 5*DateUtils.SECOND_IN_MILLIS;
            }
            else
            {
                syncTimer = 0;
                //sync chain tickers
            }

            syncChainTickers(tokenList, 0);
        }
    }

    private boolean syncERC20Tickers(final int chainIndex, final long chainId, final TokenCardMeta[] tokenList)
    {
        List<TokenCardMeta> erc20OnChain = getERC20OnChain(chainId, tokenList);
        if (!erc20OnChain.isEmpty())
        {
            tickerService.syncERC20Tickers(chainId, erc20OnChain)
                    .subscribeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(v -> syncChainTickers(tokenList, chainIndex+1), this::onERC20Error)
                    .isDisposed();
            return true;
        }
        else
        {
            return false;
        }
    }

    private List<TokenCardMeta> getERC20OnChain(long chainId, TokenCardMeta[] tokenList)
    {
        List<TokenCardMeta> allERC20 = new ArrayList<>();
        for (TokenCardMeta tcm : tokenList)
        {
            if (tcm.type == ContractType.ERC20 && tcm.hasPositiveBalance() && tcm.getChain() == chainId)
            {
                allERC20.add(tcm);
            }
        }
        return allERC20;
    }

    private void syncChainTickers(TokenCardMeta[] tokenList, int chainIndex)
    {
        //go through all mainnet chains
        NetworkInfo[] networks = ethereumNetworkRepository.getAvailableNetworkList();

        for (int i = chainIndex; i < networks.length; i++)
        {
            NetworkInfo info = networks[i];
            if (info.hasRealValue() && syncERC20Tickers(i, info.chainId, tokenList)) return;
        }

        //complete
        if (completionCallback != null)
        {
            completionCallback.syncComplete(this, syncCount);
        }
    }

    /**
     * Creates and stores an enabled basechain Token
     * @param chainId
     * @return
     */
    public Single<Token[]> createBaseToken(long chainId)
    {
        Token[] tok = new Token[1];
        tok[0] = tokenRepository.fetchToken(chainId, currentAddress, currentAddress);
        if (!networkFilter.contains(chainId)) //add chain to filter list
        {
            networkFilter.add(chainId);
            ethereumNetworkRepository.setFilterNetworkList(networkFilter.toArray(new Long[0]));
        }

        return tokenRepository.storeTokens(new Wallet(currentAddress), tok);
    }

    public void stopUpdateCycle()
    {
        if (eventTimer != null && !eventTimer.isDisposed())
        {
            eventTimer.dispose();
            eventTimer = null;
        }

        if (balanceCheckDisposable != null && !balanceCheckDisposable.isDisposed()) { balanceCheckDisposable.dispose(); }
        if (erc20CheckDisposable != null && !erc20CheckDisposable.isDisposed()) { erc20CheckDisposable.dispose(); }
        if (tokenStoreDisposable != null && !tokenStoreDisposable.isDisposed()) { tokenStoreDisposable.dispose(); }
        if (openSeaQueryDisposable != null && !openSeaQueryDisposable.isDisposed()) { openSeaQueryDisposable.dispose(); }
        if (checkUnknownTokenCycle != null && !checkUnknownTokenCycle.isDisposed()) { checkUnknownTokenCycle.dispose(); }
        if (queryUnknownTokensDisposable != null && !queryUnknownTokensDisposable.isDisposed()) { queryUnknownTokensDisposable.dispose(); }
        if (openSeaQueryDisposable != null && !openSeaQueryDisposable.isDisposed()) { openSeaQueryDisposable.dispose(); }
        if (okDisposable != null && !okDisposable.isDisposed()) { okDisposable.dispose(); }

        pendingChainMap.clear();
        tokenStoreList.clear();
        baseTokenCheck.clear();
        pendingTokenMap.clear();
        unknownTokens.clear();
        chainCheckList.clear();
    }

    public String getCurrentAddress() { return currentAddress; }

    public static void setWalletStartup() { walletStartup = true; }

    public void setupFilter(boolean userUpdated)
    {
        networkFilter.clear();
        if (!CustomViewSettings.getLockedChains().isEmpty())
        {
            networkFilter.addAll(CustomViewSettings.getLockedChains());
        }
        else
        {
            networkFilter.addAll(ethereumNetworkRepository.getFilterNetworkList());
        }

        if (userUpdated) ethereumNetworkRepository.setHasSetNetworkFilters();
    }

    public void setFocusToken(@NotNull Token token)
    {
        focusToken = new ContractLocator(token.getAddress(), token.tokenInfo.chainId);
    }

    public void clearFocusToken()
    {
        focusToken = null;
    }

    public void onWalletRefreshSwipe()
    {
        openseaService.resetOffsetRead(networkFilter);
    }

    private boolean isFocusToken(Token t)
    {
        return focusToken != null && focusToken.equals(t);
    }

    private boolean isFocusToken(TokenCardMeta t)
    {
        return focusToken != null && focusToken.equals(t);
    }

    /**
     * This method will add unknown token to the list and discover it
     * @param cAddr Contract Address
     */
    public void addUnknownTokenToCheck(ContractAddress cAddr)
    {
        for (ContractAddress check : unknownTokens)
        {
            if (check.chainId == cAddr.chainId && check.address.equalsIgnoreCase(cAddr.address))
            {
                return;
            }
        }

        if (getToken(cAddr.chainId, cAddr.address) == null)
        {
            unknownTokens.addLast(cAddr);
            startUnknownCheck();
        }
    }

    public void addUnknownTokenToCheckPriority(ContractAddress cAddr)
    {
        for (ContractAddress check : unknownTokens)
        {
            if (check.chainId == cAddr.chainId && (check.address == null || check.address.equalsIgnoreCase(cAddr.address)))
            {
                return;
            }
        }

        if (getToken(cAddr.chainId, cAddr.address) == null)
        {
            unknownTokens.addFirst(cAddr);
            startUnknownCheck();
        }
    }

    private void startUnknownCheck()
    {
        if (checkUnknownTokenCycle == null || checkUnknownTokenCycle.isDisposed())
        {
            checkUnknownTokenCycle = Observable.interval(1000, 500, TimeUnit.MILLISECONDS)
                    .doOnNext(l -> checkUnknownTokens()).subscribe();
        }
    }

    private void startImageWrite()
    {
        if (imageWriter == null || imageWriter.isDisposed())
        {
            imageWriter = Observable.interval(500, 500, TimeUnit.MILLISECONDS)
                    .doOnNext(l -> writeImages()).subscribe();
        }
    }

    private void writeImages()
    {
        if (imagesForWrite.isEmpty())
        {
            imageWriter.dispose();
            imageWriter = null;
        }
        else
        {
            Single.fromCallable(() -> {
                        List<ImageEntry> entries = new ArrayList<>(imagesForWrite);
                        imagesForWrite.clear();
                        tokenRepository.addImageUrl(entries);
                        return "";
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io()).subscribe().isDisposed();
        }
    }

    private void checkTokensOnOKx()
    {
        if (httpClient == null)
        {
            return;
        }
        //refresh check list
        //get list of current chains we need to check
        chainCheckList.clear();
        for (long chainId : networkFilter)
        {
            if (OkLinkService.supportsChain(chainId))
            {
                chainCheckList.add(new OkTokenCheck(chainId, OkProtocolType.ERC_20));
                chainCheckList.add(new OkTokenCheck(chainId, OkProtocolType.ERC_721));
                chainCheckList.add(new OkTokenCheck(chainId, OkProtocolType.ERC_1155));
            }
        }

        if (okDisposable == null || okDisposable.isDisposed())
        {
            okDisposable = Observable.interval(2000, 1000, TimeUnit.MILLISECONDS)
                    .doOnNext(l -> checkChainOnOkx()).subscribe();
        }
    }

    private void checkChainOnOkx()
    {
        if (chainCheckList.isEmpty())
        {
            okDisposable.dispose();
            okDisposable = null;
            return;
        }

        //perform check & update tokens
        OkTokenCheck thisCheck = chainCheckList.poll();
        if (thisCheck != null)
        {
            checkOkTokens(thisCheck.chainId, thisCheck.type);
        }
    }

    private void startupPass()
    {
        if (!walletStartup) return;

        walletStartup = false;

        //one time pass over tokens with a null name
        tokenRepository.fetchAllTokensWithBlankName(currentAddress, networkFilter)
                .map(contractAddrs -> {
                    Collections.addAll(unknownTokens, contractAddrs);
                    startUnknownCheck();
                    return 1;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe()
                .isDisposed();
    }

    public List<Long> getNetworkFilters()
    {
        return networkFilter;
    }

    public String getNetworkName(long chainId)
    {
        NetworkInfo info = ethereumNetworkRepository.getNetworkByChain(chainId);
        if (info != null) return info.getShortName();
        else return "";
    }

    public String getNetworkSymbol(long chainId)
    {
        NetworkInfo info = ethereumNetworkRepository.getNetworkByChain(chainId);
        if (info == null) { info = ethereumNetworkRepository.getNetworkByChain(MAINNET_ID); }
        return info.symbol;
    }

    //Add to write queue
    public void addTokenImageUrl(long networkId, String address, String imageUrl)
    {
        ImageEntry entry = new ImageEntry(networkId, address, imageUrl);
        imagesForWrite.add(entry);
        startImageWrite();
    }

    public Single<TokenInfo> update(String address, long chainId, ContractType type)
    {
        return tokenRepository.update(address, chainId, type);
    }

    private void checkIssueTokens()
    {
        if (openseaService == null) return;
        tokenRepository.fetchTokensThatMayNeedUpdating(currentAddress, networkFilter)
                .map(tokens -> {
                    for (Token t : tokens)
                    {
                        storeToken(t);
                    }
                    return tokens;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
                .isDisposed();
    }

    private void checkTokensBalance()
    {
        final Token t = getNextInBalanceUpdateQueue();

        if (t != null)
        {
            Timber.tag(TAG).d("Updating: " + t.tokenInfo.chainId + (t.isEthereum() ? " (Base Chain) ":"") + " : " + t.getAddress() + " : " + t.getFullName());
            balanceCheckDisposable = tokenRepository.updateTokenBalance(currentAddress, t)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(newBalance -> onBalanceChange(newBalance, t), this::onError);
        }

        checkPendingChains();
    }

    public Single<BigDecimal> getChainBalance(String walletAddress, long chainId)
    {
        return tokenRepository.fetchChainBalance(walletAddress, chainId);
    }

    public Single<TokenInfo> storeTokenInfo(Wallet wallet, TokenInfo tInfo, ContractType type)
    {
        return tokenRepository.determineCommonType(tInfo)
                .map(contractType -> checkDefaultType(contractType, type))
                        .flatMap(contractType -> tokenRepository.storeTokenInfo(wallet, tInfo, type));
    }

    public Single<TokenInfo> storeTokenInfoDirect(Wallet wallet, TokenInfo tInfo, ContractType type)
    {
        return tokenRepository.storeTokenInfo(wallet, tInfo, type);
    }

    //Fix undermined contract type
    private ContractType checkDefaultType(ContractType contractType, ContractType defaultType)
    {
        switch (contractType)
        {
            case OTHER:
            case ERC721_UNDETERMINED:
                return defaultType;
            default:
                return contractType;
        }
    }

    // Note that this routine works across different wallets, so there's no usage of currentAddress
    public Single<Token[]> syncChainBalances(String walletAddress, TokenUpdateType updateType)
    {
        //update all chain balances
        return Single.fromCallable(() -> {
            List<Token> baseTokens = new ArrayList<>();
            for (long chainId : networkFilter)
            {
                Token baseToken = tokenRepository.fetchToken(chainId, walletAddress, walletAddress);
                if (baseToken == null)
                {
                    baseToken = ethereumNetworkRepository.getBlankOverrideToken(ethereumNetworkRepository.getNetworkByChain(chainId));
                }
                baseToken.setTokenWallet(walletAddress);
                BigDecimal balance = baseToken.balance;
                if (updateType == TokenUpdateType.ACTIVE_SYNC) balance = tokenRepository.updateTokenBalance(walletAddress, baseToken).blockingGet();
                if (balance.compareTo(BigDecimal.ZERO) > 0)
                {
                    baseToken.balance = balance;
                    baseTokens.add(baseToken);
                }
            }

            return baseTokens.toArray(new Token[0]);
        });
    }

    private void onBalanceChange(BigDecimal newBalance, Token t)
    {
        boolean balanceChange = !newBalance.equals(t.balance);

        if (newBalance.equals(BigDecimal.valueOf(-2)))
        {
            //token deleted
            return;
        }

        if (balanceChange && BuildConfig.DEBUG)
        {
            Timber.tag(TAG).d("Change Registered: * %s", t.getFullName());
        }

        //update check time
        pendingTokenMap.put(databaseKey(t), System.currentTimeMillis());

        //Switch this token chain on
        if (t.isEthereum() && newBalance.compareTo(BigDecimal.ZERO) > 0)
        {
            checkChainVisibility(t);
            if (syncCount == 0 && completionCallback != null) { completionCallback.syncComplete(this, -1); }
        }

        if (t.isEthereum())
        {
            checkERC20(t.tokenInfo.chainId);
        }

        checkOpenSea(t.tokenInfo.chainId);
    }

    private void checkChainVisibility(Token t)
    {
        //Switch this token chain on
        if (!networkFilter.contains(t.tokenInfo.chainId) && EthereumNetworkRepository.hasRealValue(t.tokenInfo.chainId))
        {
            Timber.tag(TAG).d("Detected balance");
            //activate this filter
            networkFilter.add(t.tokenInfo.chainId);
            //now update the default filters
            ethereumNetworkRepository.setFilterNetworkList(networkFilter.toArray(new Long[0]));
        }
    }

    private void checkPendingChains()
    {
        long currentTime = System.currentTimeMillis();
        for (Long chainId : pendingChainMap.keySet())
        {
            if (currentTime > pendingChainMap.get(chainId))
            {
                pendingChainMap.remove(chainId);
            }
        }
    }

    private void onError(Throwable throwable)
    {
        Timber.e(throwable);
    }

    private void checkOpenSea(long chainId)
    {
        if ((openSeaQueryDisposable != null && !openSeaQueryDisposable.isDisposed())
            || openseaService == null || !EthereumNetworkBase.hasOpenseaAPI(chainId)
            || !openseaService.canCheckChain(chainId)) return;

        NetworkInfo info = ethereumNetworkRepository.getNetworkByChain(chainId);

        if (info.chainId == transferCheckChain) return; //currently checking this chainId in TransactionsNetworkClient
        
        Timber.tag(TAG).d("Fetch from opensea : " + currentAddress + " : " + info.getShortName());

        openSeaCheckId = info.chainId;

        openSeaQueryDisposable = callOpenSeaAPI(info)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(r -> {
                    openSeaQueryDisposable = null;
                    openSeaCheckId = 0;
                }, this::openSeaCallError);
    }

    private void checkOkTokens(long chainId, OkProtocolType tokenType)
    {
        OkLinkService.get(httpClient).getTokensForChain(chainId, currentAddress, tokenType)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(tokenList -> processOkTokenList(tokenList, chainId, tokenType))
                .isDisposed();
    }

    private void processOkTokenList(List<OkToken> tokenList, long chainId, OkProtocolType tokenType)
    {
        //process the list; it's either an individual NFT or an ERC20 token
        Map<String, TokenTicker> tickerMap = new HashMap<>();
        for (OkToken okToken : tokenList)
        {
            //first check if token is known
            Token token = getToken(chainId, okToken.tokenContractAddress);
            if (token == null)
            {
                addUnknownTokenToCheck(new ContractAddress(chainId, okToken.tokenContractAddress));
            }

            switch (tokenType)
            {
                case ERC_20 ->
                {
                    if (!TextUtils.isEmpty(okToken.priceUsd) && !okToken.priceUsd.equals("0"))
                    {
                        TokenTicker ticker = new TokenTicker(okToken.priceUsd, "0", okToken.symbol, "", System.currentTimeMillis());
                        tickerMap.put(okToken.tokenContractAddress, ticker);
                    }
                }
                case ERC_721, ERC_1155 ->
                {
                    //handle each individual token
                    //check if this tokenId is known
                    BigInteger tokenId = new BigInteger(okToken.tokenId);
                    if (token == null)
                    {
                        token = tokenFactory.createToken(okToken.createInfo(chainId), OkProtocolType.getStandardType(tokenType), ethereumNetworkRepository.getNetworkByChain(chainId).getShortName());
                    }

                    NFTAsset asset = token.getAssetForToken(tokenId);

                    if (asset == null)
                    {
                        //create blank asset with tokenId
                        asset = new NFTAsset(tokenId);
                        asset.setBalance(new BigDecimal(okToken.holdingAmount));
                        //store the asset
                        storeAsset(token, tokenId, asset);
                    }
                }
            }
        }

        if (tokenType == OkProtocolType.ERC_20)
        {
            tickerService.storeTickers(chainId, tickerMap);
        }
    }

    private void openSeaCallError(Throwable error)
    {
        Timber.w(error);
        openSeaQueryDisposable = null;
        openSeaCheckId = 0;
    }

    private Single<Boolean> callOpenSeaAPI(NetworkInfo info)
    {
        final Wallet wallet = new Wallet(currentAddress);

        return Single.fromCallable(() -> {
            openseaService.getTokens(currentAddress, info.chainId, info.getShortName(), this).toObservable()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .flatMap(Observable::fromArray)
                    .blockingForEach(t -> tokenRepository.checkInterface(t, wallet)
                            .map(token -> tokenRepository.initNFTAssets(wallet, token))
                            .flatMap(token -> tokenRepository.storeTokens(wallet, new Token[]{token}))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .blockingGet()
                    );

            return true;
        });
    }

    public boolean openSeaUpdateInProgress(long chainId)
    {
        return openSeaQueryDisposable != null && !openSeaQueryDisposable.isDisposed() && openSeaCheckId == chainId;
    }

    private void checkERC20(long chainId)
    {
        if (erc20CheckDisposable == null || erc20CheckDisposable.isDisposed())
        {
            erc20CheckDisposable = tickerService.syncERC20Tickers(chainId, getAllERC20(chainId))
                    .subscribeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::finishCheckChain, this::onERC20Error);
        }
    }

    private List<TokenCardMeta> getAllERC20(long chainId)
    {
        TokenCardMeta[] tokenList = tokenRepository.fetchTokenMetasForUpdate(new Wallet(currentAddress), Collections.singletonList(chainId));
        List<TokenCardMeta> allERC20 = new ArrayList<>();
        for (TokenCardMeta tcm : tokenList)
        {
            if (tcm.type == ContractType.ERC20 && tcm.isEnabled) //filter out enabled, visible tokens
            {
                allERC20.add(tcm);
            }
        }
        return allERC20;
    }

    private void finishCheckChain(int updated)
    {
        erc20CheckDisposable = null;
    }

    private void onERC20Error(Throwable throwable)
    {
        erc20CheckDisposable = null;
        Timber.e(throwable);
    }

    public void updateTickers()
    {
        tickerService.updateTickers();
    }

    public Realm getRealmInstance(Wallet wallet)
    {
        return tokenRepository.getRealmInstance(wallet);
    }

    public Realm getWalletRealmInstance()
    {
        if (currentAddress != null)
        {
            return tokenRepository.getRealmInstance(new Wallet(currentAddress));
        }
        else
        {
            return null;
        }
    }

    /**
     * Called when we create a transaction
     * @param chainId
     */
    public void markChainPending(long chainId)
    {
        pendingChainMap.put(chainId, System.currentTimeMillis() + PENDING_TIME_LIMIT);
    }

    public Single<Pair<Double, Double>> getFiatValuePair()
    {
        return tokenRepository.getTotalValue(currentAddress, EthereumNetworkBase.getAllMainNetworks());
    }

    public Single<List<String>> getTickerUpdateList()
    {
        return tokenRepository.getTickerUpdateList(networkFilter);
    }

    public double convertToUSD(double localFiatValue)
    {
        return localFiatValue / tickerService.getCurrentConversionRate();
    }

    public Pair<Double, Double> getFiatValuePair(long chainId, String address)
    {
        Token token = getToken(chainId, address);
        TokenTicker tt = token != null ? getTokenTicker(token) : null;
        if (tt == null) return new Pair<>(0.0, 0.0);

        return new Pair<>(Double.parseDouble(tt.price), Double.parseDouble(tt.percentChange24h));
    }

    public double getTokenFiatValue(long chainId, String address)
    {
        Token token = getToken(chainId, address);
        TokenTicker tt = token != null ? getTokenTicker(token) : null;
        if (tt == null) return 0.0;
        BigDecimal correctedBalance = token.getCorrectedBalance(18);
        BigDecimal fiatValue = correctedBalance.multiply(new BigDecimal(tt.price)).setScale(18, RoundingMode.DOWN);
        return fiatValue.doubleValue();
    }

    ///////////////////////////////////////////
    // Update Heuristics - timings and weightings for token updates
    // Fine tune how and when tokens are updated here

    /**
     * Token update heuristic - calculates which token should be updated next
     * @return Token that needs updating
     */

    //TODO: Integrate the transfer check update time into the priority calculation
    //TODO: If we have done a transfer check recently then we don't need to check balance here
    public Token getNextInBalanceUpdateQueue()
    {
        //pull all tokens from this wallet out of DB
        TokenCardMeta[] tokenList = buildUpdateMap();

        //calculate update based on last update time & importance
        float highestWeighting = 0;
        long currentTime = System.currentTimeMillis();
        Token storeToken = pendingBaseCheck();
        if (storeToken == null) { storeToken = tokenStoreList.poll(); }
        if (storeToken != null) { return storeToken; }

        TokenCardMeta highestToken = null;

        //this list will be in order of update.
        for (TokenCardMeta check : tokenList)
        {
            long lastCheckDiff = currentTime - check.lastUpdate;
            long lastUpdateDiff = check.lastTxUpdate > 0 ? currentTime - check.lastTxUpdate : 0;

            float weighting = check.calculateBalanceUpdateWeight();

            if ((!check.isEnabled || check.isNFT()) && !isSynced()) continue; //don't start looking at NFT balances until we sync the chain/ERC20 tokens
            if (!isSynced() && check.lastUpdate > syncStart) continue; //don't start updating already updated tokens until all ERC20 are checked
            if (!appHasFocus && (!check.isEthereum() && !isFocusToken(check))) continue; //only check chains when wallet out of focus

            //simply multiply the weighting by the last diff.
            float updateFactor = weighting * (float) lastCheckDiff * (check.isEnabled ? 1 : 0.25f);
            long cutoffCheck = check.calculateUpdateFrequency(); //normal minimum update frequency for token 30 seconds, 5 minutes for hidden token

            if (!check.isEthereum() && lastUpdateDiff > DateUtils.DAY_IN_MILLIS)
            {
                cutoffCheck = 120*DateUtils.SECOND_IN_MILLIS;
                updateFactor = 0.5f * updateFactor;
            }

            if (isFocusToken(check))
            {
                updateFactor = 3.0f * (float) lastCheckDiff;
                cutoffCheck = 15*DateUtils.SECOND_IN_MILLIS; //focus token can be checked every 15 seconds - focus token when erc20 or chain clicked on in wallet
            }
            else if (check.isEthereum() && pendingChainMap.containsKey(check.getChain())) //higher priority for checking balance of pending chain
            {
                cutoffCheck = 15*DateUtils.SECOND_IN_MILLIS;
                updateFactor = 4.0f * (float) lastCheckDiff; //chain has a recent transaction
            }
            else if (check.isEthereum())
            {
                cutoffCheck = 20*DateUtils.SECOND_IN_MILLIS; //update check limit for base chains is 20 seconds
            }
            else if (focusToken != null)
            {
                updateFactor = 0.1f * (float) lastCheckDiff;
                cutoffCheck = 60*DateUtils.SECOND_IN_MILLIS; //when looking at token in detail view (ERC20TokenDetail) update other tokens at 1 minute cycle
            }

            if (updateFactor > highestWeighting && (lastCheckDiff > (float)cutoffCheck))
            {
                highestWeighting = updateFactor;
                highestToken = check;
            }
        }

        if (highestToken != null)
        {
            pendingTokenMap.put(databaseKey(highestToken.getChain(), highestToken.getAddress()), System.currentTimeMillis());
            return getToken(highestToken.getChain(), highestToken.getAddress());
        }
        else
        {
            return null;
        }
    }

    private Token pendingBaseCheck()
    {
        Long chainId = baseTokenCheck.poll();
        if (chainId != null)
        {
            Timber.tag(TAG).d("Base Token Check: %s", ethereumNetworkRepository.getNetworkByChain(chainId).name);
            //return new TokenCardMeta(getToken(chainId, currentAddress));
            return createCurrencyToken(ethereumNetworkRepository.getNetworkByChain(chainId), new Wallet(currentAddress));
        }
        else
        {
            if (syncCount == 0)
            {
                syncCount = 1;
            }
            return null;
        }
    }

    /**
     * Get network filter settings and initialise checks for token balances
     */
    private void setupFilters()
    {
        baseTokenCheck.clear();
        if (!ethereumNetworkRepository.hasSetNetworkFilters()) //add all networks to a check list to check balances at wallet startup and refresh
        {
            //first blank all existing filters for zero balance tokens, as user hasn't specified which chains they want to see
            blankFiltersForZeroBalance();

            NetworkInfo[] networks = ethereumNetworkRepository.getAvailableNetworkList();
            for (NetworkInfo info : networks) { baseTokenCheck.add(info.chainId); }
        }
    }

    /**
     * set up visibility only for tokens with balance, if all zero then add the default networks
     */
    private void blankFiltersForZeroBalance()
    {
        networkFilter.clear();
        NetworkInfo[] networks = ethereumNetworkRepository.getAvailableNetworkList();

        if (!ethereumNetworkRepository.hasSetNetworkFilters())
        {
            for (NetworkInfo network : networks)
            {
                Token t = getToken(network.chainId, currentAddress);
                if (t != null && t.balance.compareTo(BigDecimal.ZERO) > 0)
                {
                    networkFilter.add(network.chainId);
                }
            }
        }

        for (Long lockedChain : CustomViewSettings.getLockedChains())
        {
            if (!networkFilter.contains(lockedChain)) networkFilter.add(lockedChain);
        }

        if (networkFilter.size() == 0) networkFilter.add(ethereumNetworkRepository.getDefaultNetwork());

        //set network filter prefs
        ethereumNetworkRepository.setFilterNetworkList(networkFilter.toArray(new Long[0]));
    }

    /**
     * Provides an immediate find token and add to Realm call
     * - As opposed to using storeToken which is a lower priority 'add token to standard update queue'
     *
     * @param info TokenInfo
     * @param walletAddress Current Wallet Address
     * @return RX-Single token return
     */
    public Single<Token> addToken(final TokenInfo info, final String walletAddress)
    {
        return tokenRepository.determineCommonType(info)
                .map(contractType -> tokenFactory.createToken(info, contractType, ethereumNetworkRepository.getNetworkByChain(info.chainId).getShortName()))
                .map(token -> { token.setTokenWallet(walletAddress); return token; })
                .flatMap(token -> tokenRepository.updateTokenBalance(walletAddress, token).map(newBalance -> {
                    token.balance = newBalance;
                    return token;
                }));
    }

    public void addTokens(List<Token> tokenList)
    {
        for (Token t : tokenList)
        {
            if (t != null) tokenStoreList.addFirst(t);
        }
    }

    //Add in any tokens required to be shown - mainly used by forks for always showing a specific token
    //Note that we can't go via the usual tokenStoreList method as we need to mark this token as enabled and locked visible
    private void addLockedTokens()
    {
        final String wallet = currentAddress;
        //ensure locked tokens are displaying
        Observable.fromIterable(CustomViewSettings.getLockedTokens())
                .forEach(info -> addToken(info, wallet)
                        .flatMapCompletable(token -> enableToken(wallet, token.getContractAddress()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe())
                        .isDisposed();
    }

    public Completable enableToken(String walletAddr, ContractAddress cAddr)
    {
        return Completable.fromAction(() -> {
            final Wallet wallet = new Wallet(walletAddr);
            tokenRepository.setEnable(wallet, cAddr, true);
            tokenRepository.setVisibilityChanged(wallet, cAddr);
        });
    }

    public Realm getTickerRealmInstance()
    {
        return tokenRepository.getTickerRealmInstance();
    }

    public void walletInFocus()
    {
        appHasFocus = true;

        //running or not?
    }

    public void walletOutOfFocus()
    {
        appHasFocus = false;
    }

    /**
     * Notify that the new gas setting widget was actually used :)
     *
     * @param gasSpeed
     */
    public void track(String gasSpeed)
    {
        if (analyticsService != null)
        {
            AnalyticsProperties analyticsProperties = new AnalyticsProperties();
            analyticsProperties.put(Analytics.PROPS_GAS_SPEED, gasSpeed);
            analyticsService.track(Analytics.Action.USE_GAS_WIDGET.getValue(), analyticsProperties);
        }
    }

    @NotNull
    public Token getTokenOrBase(long chainId, String address)
    {
        Token token = getToken(chainId, address);
        if (token == null)
        {
            token = getToken(chainId, currentAddress); // use base currency
        }

        if (token == null)
        {
            //create base token if required
            token = ethereumNetworkRepository.getBlankOverrideToken(ethereumNetworkRepository.getNetworkByChain(chainId));
        }

        return token;
    }

    public void updateAssets(Token token, List<BigInteger> additions, List<BigInteger> removals)
    {
        tokenRepository.updateAssets(currentAddress, token, additions, removals);
    }

    public void storeAsset(Token token, BigInteger tokenId, NFTAsset asset)
    {
        tokenRepository.storeAsset(currentAddress, token, tokenId, asset);
    }

    public boolean isChainToken(long chainId, String tokenAddress)
    {
        return ethereumNetworkRepository.isChainContract(chainId, tokenAddress);
    }

    public boolean hasChainToken(long chainId)
    {
        return !EthereumNetworkRepository.getChainOverrideAddress(chainId).isEmpty();
    }

    public Token getServiceToken(long chainId)
    {
        if (hasChainToken(chainId))
        {
            return getToken(chainId, EthereumNetworkRepository.getChainOverrideAddress(chainId));
        }
        else
        {
            return getToken(chainId, currentAddress);
        }
    }

    public String getFallbackUrlForToken(Token token)
    {
        String tURL = tokenRepository.getTokenImageUrl(token.tokenInfo.chainId, token.getAddress());
        if (TextUtils.isEmpty(tURL))
        {
            tURL = Utils.getTWTokenImageUrl(token.tokenInfo.chainId, token.getAddress());
        }

        return tURL;
    }

    public void checkingChain(long chainId)
    {
        transferCheckChain = chainId;
    }

    public void addBalanceCheck(Token token)
    {
        for (Token t : tokenStoreList)
        {
            if (t.equals(token)) return;
        }

        tokenStoreList.add(token);
    }

    private Token createCurrencyToken(NetworkInfo network, Wallet wallet)
    {
        TokenInfo tokenInfo = new TokenInfo(wallet.address, network.name, network.symbol, 18, true, network.chainId);
        BigDecimal balance = BigDecimal.ZERO;
        Token eth = new Token(tokenInfo, balance, 0, network.getShortName(), ContractType.ETHEREUM); //create with zero time index to ensure it's updated immediately
        eth.setTokenWallet(wallet.address);
        eth.setIsEthereum();
        eth.pendingBalance = balance;
        return eth;
    }

    public boolean isSynced()
    {
        return (syncTimer == 0);
    }

    public boolean startWalletSync(ServiceSyncCallback cb)
    {
        setCompletionCallback(cb, 0);
        return true;
    }

    public void setCompletionCallback(ServiceSyncCallback cb, int sync)
    {
        syncCount = sync;
        completionCallback = cb;
        syncTimer = System.currentTimeMillis();

        if (sync > 0)
        {
            baseTokenCheck.clear();
            networkFilter.clear();

            NetworkInfo[] networks = ethereumNetworkRepository.getAvailableNetworkList();

            for (NetworkInfo info : networks)
            {
                if (info.hasRealValue())
                {
                    networkFilter.add(info.chainId);
                    baseTokenCheck.add(info.chainId);
                }
            }
        }
    }

    private void deleteTickers()
    {
        if (BuildConfig.DEBUG && !done) //Ensure release build never deletes all the tickers
        {
            done = true;
            Single.fromCallable(() -> {
                tickerService.deleteTickers();
                return true;
            }).subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io()).subscribe(b -> {
            }).isDisposed();
        }
    }

    // TODO: This may be refactored once we have switched over to a more efficient Token database model
    // That is - common data like Name, Decimals, Address goes into a single Token database,
    //   wallet specific data like balance, update time etc goes into the per-wallet database
    public TokenGroup getTokenGroup(Token token)
    {
        if (token != null)
        {
            return tokenRepository.getTokenGroup(token.tokenInfo.chainId, token.tokenInfo.address, token.getInterfaceSpec());
        }
        else
        {
            return TokenGroup.ASSET;
        }
    }

    public boolean hasLockedGas(long chainId)
    {
        return ethereumNetworkRepository.hasLockedGas(chainId);
    }

    public Single<Boolean> deleteTokens(List<TokenCardMeta> metasToDelete)
    {
        return Single.fromCallable(() -> {
            tokenRepository.deleteRealmTokens(new Wallet(currentAddress), metasToDelete);
            for (TokenCardMeta tcm : metasToDelete)
            {
                pendingTokenMap.remove(databaseKey(tcm.getChain(), tcm.getAddress()));
            }
            return true;
        });
    }

    public Token getToken(String walletAddress, long chainId, String tokenAddress)
    {
        if (walletAddress == null)
        {
            walletAddress = currentAddress;
        }
        if (TextUtils.isEmpty(walletAddress) || TextUtils.isEmpty(tokenAddress)) return null;
        else return tokenRepository.fetchToken(chainId, walletAddress, tokenAddress.toLowerCase());
    }

    public Token getAttestation(long chainId, String addr, String attnId)
    {
        //fetch attestation
        return tokenRepository.fetchAttestation(chainId, currentAddress, addr.toLowerCase(), attnId);
    }

    public List<Token> getAttestations(long chainId, String address)
    {
        return tokenRepository.fetchAttestations(chainId, currentAddress, address);
    }

    public boolean isOnFocus()
    {
        return appHasFocus;
    }
}
