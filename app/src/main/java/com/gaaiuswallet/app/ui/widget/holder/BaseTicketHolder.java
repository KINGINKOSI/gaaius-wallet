package com.gaaiuswallet.app.ui.widget.holder;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.ui.widget.TokensAdapterCallback;
import com.gaaiuswallet.app.web3.Web3TokenView;
import com.gaaiuswallet.app.web3.entity.PageReadyCallback;
import com.gaaiuswallet.token.entity.TicketRange;
import com.gaaiuswallet.token.entity.ViewType;

public class BaseTicketHolder extends BinderViewHolder<TicketRange> implements View.OnClickListener, View.OnLongClickListener, PageReadyCallback
{
    private TicketRange thisData;
    private final Token token;
    private final Web3TokenView tokenView;
    private final LinearLayout webWrapper;
    private TokensAdapterCallback tokensAdapterCallback;
    private final AssetDefinitionService assetService; //need to cache this locally, unless we cache every string we need in the constructor

    protected final RelativeLayout ticketLayout;

    public BaseTicketHolder(int resId, ViewGroup parent, Token ticket, AssetDefinitionService service) {
        super(resId, parent);

        tokenView = findViewById(R.id.web3_tokenview);
        webWrapper = findViewById(R.id.layout_webwrapper);
        itemView.setOnClickListener(this);
        ticketLayout = findViewById(R.id.layout_select_ticket);
        assetService = service;
        token = ticket;
        tokenView.setChainId(ticket.tokenInfo.chainId);
        tokenView.setOnReadyCallback(this);
    }

    @Override
    public void bind(@Nullable TicketRange data, @NonNull Bundle addition)
    {
        this.thisData = data;

        if (data.tokenIds.size() > 0)
        {
            tokenView.displayTicketHolder(token, data, assetService, ViewType.ITEM_VIEW);
        }
    }

    @Override
    public void onClick(View v) {
        if (tokensAdapterCallback != null) {
            tokensAdapterCallback.onTokenClick(v, token, thisData.tokenIds, true);
        }
    }

    public void setOnTokenClickListener(TokensAdapterCallback tokensAdapterCallback) {
        this.tokensAdapterCallback = tokensAdapterCallback;
    }

    @Override
    public boolean onLongClick(View view)
    {
        if (tokensAdapterCallback != null)
        {
            tokensAdapterCallback.onLongTokenClick(view, token, thisData.tokenIds);
        }

        return true;
    }

    @Override
    public void onPageLoaded(WebView view)
    {

    }

    @Override
    public void onPageRendered(WebView view)
    {
        webWrapper.setVisibility(View.VISIBLE);
    }
}
