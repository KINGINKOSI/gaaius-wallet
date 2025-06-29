package com.gaaiuswallet.app.ui.widget.holder;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.AdapterCallback;
import com.gaaiuswallet.app.entity.EventMeta;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.repository.EventResult;
import com.gaaiuswallet.app.repository.TokensRealmSource;
import com.gaaiuswallet.app.repository.entity.RealmAuxData;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.TransactionDetailActivity;
import com.gaaiuswallet.app.util.BalanceUtils;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.widget.TokenIcon;
import com.gaaiuswallet.token.entity.EventDefinition;
import com.gaaiuswallet.token.entity.TSTokenView;
import com.gaaiuswallet.token.tools.TokenDefinition;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static com.gaaiuswallet.app.service.AssetDefinitionService.ASSET_SUMMARY_VIEW_NAME;
import static com.gaaiuswallet.app.ui.widget.holder.TransactionHolder.DEFAULT_ADDRESS_ADDITIONAL;

/**
 * Created by JB on 28/07/2020.
 */
public class EventHolder extends BinderViewHolder<EventMeta> implements View.OnClickListener
{
    public static final int VIEW_TYPE = 2016;
    private final TokenIcon tokenIcon;
    private final TextView date;
    private final TextView type;
    private final TextView address;
    private final TextView value;
    private final AssetDefinitionService assetDefinition;
    private final AdapterCallback refreshSignaller;
    private Token token;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final TokensService tokensService;
    private String eventKey;
    private Transaction transaction;

    public EventHolder(ViewGroup parent, TokensService service, FetchTransactionsInteract interact,
                       AssetDefinitionService svs, AdapterCallback signaller)
    {
        super(R.layout.item_transaction, parent);
        date = findViewById(R.id.text_tx_time);
        tokenIcon = findViewById(R.id.token_icon);
        address = findViewById(R.id.address);
        type = findViewById(R.id.type);
        value = findViewById(R.id.value);
        tokensService = service;
        itemView.setOnClickListener(this);
        assetDefinition = svs;
        fetchTransactionsInteract = interact;
        refreshSignaller = signaller;
    }

    @Override
    public void bind(@Nullable EventMeta data, @NonNull Bundle addition)
    {
        String walletAddress = addition.getString(DEFAULT_ADDRESS_ADDITIONAL);
        //pull event details from DB
        eventKey = TokensRealmSource.eventActivityKey(data.hash, data.eventName);
        findViewById(R.id.token_name_detail).setVisibility(View.GONE);

        RealmAuxData eventData = fetchTransactionsInteract.fetchEvent(walletAddress, eventKey);
        transaction = fetchTransactionsInteract.fetchCached(walletAddress, data.hash);

        if (eventData == null || transaction == null)
        {
            // probably caused by a new script detected. Signal to holder we need a reset
            refreshSignaller.resetRequired();
            return;
        }
        token = tokensService.getToken(eventData.getChainId(), eventData.getTokenAddress());

        if (token == null) token = tokensService.getToken(data.chainId, walletAddress);
        String sym = token.getShortSymbol();
        tokenIcon.bindData(token);
        String itemView = null;

        TokenDefinition td = assetDefinition.getAssetDefinition(token);
        if (td != null && td.getActivityCards().containsKey(eventData.getFunctionId()))
        {
            TSTokenView view = td.getActivityCards().get(eventData.getFunctionId()).getView(ASSET_SUMMARY_VIEW_NAME);
            if (view != null) itemView = view.getTokenView();
        }

        String transactionValue = getEventAmount(eventData, transaction);

        if (TextUtils.isEmpty(transactionValue))
        {
            value.setVisibility(View.GONE);
        }
        else
        {
            value.setText(getString(R.string.valueSymbol, transactionValue, sym));
        }

        CharSequence typeValue = Utils.createFormattedValue(getTitle(eventData), token);

        type.setText(typeValue);
        //symbol.setText(sym);
        address.setText(eventData.getDetail(getContext(), transaction, itemView));// getDetail(eventData, resultMap));
        tokenIcon.setStatusIcon(eventData.getEventStatusType());
        tokenIcon.setChainIcon(token.tokenInfo.chainId);

        //timestamp
        date.setText(Utils.localiseUnixTime(getContext(), eventData.getResultTime()));
        date.setVisibility(View.VISIBLE);
    }

    private String getEventAmount(RealmAuxData eventData, Transaction tx)
    {
        Map<String, EventResult> resultMap = eventData.getEventResultMap();
        int decimals = token != null ? token.tokenInfo.decimals : C.ETHER_DECIMALS;
        String value = "";
        switch (eventData.getFunctionId())
        {
            case "received":
            case "sent":
                if (resultMap.get("amount") != null)
                {
                    value = eventData.getFunctionId().equals("sent") ? "- " : "+ ";
                    value += BalanceUtils.getScaledValueFixed(new BigDecimal(resultMap.get("amount").value),
                            decimals, 4);
                }
                break;
            case "approvalObtained":
            case "ownerApproved":
                if (resultMap.get("value") != null)
                {
                    value = BalanceUtils.getScaledValueFixed(new BigDecimal(resultMap.get("value").value),
                            decimals, 4);
                }
                break;
            default:
                if (token != null && tx != null)
                {
                    value = token.isEthereum() ? token.getTransactionValue(tx, 4) : tx.getOperationResult(token, 4);
                }
                break;
        }

        return value;
    }

    private String getTitle(RealmAuxData eventData)
    {
        //TODO: pick up item-view
        return eventData.getTitle(getContext());
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
        intent.putExtra(C.EXTRA_TXHASH, transaction.hash);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        getContext().startActivity(intent);
    }
}
