package com.gaaiuswallet.app.viewmodel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.CryptoFunctions;
import com.gaaiuswallet.app.entity.DisplayState;
import com.gaaiuswallet.app.entity.GasEstimate;
import com.gaaiuswallet.app.entity.Operation;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.TransactionReturn;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.CreateTransactionInteract;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.repository.TokenRepository;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.service.TransactionSendHandlerInterface;
import com.gaaiuswallet.app.ui.TransferTicketDetailActivity;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.web3.entity.Address;
import com.gaaiuswallet.app.web3.entity.Web3Transaction;
import com.gaaiuswallet.hardware.SignatureFromKey;
import com.gaaiuswallet.hardware.SignatureReturnType;
import com.gaaiuswallet.token.entity.SalesOrderMalformed;
import com.gaaiuswallet.token.entity.SignableBytes;
import com.gaaiuswallet.token.tools.ParseMagicLink;

import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by James on 21/02/2018.
 */
@HiltViewModel
public class TransferTicketDetailViewModel extends BaseViewModel implements TransactionSendHandlerInterface
{
    private final MutableLiveData<Wallet> defaultWallet = new MutableLiveData<>();
    private final MutableLiveData<String> newTransaction = new MutableLiveData<>();
    private final MutableLiveData<String> universalLinkReady = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionFinalised = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionError = new MutableLiveData<>();

    private final GenericWalletInteract genericWalletInteract;
    private final KeyService keyService;
    private final CreateTransactionInteract createTransactionInteract;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final GasService gasService;
    private final TokensService tokensService;

    private ParseMagicLink parser;
    private Token token;

    private byte[] linkMessage;

    @Inject
    TransferTicketDetailViewModel(GenericWalletInteract genericWalletInteract,
                                  KeyService keyService,
                                  CreateTransactionInteract createTransactionInteract,
                                  FetchTransactionsInteract fetchTransactionsInteract,
                                  AssetDefinitionService assetDefinitionService,
                                  GasService gasService,
                                  AnalyticsServiceType analyticsService,
                                  TokensService tokensService)
    {
        this.genericWalletInteract = genericWalletInteract;
        this.keyService = keyService;
        this.createTransactionInteract = createTransactionInteract;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.gasService = gasService;
        this.tokensService = tokensService;
        setAnalyticsService(analyticsService);
    }

    public MutableLiveData<TransactionReturn> transactionFinalised()
    {
        return transactionFinalised;
    }

    public MutableLiveData<TransactionReturn> transactionError()
    {
        return transactionError;
    }

    public LiveData<Wallet> defaultWallet()
    {
        return defaultWallet;
    }

    public LiveData<String> newTransaction()
    {
        return newTransaction;
    }

    public LiveData<String> universalLinkReady()
    {
        return universalLinkReady;
    }

    private void initParser()
    {
        if (parser == null)
        {
            parser = new ParseMagicLink(new CryptoFunctions(), EthereumNetworkRepository.extraChains());
        }
    }

    public void prepare(Token token)
    {
        this.token = token;
        gasService.startGasPriceCycle(token.tokenInfo.chainId);
    }

    private void onDefaultWallet(Wallet wallet)
    {
        defaultWallet.setValue(wallet);
    }

    public Wallet getWallet()
    {
        return defaultWallet.getValue();
    }

    public void setWallet(Wallet wallet)
    {
        defaultWallet.setValue(wallet);
    }

