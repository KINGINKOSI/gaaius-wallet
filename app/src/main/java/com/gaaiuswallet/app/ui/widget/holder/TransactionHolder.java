package com.gaaiuswallet.app.ui.widget.holder;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.TransactionMeta;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.TransactionDetailActivity;
import com.gaaiuswallet.app.ui.widget.entity.StatusType;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.widget.TokenIcon;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.token.entity.ContractAddress;

import java.util.Locale;

public class TransactionHolder extends BinderViewHolder<TransactionMeta> implements View.OnClickListener
{
    public static final int VIEW_TYPE = 1003;

    public static final int TRANSACTION_BALANCE_PRECISION = 4;

    public static final String DEFAULT_ADDRESS_ADDITIONAL = "default_address";

    private final TokenIcon tokenIcon;
    private final TextView date;
    private final TextView type;
    private final TextView address;
    private final TextView value;
    private final TextView supplemental;
    private final TokensService tokensService;
    private final FetchTransactionsInteract transactionsInteract;

    private Transaction transaction;
    private String defaultAddress;

    public TransactionHolder(ViewGroup parent, TokensService service, FetchTransactionsInteract interact)
    {
        super(R.layout.item_transaction, parent);
        date = findViewById(R.id.text_tx_time);
        tokenIcon = findViewById(R.id.token_icon);
        address = findViewById(R.id.address);
        type = findViewById(R.id.type);
        value = findViewById(R.id.value);
        supplemental = findViewById(R.id.supplimental);
        tokensService = service;
        transactionsInteract = interact;
        itemView.setOnClickListener(this);
    }

    @Override
    public void bind(@Nullable TransactionMeta data, @NonNull Bundle addition)
    {
        defaultAddress = addition.getString(DEFAULT_ADDRESS_ADDITIONAL);
        supplemental.setText("");

        //fetch data from database
        transaction = transactionsInteract.fetchCached(defaultAddress, data.hash);

        if (this.transaction == null)
        {
            return;
        }

        value.setVisibility(View.VISIBLE);

        Token token = getOperationToken();
        if (token == null) return;

        String operationName = token.getOperationName(transaction, getContext());

        String transactionOperation = token.getTransactionResultValue(transaction, TRANSACTION_BALANCE_PRECISION);
        boolean shouldShowToken = token.shouldShowSymbol(transaction);
        value.setText(transactionOperation);
        CharSequence typeValue = Utils.createFormattedValue(operationName, shouldShowToken ? token : null);
        type.setText(typeValue);
        //set address or contract name
        setupTransactionDetail(token);

        //Display further token details if required
        setTokenDetailName(token);

        //set colours and up/down arrow
        tokenIcon.bindData(token);
        tokenIcon.setStatusIcon(token.getTxStatus(transaction));
        tokenIcon.setChainIcon(token.tokenInfo.chainId);

        String supplementalTxt = transaction.getSupplementalInfo(token.getWallet(), EthereumNetworkBase.getChainSymbol(token.tokenInfo.chainId));
        supplemental.setText(supplementalTxt);
        supplemental.setTextColor(getContext().getColor(transaction.getSupplementalColour(supplementalTxt)));

        date.setText(Utils.localiseUnixTime(getContext(), transaction.timeStamp));
        date.setVisibility(View.VISIBLE);

        setTransactionStatus(transaction.blockNumber, transaction.error, transaction.isPending());
    }

    private void setupTransactionDetail(Token token)
    {
        String detailStr = token.getTransactionDetail(getContext(), transaction, tokensService);
        address.setText(detailStr);
    }

    private Token getOperationToken()
    {
        String operationAddress = transaction.getOperationTokenAddress();
        Token operationToken = tokensService.getToken(transaction.chainId, operationAddress);

        if (operationToken == null)
        {
            operationToken = tokensService.getToken(transaction.chainId, defaultAddress);
            tokensService.addUnknownTokenToCheck(new ContractAddress(transaction.chainId, operationAddress));
        }

        return operationToken;
    }

    @Override
    public void onClick(View view)
    {
        Intent intent = new Intent(getContext(), TransactionDetailActivity.class);
        intent.putExtra(C.EXTRA_TXHASH, transaction.hash);
        intent.putExtra(C.EXTRA_CHAIN_ID, getOperationToken().tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, getOperationToken().getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        getContext().startActivity(intent);
    }

    private void setFailed()
    {
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) supplemental.getLayoutParams();
        layoutParams.setMarginStart(10);
        String failure = getString(R.string.failed) + " ☹";
        supplemental.setText(failure);
        supplemental.setTextColor(ContextCompat.getColor(getContext(), R.color.error));
    }

    private void setTransactionStatus(String blockNumber, String error, boolean isPending)
    {
        if (error != null && error.equals("1"))
        {
            setFailed();
            tokenIcon.setStatusIcon(StatusType.FAILED);
        }

        //Handle displaying the transaction item as pending or completed
        if (blockNumber.equals("-1"))
        {
            setFailed();
            tokenIcon.setStatusIcon(StatusType.REJECTED);
            address.setText(R.string.tx_rejected);
        }
        else if (isPending)
        {
            tokenIcon.setStatusIcon(StatusType.PENDING);
            type.setText(R.string.pending_transaction);
        }
    }
}
