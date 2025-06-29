package com.gaaiuswallet.app.viewmodel;

import static com.gaaiuswallet.app.entity.DisplayState.TRANSFER_TO_ADDRESS;
import static com.gaaiuswallet.token.tools.TokenDefinition.NO_SCRIPT;
import static com.gaaiuswallet.token.tools.TokenDefinition.UNCHANGED_SCRIPT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.entity.GasEstimate;
import com.gaaiuswallet.app.entity.Operation;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.TSAttrCallback;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.TransactionReturn;
import com.gaaiuswallet.app.entity.UpdateType;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.opensea.OpenSeaAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.CreateTransactionInteract;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.service.OpenSeaService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.service.TransactionSendHandlerInterface;
import com.gaaiuswallet.app.ui.AssetDisplayActivity;
import com.gaaiuswallet.app.ui.Erc1155AssetSelectActivity;
import com.gaaiuswallet.app.ui.Erc20DetailActivity;
import com.gaaiuswallet.app.ui.FunctionActivity;
import com.gaaiuswallet.app.ui.MyAddressActivity;
import com.gaaiuswallet.app.ui.RedeemAssetSelectActivity;
import com.gaaiuswallet.app.ui.SellDetailActivity;
import com.gaaiuswallet.app.ui.TransferNFTActivity;
import com.gaaiuswallet.app.ui.TransferTicketDetailActivity;
import com.gaaiuswallet.app.ui.widget.entity.TicketRangeParcel;
import com.gaaiuswallet.app.util.BalanceUtils;
import com.gaaiuswallet.app.util.JsonUtils;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.web3.entity.Address;
import com.gaaiuswallet.app.web3.entity.Web3Transaction;
import com.gaaiuswallet.hardware.SignatureFromKey;
import com.gaaiuswallet.token.entity.Attribute;
import com.gaaiuswallet.token.entity.ContractAddress;
import com.gaaiuswallet.token.entity.FunctionDefinition;
import com.gaaiuswallet.token.entity.MethodArg;
import com.gaaiuswallet.token.entity.SigReturnType;
import com.gaaiuswallet.token.entity.TSAction;
import com.gaaiuswallet.token.entity.TicketRange;
import com.gaaiuswallet.token.entity.TokenScriptResult;
import com.gaaiuswallet.token.entity.TokenscriptElement;
import com.gaaiuswallet.token.entity.XMLDsigDescriptor;
import com.gaaiuswallet.token.tools.TokenDefinition;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import timber.log.Timber;

/**
 * Created by James on 2/04/2019.
 * Stormbird in Singapore
 */
@HiltViewModel
public class TokenFunctionViewModel extends BaseViewModel implements TransactionSendHandlerInterface
{
    private final AssetDefinitionService assetDefinitionService;
    private final CreateTransactionInteract createTransactionInteract;
    private final GasService gasService;
    private final TokensService tokensService;
    private final KeyService keyService;
    private final GenericWalletInteract genericWalletInteract;
    private final OpenSeaService openseaService;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final PreferenceRepositoryType preferences;
    private final MutableLiveData<Token> insufficientFunds = new MutableLiveData<>();
    private final MutableLiveData<String> invalidAddress = new MutableLiveData<>();
    private final MutableLiveData<XMLDsigDescriptor> sig = new MutableLiveData<>();
    private final MutableLiveData<TokenDefinition> newScriptFound = new MutableLiveData<>();
    private final MutableLiveData<TokenDefinition> attrFetchComplete = new MutableLiveData<>();
    private final MutableLiveData<Wallet> walletUpdate = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionFinalised = new MutableLiveData<>();
    private final MutableLiveData<TransactionReturn> transactionError = new MutableLiveData<>();
    private final MutableLiveData<Web3Transaction> gasEstimateComplete = new MutableLiveData<>();
    private final MutableLiveData<Pair<GasEstimate, Web3Transaction>> gasEstimateError = new MutableLiveData<>();
    private final MutableLiveData<NFTAsset> nftAsset = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scriptUpdateInProgress = new MutableLiveData<>();
    private Wallet wallet;

    @Nullable
    private Disposable metadataDisposable;

