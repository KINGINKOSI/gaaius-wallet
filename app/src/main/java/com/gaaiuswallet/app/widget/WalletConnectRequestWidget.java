package com.gaaiuswallet.app.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.app.ui.widget.entity.WalletConnectWidgetCallback;
import com.gaaiuswallet.app.walletconnect.entity.WCPeerMeta;

public class WalletConnectRequestWidget extends LinearLayout
{

    private long chainIdOverride;
    private WalletConnectWidgetCallback callback;

    private DialogInfoItem website;
    private DialogInfoItem network;

    public WalletConnectRequestWidget(Context context, AttributeSet attributeSet)
    {
        super(context, attributeSet);
        inflate(context, R.layout.item_wallet_connect_request, this);
        website = findViewById(R.id.info_website);
        network = findViewById(R.id.info_network);
    }

    public void setupWidget(WCPeerMeta wcPeerMeta, long chainId, WalletConnectWidgetCallback callback)
    {
        this.chainIdOverride = chainId;
        this.callback = callback;

        website.setLabel(getContext().getString(R.string.website_text));
        website.setMessage(wcPeerMeta.getUrl());

        network.setLabel(getContext().getString(R.string.subtitle_network));
        network.setMessage(EthereumNetworkBase.getShortChainName(chainIdOverride));
        network.setMessageTextColor(EthereumNetworkBase.getChainColour(chainIdOverride));
        network.setActionText(getContext().getString(R.string.edit));

        network.setActionListener(v -> {
            callback.openChainSelection();
        });
    }

    public void updateChain(long chainIdOverride)
    {
        this.chainIdOverride = chainIdOverride;
        network.setMessage(EthereumNetworkBase.getShortChainName(chainIdOverride));
        network.setMessageTextColor(EthereumNetworkBase.getChainColour(chainIdOverride));
    }

    public long getChainIdOverride()
    {
        return chainIdOverride;
    }
}
