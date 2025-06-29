package com.gaaiuswallet.app.viewmodel;

import static com.gaaiuswallet.app.repository.TokenRepository.getWeb3jService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.ErrorEnvelope;
import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.TransactionReturn;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.tokenscript.EventUtils;
import com.gaaiuswallet.app.interact.CreateTransactionInteract;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.interact.FindDefaultNetworkInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.app.repository.TransactionsRealmCache;
import com.gaaiuswallet.app.repository.entity.RealmTransaction;
import com.gaaiuswallet.app.router.ExternalBrowserRouter;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.service.TransactionSendHandlerInterface;
import com.gaaiuswallet.app.util.BalanceUtils;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.web3.entity.Web3Transaction;
import com.gaaiuswallet.hardware.SignatureFromKey;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthTransaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;

@HiltViewModel
public class TransactionDetailViewModel extends BaseViewModel implements TransactionSendHandlerInterface
{
    private final ExternalBrowserRouter externalBrowserRouter;
    private final FindDefaultNetworkInteract networkInteract;
    private final TokenRepositoryType tokenRepository;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final CreateTransactionInteract createTransactionInteract;
    private final GenericWalletInteract genericWalletInteract;
    private final KeyService keyService;
    private final TokensService tokenService;
    private final GasService gasService;
    private final MutableLiveData<Wallet> wallet = new MutableLiveData<>();
    private final MutableLiveData<BigInteger> latestBlock = new MutableLiveData<>();
    private final MutableLiveData<Transaction> latestTx = new MutableLiveData<>();
    private final MutableLiveData<Transaction> transaction = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionFinalised = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionError = new MutableLiveData<>();
    private String walletAddress;
    @Nullable
    private Disposable findWalletDisposable;
    @Nullable
    private Disposable pendingUpdateDisposable;
    @Nullable
    private Disposable currentBlockUpdateDisposable;

    @Inject
    TransactionDetailViewModel(
        GenericWalletInteract genericWalletInteract,
        FindDefaultNetworkInteract findDefaultNetworkInteract,
        ExternalBrowserRouter externalBrowserRouter,
        TokenRepositoryType tokenRepository,
        TokensService tokenService,
        FetchTransactionsInteract fetchTransactionsInteract,
        KeyService keyService,
        GasService gasService,
        CreateTransactionInteract createTransactionInteract,
        AnalyticsServiceType analyticsService)
    {
        this.genericWalletInteract = genericWalletInteract;
        this.networkInteract = findDefaultNetworkInteract;
        this.externalBrowserRouter = externalBrowserRouter;
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.keyService = keyService;
        this.gasService = gasService;
        this.createTransactionInteract = createTransactionInteract;
        setAnalyticsService(analyticsService);
    }

    public LiveData<Wallet> wallet()
    {
        return wallet;
    }

    public LiveData<BigInteger> latestBlock()
    {
        return latestBlock;
    }

    public LiveData<Transaction> latestTx()
    {
        return latestTx;
    }

    public LiveData<Transaction> onTransaction()
    {
        return transaction;
    }

    public MutableLiveData<TransactionReturn> transactionFinalised()
    {
        return transactionFinalised;
    }

    public MutableLiveData<TransactionReturn> transactionError()
    {
        return transactionError;
    }

    public void prepare(final long chainId)
    {
        findWalletDisposable = genericWalletInteract.find()
            .subscribe(w -> onWallet(w, chainId), this::onError);
    }

    private void onWallet(Wallet w, long chainId)
    {
        wallet.postValue(w);
        walletAddress = w.address;
        currentBlockUpdateDisposable = Observable.interval(0, 6, TimeUnit.SECONDS)
            .doOnNext(l -> {
                disposable = tokenRepository.fetchLatestBlockNumber(chainId)
                    .subscribeOn(Schedulers.io())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(latestBlock::postValue, t -> this.latestBlock.postValue(BigInteger.ZERO));
            }).subscribe();
    }

    public void showMoreDetails(Context context, Transaction transaction)
    {
        Uri uri = buildEtherscanUri(transaction);
        if (uri != null)
        {
            externalBrowserRouter.open(context, uri);
        }
    }

    public void startPendingTimeDisplay(final String txHash)
    {
        pendingUpdateDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
            .doOnNext(l -> displayCurrentPendingTime(txHash)).subscribe();
    }

    //TODO: move to display new transaction
    private void displayCurrentPendingTime(final String txHash)
    {
        //check if TX has been written
        Transaction tx = fetchTransactionsInteract.fetchCached(walletAddress, txHash);
        if (tx != null)
        {
            latestTx.postValue(tx);
            if (!tx.isPending())
            {
                if (pendingUpdateDisposable != null && !pendingUpdateDisposable.isDisposed())
                    pendingUpdateDisposable.dispose();
            }
        }
    }