    public void generateUniversalLink(List<BigInteger> ticketSendIndexList, String contractAddress, long expiry)
    {
        initParser();
        if (ticketSendIndexList == null || ticketSendIndexList.size() == 0)
            return; //TODO: Display error message

        int[] indexList = Utils.bigIntegerListToIntList(ticketSendIndexList);

        //NB tradeBytes is the exact bytes the ERC875 contract builds to check the valid order.
        //This is what we must sign.
        SignableBytes tradeBytes = new SignableBytes(parser.getTradeBytes(indexList, contractAddress, BigInteger.ZERO, expiry));
        try
        {
            linkMessage = ParseMagicLink.generateLeadingLinkBytes(indexList, contractAddress, BigInteger.ZERO, expiry);
        }
        catch (SalesOrderMalformed e)
        {
            //TODO: Display appropriate error to user
        }

        //sign this link
        disposable = createTransactionInteract
                .sign(defaultWallet().getValue(), tradeBytes)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::gotSignature, this::onError);
    }

    public void generateSpawnLink(List<BigInteger> tokenIds, String contractAddress, long expiry)
    {
        initParser();
        SignableBytes tradeBytes = new SignableBytes(parser.getSpawnableBytes(tokenIds, contractAddress, BigInteger.ZERO, expiry));
        try
        {
            linkMessage = ParseMagicLink.generateSpawnableLeadingLinkBytes(tokenIds, contractAddress, BigInteger.ZERO, expiry);
        }
        catch (SalesOrderMalformed e)
        {
            //TODO: Display appropriate error to user
        }

        //sign this link
        disposable = createTransactionInteract
                .sign(defaultWallet().getValue(), tradeBytes)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::gotSignature, this::onError);
    }

    private void gotSignature(SignatureFromKey signature)
    {
        if (signature.sigType == SignatureReturnType.SIGNING_POSTPONED) return;

        String universalLink = parser.completeUniversalLink(token.tokenInfo.chainId, linkMessage, signature.signature);
        //Now open the share icon
        universalLinkReady.postValue(universalLink);
    }

    public void createTokenTransfer(String to, Token token, List<BigInteger> transferList)
    {
        if (!token.contractTypeValid())
        {
            //need to determine the spec
            disposable = fetchTransactionsInteract.queryInterfaceSpec(token.tokenInfo)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(spec -> onInterfaceSpec(spec, to, token, transferList), this::onError);
        }
        else
        {
            final byte[] transactionBytes = TokenRepository.createTicketTransferData(to, transferList, token);
            final Web3Transaction w3tx = new Web3Transaction(
                    new Address(getWallet().address),
                    new Address(token.getAddress()),
                    BigInteger.ZERO,
                    BigInteger.ZERO,
                    new BigInteger(C.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS),
                    -1,
                    Numeric.toHexString(transactionBytes),
                    -1);

            requestSignature(w3tx, getWallet(), token.tokenInfo.chainId);
        }
    }

    public void createTokenTransfer(String to, Token token, ArrayList<Pair<BigInteger, NFTAsset>> transferList)
    {
        //need to determine the spec
        disposable = fetchTransactionsInteract.queryInterfaceSpec(token.tokenInfo)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(spec -> onInterfaceSpec(spec, to, token, transferList), this::onError);
    }

    private void onInterfaceSpec(ContractType spec, String to, Token token, ArrayList<Pair<BigInteger, NFTAsset>> transferList)
    {
        token.setInterfaceSpec(spec);
        createTokenTransfer(to, token, transferList);
    }

    private void onInterfaceSpec(ContractType spec, String to, Token token, List<BigInteger> transferList)
    {
        token.setInterfaceSpec(spec);
        createTokenTransfer(to, token, transferList);
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }

    public void stopGasSettingsFetch()
    {
        gasService.stopGasPriceCycle();
    }

    public void getAuthorisation(Activity activity, SignAuthenticationCallback callback)
    {
        if (defaultWallet.getValue() != null)
        {
            keyService.getAuthenticationForSignature(defaultWallet.getValue(), activity, callback);
        }
    }

    public void resetSignDialog()
    {
        keyService.resetSigningDialog();
    }

    public void completeAuthentication(Operation signData)
    {
        keyService.completeAuthentication(signData);
    }

    public Single<GasEstimate> calculateGasEstimate(Wallet wallet, byte[] transactionBytes, long chainId, String sendAddress, BigDecimal sendAmount)
    {
        return gasService.calculateGasEstimate(transactionBytes, chainId, sendAddress, sendAmount.toBigInteger(), wallet, BigInteger.ZERO);
    }

    public void getAuthentication(Activity activity, Wallet wallet, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void failedAuthentication(Operation signData)
    {
        keyService.completeAuthentication(signData);
    }

    public void requestSignature(Web3Transaction finalTx, Wallet wallet, long chainId)
    {
        createTransactionInteract.requestSignature(finalTx, wallet, chainId, this);
    }

    public void sendTransaction(Wallet wallet, long chainId, Web3Transaction w3Tx, SignatureFromKey signatureFromKey)
    {
        createTransactionInteract.sendTransaction(wallet, chainId, w3Tx, signatureFromKey);
    }

    public byte[] getERC721TransferBytes(String to, String contractAddress, String tokenId, long chainId)
    {
        Token token = tokensService.getToken(chainId, contractAddress);
        List<BigInteger> tokenIds = token.stringHexToBigIntegerList(tokenId);
        return TokenRepository.createERC721TransferFunction(to, token, tokenIds);
    }

    public TokensService getTokenService()
    {
        return tokensService;
    }

    public void openTransferState(Context context, Token token, String ticketIds, DisplayState transferStatus)
    {
        if (transferStatus != DisplayState.NO_ACTION)
        {
            Intent intent = new Intent(context, TransferTicketDetailActivity.class);
            intent.putExtra(C.Key.WALLET, defaultWallet.getValue());
            intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
            intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
            intent.putExtra(C.EXTRA_TOKENID_LIST, ticketIds);
            intent.putExtra(C.EXTRA_STATE, transferStatus.ordinal());
            intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            context.startActivity(intent);
        }
    }

    @Override
    public void transactionFinalised(TransactionReturn txData)
    {
        transactionFinalised.postValue(txData);
    }

    @Override
    public void transactionError(TransactionReturn txError)
    {
        transactionError.postValue(txError);
    }

    public void loadWallet(String address)
    {
        disposable = genericWalletInteract
                .findWallet(address)
                .subscribe(this::onDefaultWallet, this::onError);
    }

    public GasService getGasService()
    {
        return gasService;
    }
}