    @Nullable
    private Disposable openseaDisposable;

    @Nullable
    private Disposable calcGasCost;

    @Nullable
    private Disposable scriptUpdate;

    @Nullable
    private Disposable attrRefresh;

    @Inject
    TokenFunctionViewModel(
            AssetDefinitionService assetDefinitionService,
            CreateTransactionInteract createTransactionInteract,
            GasService gasService,
            TokensService tokensService,
            KeyService keyService,
            GenericWalletInteract genericWalletInteract,
            OpenSeaService openseaService,
            FetchTransactionsInteract fetchTransactionsInteract,
            AnalyticsServiceType analyticsService,
            PreferenceRepositoryType prefs)
    {
        this.assetDefinitionService = assetDefinitionService;
        this.createTransactionInteract = createTransactionInteract;
        this.gasService = gasService;
        this.tokensService = tokensService;
        this.keyService = keyService;
        this.genericWalletInteract = genericWalletInteract;
        this.openseaService = openseaService;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        setAnalyticsService(analyticsService);
        this.preferences = prefs;
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }

    public LiveData<Token> insufficientFunds()
    {
        return insufficientFunds;
    }

    public LiveData<String> invalidAddress()
    {
        return invalidAddress;
    }

    public LiveData<XMLDsigDescriptor> sig()
    {
        return sig;
    }

    public LiveData<Wallet> walletUpdate()
    {
        return walletUpdate;
    }

    public LiveData<TokenDefinition> newScriptFound()
    {
        return newScriptFound;
    }

    public LiveData<TokenDefinition> attrFetchComplete()
    {
        return attrFetchComplete;
    }

    public LiveData<Boolean> scriptUpdateInProgress()
    {
        return scriptUpdateInProgress;
    }

    public MutableLiveData<TransactionReturn> transactionFinalised()
    {
        return transactionFinalised;
    }

    public MutableLiveData<TransactionReturn> transactionError()
    {
        return transactionError;
    }

    public MutableLiveData<Web3Transaction> gasEstimateComplete()
    {
        return gasEstimateComplete;
    }

    public MutableLiveData<Pair<GasEstimate, Web3Transaction>> gasEstimateError()
    {
        return gasEstimateError;
    }

    public MutableLiveData<NFTAsset> nftAsset()
    {
        return nftAsset;
    }

    public void prepare()
    {
        getCurrentWallet();
    }