    public void shareTransactionDetail(Context context, Transaction transaction)
    {
        Uri shareUri = buildEtherscanUri(transaction);
        if (shareUri != null)
        {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.subject_transaction_detail));
            sharingIntent.putExtra(Intent.EXTRA_TEXT, shareUri.toString());
            context.startActivity(Intent.createChooser(sharingIntent, "Share via"));
        }
    }

    public Token getToken(long chainId, String address)
    {
        return tokenService.getTokenOrBase(chainId, address);
    }

    @Nullable
    private Uri buildEtherscanUri(Transaction transaction)
    {
        NetworkInfo networkInfo = networkInteract.getNetworkInfo(transaction.chainId);
        if (networkInfo != null && Utils.isValidUrl(networkInfo.etherscanUrl))
        {
            return networkInfo.getEtherscanUri(transaction.hash);
        }
        else
        {
            return null;
        }
    }

    public boolean hasEtherscanDetail(Transaction tx)
    {
        NetworkInfo networkInfo = networkInteract.getNetworkInfo(tx.chainId);
        return networkInfo != null && !networkInfo.getEtherscanUri(tx.hash).equals(Uri.EMPTY);
    }

    public String getNetworkName(long chainId)
    {
        return networkInteract.getNetworkName(chainId);
    }

    public String getNetworkSymbol(long chainId)
    {
        return networkInteract.getNetworkInfo(chainId).symbol;
    }

    public void onDispose()
    {
        if (pendingUpdateDisposable != null && !pendingUpdateDisposable.isDisposed())
            pendingUpdateDisposable.dispose();
        if (currentBlockUpdateDisposable != null && !currentBlockUpdateDisposable.isDisposed())
            currentBlockUpdateDisposable.dispose();
    }

    public void fetchTransaction(Wallet wallet, String txHash, long chainId)
    {
        Transaction tx = fetchTransactionsInteract.fetchCached(wallet.address, txHash);
        if (tx == null || tx.gas.startsWith("0x"))
        {
            //fetch Transaction from chain
            Web3j web3j = getWeb3jService(chainId);
            disposable = EventUtils.getTransactionDetails(txHash, web3j)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(ethTx -> storeTx(ethTx, wallet, chainId, web3j), this::onError);
        }
        else
        {
            transaction.postValue(tx);
        }
    }

    private void storeTx(EthTransaction rawTx, Wallet wallet, long chainId, Web3j web3j)
    {
        //need to fetch the tx block time
        if (rawTx == null)
        {
            error.postValue(new ErrorEnvelope("no transaction"));
            return;
        }

        org.web3j.protocol.core.methods.response.Transaction ethTx = rawTx.getTransaction().get();
        disposable = EventUtils.getBlockDetails(ethTx.getBlockHash(), web3j)
            .map(ethBlock -> new Transaction(ethTx, chainId, true, ethBlock.getBlock().getTimestamp().longValue()))
            .map(tx -> writeTransaction(wallet, tx))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(transaction::postValue, this::onError);
    }

    private Transaction writeTransaction(Wallet wallet, Transaction tx)
    {
        try (Realm instance = fetchTransactionsInteract.getRealmInstance(wallet))
        {
            instance.beginTransaction();
            RealmTransaction realmTx = instance.where(RealmTransaction.class)
                .equalTo("hash", tx.hash)
                .findFirst();

            if (realmTx == null) realmTx = instance.createObject(RealmTransaction.class, tx.hash);
            TransactionsRealmCache.fill(realmTx, tx);
            instance.commitTransaction();
        }
        catch (Exception e)
        {
            //
        }

        return tx;
    }

    public void startGasCycle(long chainId)
    {
        gasService.startGasPriceCycle(chainId);
    }

    public void onDestroy()
    {
        gasService.stopGasPriceCycle();
    }

    public void restartServices()
    {
        fetchTransactionsInteract.restartTransactionService();
    }

    public TokensService getTokenService()
    {
        return tokenService;
    }

    public BigInteger calculateMinGasPrice(BigInteger oldGasPrice)
    {
        BigInteger candidateGasOverridePrice = new BigDecimal(oldGasPrice).multiply(BigDecimal.valueOf(1.1)).setScale(0, RoundingMode.CEILING).toBigInteger();
        BigInteger checkGasPrice = oldGasPrice.add(BalanceUtils.gweiToWei(BigDecimal.valueOf(2)));

        return checkGasPrice.max(candidateGasOverridePrice); //highest price between adding 2 gwei or 10%
    }

    public void getAuthentication(Activity activity, Wallet wallet, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void requestSignature(Web3Transaction finalTx, Wallet wallet, long chainId)
    {
        createTransactionInteract.requestSignature(finalTx, wallet, chainId, this);
    }

    public void sendTransaction(Wallet wallet, long chainId, Web3Transaction w3Tx, SignatureFromKey signatureFromKey)
    {
        createTransactionInteract.sendTransaction(wallet, chainId, w3Tx, signatureFromKey);
    }

    @Override
    public void transactionFinalised(TransactionReturn txData)
    {
        transactionFinalised.postValue(txData);
    }

    @Override
    public void transactionError(TransactionReturn error)
    {
        transactionError.postValue(error);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();
        if (disposable != null && disposable.isDisposed())
        {
            disposable.dispose();
        }
        if (findWalletDisposable != null && findWalletDisposable.isDisposed())
        {
            findWalletDisposable.dispose();
        }
        if (pendingUpdateDisposable != null && pendingUpdateDisposable.isDisposed())
        {
            pendingUpdateDisposable.dispose();
        }
        if (currentBlockUpdateDisposable != null && currentBlockUpdateDisposable.isDisposed())
        {
            currentBlockUpdateDisposable.dispose();
        }
    }

    public GasService getGasService()
    {
        return gasService;
    }
}
