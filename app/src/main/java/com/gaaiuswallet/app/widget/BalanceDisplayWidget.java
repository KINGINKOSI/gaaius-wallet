package com.gaaiuswallet.app.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.util.BalanceUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Created by Jenny Jingjing Li on 4/3/2021
 */

public class BalanceDisplayWidget extends LinearLayout
{
    public final TextView balance;
    public final TextView newBalance;
    private final TokenIcon tokenIcon;
    private Transaction transaction;

    public BalanceDisplayWidget(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        inflate(context, R.layout.item_balance_display, this);
        balance = findViewById(R.id.text_balance);
        newBalance = findViewById(R.id.text_new_balance);
        tokenIcon = findViewById(R.id.token_icon);
    }

    public void setupBalance(Token token, Transaction tx)
    {
        if (token.isNonFungible())
        {
            tokenIcon.setVisibility(View.VISIBLE);
            tokenIcon.bindData(token);
        }
        else
        {
            tokenIcon.setVisibility(View.GONE);
            balance.setText(getContext().getString(R.string.total_cost, token.getStringBalanceForUI(5), token.getSymbol()));
        }
        transaction = tx;
    }

    public void setNewBalanceText(Token token, BigDecimal transactionAmount, BigInteger networkFee, BigInteger balanceAfterTransaction)
    {
        if (token.isEthereum())
        {
            balanceAfterTransaction = balanceAfterTransaction.subtract(networkFee).max(BigInteger.ZERO);
        }
        else if (transaction == null || transaction.transactionInput == null || transaction.transactionInput.isSendOrReceive(transaction))
        {
            balanceAfterTransaction = token.getBalanceRaw().subtract(transactionAmount).toBigInteger();
        }
        else
        {
            newBalance.setVisibility(View.GONE);
            return;
        }

        //convert to ETH amount
        String newBalanceVal = BalanceUtils.getScaledValueScientific(new BigDecimal(balanceAfterTransaction), token.tokenInfo.decimals);
        newBalance.setText(getContext().getString(R.string.new_balance, newBalanceVal, token.getSymbol()));
    }
}