    public void openUniversalLink(Context context, Token token, List<BigInteger> selection)
    {
        Intent intent = new Intent(context, SellDetailActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(selection, false));
        intent.putExtra(C.EXTRA_STATE, SellDetailActivity.SET_A_PRICE);
        intent.putExtra(C.EXTRA_PRICE, 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();
    }

    public void startGasPriceUpdate(long chainId)
    {
        gasService.startGasPriceCycle(chainId);
    }

    public void showTransferToken(Context ctx, Token token, List<BigInteger> selection)
    {
        Intent intent = new Intent(ctx, TransferTicketDetailActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());

        intent.putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(selection, false));

        if (token.isERC721()) //skip numerical selection - ERC721 has no multiple token transfer
        {
            intent.putExtra(C.EXTRA_STATE, TRANSFER_TO_ADDRESS.ordinal());
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ctx.startActivity(intent);
    }

    public void showFunction(Context ctx, Token token, String method, List<BigInteger> tokenIds, NFTAsset asset)
    {
        Intent intent = new Intent(ctx, FunctionActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_STATE, method);
        if (asset != null)
        {
            intent.putExtra(C.EXTRA_NFTASSET, asset);
        }

        if (tokenIds == null || tokenIds.size() == 0)
        {
            tokenIds = new ArrayList<>(Collections.singletonList(BigInteger.ZERO));
        }

        intent.putExtra(C.EXTRA_TOKEN_ID, Utils.bigIntListToString(tokenIds, true));
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ctx.startActivity(intent);
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

    public String getTransactionBytes(Token token, BigInteger tokenId, FunctionDefinition def)
    {
        return assetDefinitionService.generateTransactionPayload(token, tokenId, def);
    }

    public Token getToken(long chainId, String contractAddress)
    {
        return tokensService.getToken(chainId, contractAddress);
    }

    public void selectRedeemToken(Context ctx, Token token, List<BigInteger> idList)
    {
        TicketRangeParcel parcel = new TicketRangeParcel(new TicketRange(idList, token.getAddress(), true));
        Intent intent = new Intent(ctx, RedeemAssetSelectActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.TICKET_RANGE, parcel);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ctx.startActivity(intent);
    }

    public void stopGasSettingsFetch()
    {
        gasService.stopGasPriceCycle();
    }

    public void getAuthorisation(Activity activity, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public Token getCurrency(long chainId)
    {
        return tokensService.getToken(chainId, wallet.address);
    }

    public void getCurrentWallet()
    {
        disposable = genericWalletInteract
                .find()
                .subscribe(this::onDefaultWallet, this::onError);
    }

    public Single<Wallet> findActiveWallet()
    {
        return genericWalletInteract
                .find();
    }

    private void onDefaultWallet(Wallet w)
    {
        progress.postValue(false);
        wallet = w;
        walletUpdate.postValue(w);
    }

    public void resetSignDialog()
    {
        keyService.resetSigningDialog();
    }

    public void completeAuthentication(Operation signData)
    {
        keyService.completeAuthentication(signData);
    }

    public void failedAuthentication(Operation signData)
    {
        keyService.failedAuthentication(signData);
    }

    public void showContractInfo(Context ctx, Token token)
    {
        Intent intent = new Intent(ctx, MyAddressActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ctx.startActivity(intent);
    }

    public void selectRedeemTokens(Context ctx, Token token, List<BigInteger> idList)
    {
        TicketRangeParcel parcel = new TicketRangeParcel(new TicketRange(idList, token.getAddress(), true));
        Intent intent = new Intent(ctx, RedeemAssetSelectActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.TICKET_RANGE, parcel);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ctx.startActivity(intent);
    }

    public void sellTicketRouter(Context context, Token token, List<BigInteger> idList)
    {
        Intent intent = new Intent(context, SellDetailActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(idList, false));
        intent.putExtra(C.EXTRA_STATE, SellDetailActivity.SET_A_PRICE);
        intent.putExtra(C.EXTRA_PRICE, 0);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }

    public Web3Transaction handleFunction(TSAction action, BigInteger tokenId, Token token, Context context)
    {
        String functionEffect = action.function.method;
        if (action.function.tx != null && (action.function.method == null || action.function.method.length() == 0)
                && (action.function.parameters == null || action.function.parameters.size() == 0))
        {
            //no params, this is a native style transaction
            return NativeSend(action, token, context);
        }
        else
        {
            //what's selected?
            ContractAddress cAddr = new ContractAddress(action.function);
            String functionData = getTransactionBytes(token, tokenId, action.function);
            if (functionData == null) return null;
            //function call may include some value
            String value = "0";
            if (action.function.tx != null && action.function.tx.args.containsKey("value"))
            {
                TokenscriptElement arg = action.function.tx.args.get("value");
                //resolve reference
                value = assetDefinitionService.resolveReference(token, action, arg, tokenId);
                Token currency = getCurrency(token.tokenInfo.chainId);
                functionEffect = BalanceUtils.getScaledValue(value, 18, Token.TOKEN_BALANCE_PRECISION)
                        + " " + currency.getSymbol() + " to " + action.function.method;
            }
            else
            {
                //Form full method representation
                functionEffect += "(";
                boolean firstArg = true;
                for (MethodArg arg : action.function.parameters)
                {
                    if (!firstArg) functionEffect += ", ";
                    firstArg = false;
                    if (arg.element.ref.equals("tokenId"))
                    {
                        functionEffect += "TokenId";
                    }
                    else
                    {
                        functionEffect += arg.element.value;
                    }
                }

                functionEffect += ")";
            }

            //finished resolving attributes, blank definition cache so definition is re-loaded when next needed
            getAssetDefinitionService().clearCache();

            return buildWeb3Transaction(functionData, cAddr.address, functionEffect, value);
        }
    }

    private Web3Transaction NativeSend(TSAction action, Token token, Context context)
    {
        boolean isValid = true;

        //calculate native amount
        BigDecimal value = new BigDecimal(action.function.tx.args.get("value").value);
        //this is a native send, so check the native currency
        Token currency = getCurrency(token.tokenInfo.chainId);

        if (currency.balance.subtract(value).compareTo(BigDecimal.ZERO) < 0)
        {
            //flash up dialog box insufficent funds
            insufficientFunds.postValue(currency);
            isValid = false;
        }

        //is 'to' overridden?
        String to = null;
        if (action.function.tx.args.get("to") != null)
        {
            to = action.function.tx.args.get("to").value;
        }
        else if (action.function.contract.addresses.get(token.tokenInfo.chainId) != null)
        {
            to = action.function.contract.addresses.get(token.tokenInfo.chainId).get(0);
        }

        if (!Utils.isAddressValid(to))
        {
            invalidAddress.postValue(to);
            isValid = false;
        }

        String valCorrected = BalanceUtils.getScaledValue(value, token.tokenInfo.decimals, Token.TOKEN_BALANCE_PRECISION);

        //eg Send 2(*1) ETH(*2) to Alex's Amazing Coffee House(*3) (0xdeadacec0ffee(*4))
        String extraInfo = String.format(context.getString(R.string.tokenscript_send_native), valCorrected, token.getSymbol(), action.function.method, to);

        //Clear the cache to refresh any resolved values
        getAssetDefinitionService().clearCache();

        if (isValid)
        {
            return new Web3Transaction(
                    new Address(to),
                    new Address(token.getAddress()),
                    value.toBigInteger(),
                    BigInteger.ZERO,
                    BigInteger.valueOf(C.GAS_LIMIT_MIN),
                    -1,
                    "0x",
                    extraInfo
            );
        }
        else
        {
            return null;
        }
    }

    private Web3Transaction buildWeb3Transaction(String functionData,
                                                 String contractAddress, String additionalDetails, String value)
    {
        return new Web3Transaction(
                new Address(contractAddress),
                new Address(contractAddress),
                new BigInteger(value),
                BigInteger.ZERO,
                BigInteger.ZERO, // notify that we need to calculate gaslimit
                -1,
                functionData,
                additionalDetails
        );
    }

    public void onDestroy()
    {
        if (calcGasCost != null && !calcGasCost.isDisposed())
        {
            calcGasCost.dispose();
        }
        if (openseaDisposable != null && !openseaDisposable.isDisposed())
        {
            openseaDisposable.dispose();
        }
        if (metadataDisposable != null && !metadataDisposable.isDisposed())
        {
            metadataDisposable.dispose();
        }
        if (scriptUpdate != null && !scriptUpdate.isDisposed())
        {
            scriptUpdate.dispose();
        }
        if (attrRefresh != null && !attrRefresh.isDisposed())
        {
            attrRefresh.dispose();
        }

        gasService.stopGasPriceCycle();
    }

    public OpenSeaService getOpenseaService()
    {
        return openseaService;
    }

    public void updateTokenScriptViewSize(Token token, int itemViewHeight)
    {
        assetDefinitionService.storeTokenViewHeight(token.tokenInfo.chainId, token.getAddress(), itemViewHeight);
    }

    public void checkForNewScript(Token token)
    {
        if (token == null) return;
        //check server for new TokenScript
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

    public boolean isAuthorizeToFunction()
    {
        return wallet.type != WalletType.WATCH;
    }

    public Wallet getWallet()
    {
        return wallet;
    }

    public Realm getRealmInstance(Wallet w)
    {
        return tokensService.getRealmInstance(w);
    }

    public TokensService getTokensService()
    {
        return tokensService;
    }

    public FetchTransactionsInteract getTransactionsInteract()
    {
        return fetchTransactionsInteract;
    }

    public Transaction fetchTransaction(String txHash)
    {
        return fetchTransactionsInteract.fetchCached(wallet.address, txHash);
    }

    public void estimateGasLimit(Web3Transaction w3tx, long chainId)
    {
        calcGasCost = gasService.calculateGasEstimate(Numeric.hexStringToByteArray(w3tx.payload), chainId, w3tx.contract.toString(), w3tx.value, wallet, BigInteger.ZERO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(estimate -> buildNewConfirmation(estimate, w3tx),
                        error -> {
                            buildNewConfirmation(new GasEstimate(BigInteger.ZERO), w3tx); //node didn't like this tx
                        });
    }

    private void buildNewConfirmation(GasEstimate estimate, Web3Transaction w3tx)
    {
        if (estimate.hasError())
        {
            gasEstimateError.postValue(new Pair<>(estimate, w3tx));
        }
        else
        {
            gasEstimateComplete.postValue(new Web3Transaction(
                    w3tx.recipient, w3tx.contract, w3tx.value, w3tx.gasPrice, estimate.getValue(), w3tx.nonce, w3tx.payload, w3tx.description));
        }
    }

    @Override
    public void showErc20TokenDetail(Activity context, @NotNull String address, String symbol, int decimals, @NotNull Token token)
    {
        boolean hasDefinition = assetDefinitionService.hasDefinition(token);
        Intent intent = new Intent(context, Erc20DetailActivity.class);
        intent.putExtra(C.EXTRA_SENDING_TOKENS, !token.isEthereum());
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address);
        intent.putExtra(C.EXTRA_SYMBOL, symbol);
        intent.putExtra(C.EXTRA_DECIMALS, decimals);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_ADDRESS, address);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition);
        context.startActivity(intent);
    }

    @Override
    public void showTokenList(Activity activity, Token token)
    {
        Intent intent = new Intent(activity, AssetDisplayActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.WALLET, wallet);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        activity.startActivityForResult(intent, C.TERMINATE_ACTIVITY);
    }

    public void restartServices()
    {
        fetchTransactionsInteract.restartTransactionService();
    }

    public void updateTokensCheck(Token token)
    {
        tokensService.setFocusToken(token);
    }

    public void clearFocusToken()
    {
        tokensService.clearFocusToken();
    }

    public TokensService getTokenService()
    {
        return tokensService;
    }

    public void actionSheetConfirm(String mode)
    {
        AnalyticsProperties props = new AnalyticsProperties();
        props.put(Analytics.PROPS_ACTION_SHEET_MODE, mode);
        track(Analytics.Action.ACTION_SHEET_COMPLETED, props);
    }

    public Single<Intent> showTransferSelectCount(Context ctx, Token token, BigInteger tokenId)
    {
        return genericWalletInteract.find()
                .map(wallet -> completeTransferSelect(ctx, token, tokenId, wallet));
    }

    private Intent completeTransferSelect(Context ctx, Token token, BigInteger tokenId, Wallet wallet)
    {
        Intent intent = new Intent(ctx, Erc1155AssetSelectActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_TOKEN_ID, tokenId.toString(16));
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public Intent getTransferIntent(Context ctx, Token token, List<BigInteger> tokenIds, ArrayList<NFTAsset> selection)
    {
        return completeTransferIntent(ctx, token, tokenIds, selection, getWallet());
    }

    private Intent completeTransferIntent(Context ctx, Token token, List<BigInteger> tokenIds, ArrayList<NFTAsset> selection, Wallet wallet)
    {
        Intent intent = new Intent(ctx, TransferNFTActivity.class);
        intent.putExtra(C.Key.WALLET, wallet.address);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_TOKENID_LIST, Utils.bigIntListToString(tokenIds, true));
        intent.putParcelableArrayListExtra(C.EXTRA_NFTASSET_LIST, selection);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private NFTAsset storeAsset(Token token, BigInteger tokenId, NFTAsset fetchedAsset, NFTAsset oldAsset)
    {
        fetchedAsset.updateFromRaw(oldAsset);
        tokensService.storeAsset(token, tokenId, fetchedAsset);
        token.addAssetToTokenBalanceAssets(tokenId, fetchedAsset);
        return fetchedAsset;
    }

    public void getTokenMetadata(Token token, BigInteger tokenId, NFTAsset oldAsset)
    {
        metadataDisposable = Single.fromCallable(() -> token.fetchTokenMetadata(tokenId))
                .map(fetchedAsset -> storeAsset(token, tokenId, fetchedAsset, oldAsset))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onAssetMetadata, this::onAssetMetadataError);
    }

    private void onAssetMetadataError(Throwable t)
    {
        Timber.w(t);
    }

    private void onAssetMetadata(NFTAsset asset)
    {
        nftAsset.postValue(asset);
    }

    private void loadExistingMetadata(Token token, BigInteger tokenId)
    {
        NFTAsset asset = token.getAssetForToken(tokenId);
        if (asset != null && !asset.needsLoading())
        {
            nftAsset.postValue(asset);
        }
    }

    public void getAsset(Token token, BigInteger tokenId)
    {
        loadExistingMetadata(token, tokenId);

        reloadMetadata(token, tokenId);
    }

    public void reloadMetadata(Token token, BigInteger tokenId)
    {
        openseaDisposable = openseaService.getAsset(token, tokenId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> onAsset(result, token, tokenId), this::onAssetError);
    }

    public void getCollection(Token token, BigInteger tokenId, NFTAsset asset, OpenSeaAsset osAsset)
    {
        openseaDisposable = openseaService.getCollection(token, osAsset.collection.slug)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> onCollection(token, tokenId, asset, osAsset, result), this::onAssetError);
    }

    private void onCollection(Token token, BigInteger tokenId, NFTAsset asset, OpenSeaAsset osAsset, String result)
    {
        NFTAsset oldAsset = token.getAssetForToken(tokenId);
        if (JsonUtils.isValidCollection(result))
        {
            try
            {
                JSONObject assetJson = new JSONObject(result);
                if (assetJson.has("collection"))
                {
                    String collectionData = assetJson.get("collection").toString();
                    OpenSeaAsset.Collection data = new Gson().fromJson(collectionData, OpenSeaAsset.Collection.class);
                    if (data != null)
                    {
                        osAsset.collection = data;
                    }
                }
            }
            catch (JSONException e)
            {
                Timber.w(e);
                Timber.d("Error fetching from OpenSea: %s", result);
            }
            catch (Exception e)
            {
                Timber.w(e);
            }
        }

        storeAsset(token, tokenId, asset, oldAsset);
        asset.attachOpenSeaAssetData(osAsset);
        nftAsset.postValue(asset);
    }

    public void completeTokenScriptSetup(Token token, BigInteger tokenId, String prevResult, TSAttrCallback tsCb)
    {
        final StringBuilder attrsTxt = new StringBuilder();
        final List<TokenScriptResult.Attribute> attrs = new ArrayList<>();

        if (hasTokenScript(token))
        {
            attrRefresh = assetDefinitionService.resolveAttrs(token, new ArrayList<>(Collections.singleton(tokenId)), null, UpdateType.USE_CACHE)
                    .filter(attr -> !attr.userInput)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(attr -> { attrs.add(attr); attrsTxt.append(attr.text); }, this::onError, () -> checkUpdatedAttrs(prevResult, attrsTxt, attrs, tsCb));
        }
    }

    private void checkUpdatedAttrs(String prevResult, final StringBuilder attrsText, List<TokenScriptResult.Attribute> attrs, TSAttrCallback tsCb)
    {
        tsCb.showTSAttributes(attrs, !prevResult.equals(attrsText.toString()));
    }

    private void onAssetError(Throwable throwable)
    {
        Timber.d(throwable);
    }

    private void onAsset(String result, Token token, BigInteger tokenId)
    {
        NFTAsset oldAsset = token.getAssetForToken(tokenId);
        boolean loadedFromApi = false;
        if (JsonUtils.isValidAsset(result))
        {
            try
            {
                JSONObject assetJson = new JSONObject(result);
                OpenSeaAsset osAsset = new Gson().fromJson(assetJson.toString(), OpenSeaAsset.class);
                NFTAsset asset = new NFTAsset(result);
                if (!TextUtils.isEmpty(asset.getImage()))
                {
                    loadedFromApi = true;

                    // If slug is available, check for more collection data
                    if (osAsset.collection != null && !TextUtils.isEmpty(osAsset.collection.slug))
                    {
                        getCollection(token, tokenId, asset, osAsset);
                    }
                    else
                    {
                        storeAsset(token, tokenId, asset, oldAsset);
                        asset.attachOpenSeaAssetData(osAsset);
                        nftAsset.postValue(asset);
                    }
                }
            }
            catch (JSONException e)
            {
                Timber.w(e);
                Timber.d("Error fetching from OpenSea: %s", result);
            }
            catch (Exception e)
            {
                Timber.w(e);
            }
        }

        if (!loadedFromApi)
        {
            getTokenMetadata(token, tokenId, oldAsset);
        }
    }

    public boolean hasTokenScript(Token token)
    {
        return token != null && assetDefinitionService.getAssetDefinition(token) != null;
    }

    public void updateLocalAttributes(Token token, BigInteger tokenId)
    {
        //Fetch Allowed attributes, then call updateAllowedAttributes
        assetDefinitionService.fetchFunctionMap(token, Collections.singletonList(tokenId), token.getInterfaceSpec(), UpdateType.USE_CACHE)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(availableActions -> updateAllowedAttrs(token, availableActions), this::onError)
                .isDisposed();
    }

    /** @noinspection SimplifyOptionalCallChains*/
    private void updateAllowedAttrs(Token token, Map<BigInteger, List<String>> availableActions)
    {
        if (!availableActions.keySet().stream().findFirst().isPresent())
        {
            return;
        }
        TokenDefinition td = assetDefinitionService.getAssetDefinition(token);
        if (td == null)
        {
            return;
        }
        List<Attribute> localAttrList = assetDefinitionService.getLocalAttributes(td, availableActions);

        //now refresh all these attrs
        assetDefinitionService.refreshAttributes(token, td, availableActions.keySet().stream().findFirst().get(), localAttrList)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> attrFetchComplete.postValue(td), this::onError)
                .isDisposed();
    }

    //Transaction handling

    public void getAuthentication(Activity activity, SignAuthenticationCallback callback)
    {
        genericWalletInteract.find()
                .subscribe(wallet -> keyService.getAuthenticationForSignature(wallet, activity, callback))
                .isDisposed();
    }

    public void getAuthentication(Activity activity, Wallet wallet, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void requestSignature(Web3Transaction finalTx, Wallet wallet, long chainId)
    {
        createTransactionInteract.requestSignature(finalTx, wallet, chainId, this);
    }

    public void sendTransaction(Wallet wallet, long chainId, Web3Transaction tx, SignatureFromKey signatureFromKey)
    {
        createTransactionInteract.sendTransaction(wallet, chainId, tx, signatureFromKey);
    }

    @Override
    public void transactionFinalised(TransactionReturn txData)
    {
        transactionFinalised.postValue(txData);
    }

    @Override
    public void transactionError(TransactionReturn rtn)
    {
        transactionError.postValue(rtn);
    }

    public void loadWallet(String address)
    {
        disposable = genericWalletInteract
                .findWallet(address)
                .subscribe(this::onDefaultWallet, this::onError);
    }

    public Single<Wallet> findWallet(String walletAddress)
    {
        return genericWalletInteract.findWallet(walletAddress);
    }

    public String addAttestationAttrs(NFTAsset asset, Token token, TSAction action)
    {
        StringBuilder attrs = new StringBuilder();
        if (asset != null && asset.isAttestation())
        {
            List<TokenScriptResult.Attribute> attestationAttrs = assetDefinitionService.getAttestationAttrs(token, action, asset.getAttestationID());
            if (attestationAttrs != null)
            {
                for (TokenScriptResult.Attribute attr : attestationAttrs)
                {
                    onAttr(attrs, attr);
                }
            }
        }

        return attrs.toString();
    }

    private void onAttr(StringBuilder attrs, TokenScriptResult.Attribute attribute)
    {
        //is the attr incomplete?
        if (!TextUtils.isEmpty(attribute.id))
        {
            Timber.d("ATTR/FA: " + attribute.id + " (" + attribute.name + ")" + " : " + attribute.text);
            TokenScriptResult.addPair(attrs, attribute);
        }
    }

    public GasService getGasService()
    {
        return gasService;
    }

    public boolean getUseTSViewer()
    {
        return preferences.getUseTSViewer();
    }
}
