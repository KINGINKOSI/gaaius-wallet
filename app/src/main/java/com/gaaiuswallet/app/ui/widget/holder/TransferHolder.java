package com.gaaiuswallet.app.ui.widget.holder;

import static com.gaaiuswallet.app.service.AssetDefinitionService.ASSET_SUMMARY_VIEW_NAME;
import static com.gaaiuswallet.app.ui.widget.holder.TransactionHolder.DEFAULT_ADDRESS_ADDITIONAL;
import static com.gaaiuswallet.app.ui.widget.holder.TransactionHolder.TRANSACTION_BALANCE_PRECISION;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.repository.EventResult;
import com.gaaiuswallet.app.repository.entity.RealmAuxData;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.TransactionDetailActivity;
import com.gaaiuswallet.app.ui.widget.entity.TokenTransferData;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.widget.TokenIcon;
import com.gaaiuswallet.token.entity.EventDefinition;
import com.gaaiuswallet.token.entity.TSTokenView;
import com.gaaiuswallet.token.tools.TokenDefinition;

import java.math.BigInteger;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by JB on 17/12/2020.
 */
public class TransferHolder extends BinderViewHolder<TokenTransferData> implements View.OnClickListener
{
    public static final int VIEW_TYPE = 2017;
    private final TokenIcon tokenIcon;
    private final TextView date;
    private final TextView type;
    private final TextView address;
    private final TextView value;
    private final ProgressBar txLoad;
    private final AssetDefinitionService assetDefinition;
    private Token token;
    private TokenTransferData transferData;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final TokensService tokensService;
    private String hashKey;

    @Nullable
    private Disposable disposable;

    public TransferHolder(ViewGroup parent, TokensService service, FetchTransactionsInteract interact,
                          AssetDefinitionService svs)
    {
        super(R.layout.item_transaction, parent);
        date = findViewById(R.id.text_tx_time);
        tokenIcon = findViewById(R.id.token_icon);
        address = findViewById(R.id.address);
        type = findViewById(R.id.type);
        value = findViewById(R.id.value);
        txLoad = findViewById(R.id.loading_transaction);
        tokensService = service;
        itemView.setOnClickListener(this);
        assetDefinition = svs;

        fetchTransactionsInteract = interact;
    }

    @Override
    public void bind(@Nullable TokenTransferData data, @NonNull Bundle addition)
    {
        transferData = data;
        String walletAddress = addition.getString(DEFAULT_ADDRESS_ADDITIONAL);

        token = tokensService.getToken(data.chainId, data.tokenAddress);
        if (token == null)
        {
            token = tokensService.getToken(data.chainId, walletAddress);
        }

        //pull event details from DB
        Transaction tx = fetchTransactionsInteract.fetchCached(walletAddress, data.hash);

        if (disposable != null && !disposable.isDisposed())
        {
            disposable.dispose();
        }

        tokenIcon.bindData(token);

        //We haven't yet fetched the underlying transaction. Fetch and display
        if (tx == null)
        {
            txLoad.setVisibility(View.VISIBLE);
            //load the transaction and restart the bind.
            disposable = fetchTransactionsInteract.fetchFromNode(walletAddress, data.chainId, data.hash)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(fetchedTransaction -> bindView(data, fetchedTransaction),
                            err -> bindView(data, null)); //Can still build limited view with no fetched tx - better than a blank view
        }
        else
        {
            bindView(data, tx);
        }
    }

    private void bindView(TokenTransferData data, Transaction tx)
    {
        txLoad.setVisibility(View.GONE);
        findViewById(R.id.token_name_detail).setVisibility(View.GONE);

        String sym = token != null ? token.getShortSymbol() : getContext().getString(R.string.eth);
        String itemView = null;

        if (data.getTimeStamp() % 1000 != 0)
        {
            findViewById(R.id.layout_background).setLabelFor(VIEW_TYPE);
        }
        else
        {
            findViewById(R.id.layout_background).setLabelFor(0);
        }

        TokenDefinition td = assetDefinition.getAssetDefinition(data.chainId, data.tokenAddress);
        if (td != null && td.getActivityCards().containsKey(data.eventName))
        {
            TSTokenView view = td.getActivityCards().get(data.eventName).getView(ASSET_SUMMARY_VIEW_NAME);
            if (view != null) itemView = view.getTokenView();
        }

        String transactionValue = transferData.getEventAmount(token, tx);

        if (TextUtils.isEmpty(transactionValue))
        {
            value.setVisibility(View.GONE);
        }
        else
        {
            value.setText(getString(R.string.valueSymbol, transactionValue, sym));
        }

        CharSequence typeValue = Utils.createFormattedValue(getTitle(data), token);

        type.setText(typeValue);
        address.setText(data.getDetail(getContext(), tx, token, itemView));
        tokenIcon.setStatusIcon(data.getEventStatusType());
        tokenIcon.setChainIcon(token.tokenInfo.chainId);

        //check if this is a mint, in which case we already display token name
        if (!data.isMintEvent())
        {
            setTokenDetailName(token);
        }

        //timestamp
        date.setText(Utils.localiseUnixTime(getContext(), data.getTimeStampSeconds()));
        date.setVisibility(View.VISIBLE);

        hashKey = data.hash;
    }

    private String getTitle(TokenTransferData eventData)
    {
        //TODO: pick up item-view
        int titleResource = eventData.getTitle();
        if (titleResource == 0)
        {
            return eventData.eventName;
        }
        else
        {
            return getContext().getString(titleResource);
        }
    }

    private BigInteger getTokenId(TokenDefinition td, RealmAuxData eventData)
    {
        //pull tokenId
        if (token != null && token.isNonFungible() && td != null)
        {
            EventDefinition ev = td.getEventDefinition(eventData.getFunctionId());
            if (ev != null && ev.getFilterTopicValue().equals("tokenId"))
            {
                //filter topic is tokenId, therefore this event refers to a specific tokenId
                //isolate the tokenId
                Map<String, EventResult> resultMap = eventData.getEventResultMap();
                String filterIndexName = ev.getFilterTopicIndex();
                if (resultMap.containsKey(filterIndexName))
                {
                    return new BigInteger(resultMap.get(filterIndexName).value);
                }
            }
        }

        return BigInteger.ZERO;
    }

    @Override
    public void onClick(View view)
    {
        Intent intent = new Intent(getContext(), TransactionDetailActivity.class);
        intent.putExtra(C.EXTRA_TXHASH, hashKey);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_TRANSACTION_DATA, transferData);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        getContext().startActivity(intent);
    }
}
