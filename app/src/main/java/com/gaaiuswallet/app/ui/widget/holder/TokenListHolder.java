package com.gaaiuswallet.app.ui.widget.holder;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.tokens.TokenCardMeta;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.widget.OnTokenManageClickListener;
import com.gaaiuswallet.app.widget.TokenIcon;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class TokenListHolder extends BinderViewHolder<TokenCardMeta> implements View.OnClickListener, CompoundButton.OnCheckedChangeListener
{

    final RelativeLayout layout;
    final TextView tokenName;
    final SwitchMaterial switchEnabled;
    final TokenIcon tokenIcon;
    //need to cache this locally, unless we cache every string we need in the constructor
    private final AssetDefinitionService assetDefinition;
    private final TokensService tokensService;
    public Token token;
    public TokenCardMeta data;
    public int position;
    long chainId;
    private OnTokenManageClickListener onTokenClickListener;

    public TokenListHolder(int resId, ViewGroup parent, AssetDefinitionService assetService, TokensService tokensService)
    {
        super(resId, parent);

        this.assetDefinition = assetService;
        this.tokensService = tokensService;

        layout = itemView.findViewById(R.id.layout_list_item);
        tokenName = itemView.findViewById(R.id.name);
        switchEnabled = itemView.findViewById(R.id.switch_enabled);
        tokenIcon = itemView.findViewById(R.id.token_icon);

        layout.setOnClickListener(this);
        itemView.setOnClickListener(this);
    }

    @Override
    public void bind(@Nullable TokenCardMeta data, @NonNull Bundle addition)
    {
        this.data = data;
        position = addition.getInt("position");
        token = tokensService.getToken(data.getChain(), data.getAddress());

        if (token == null)
        {
            bindEmptyText(data);
            return;
        }

        tokenName.setText(token.getFullName(assetDefinition, 1));
        chainId = token.tokenInfo.chainId;
        switchEnabled.setOnCheckedChangeListener(null);
        switchEnabled.setChecked(data.isEnabled);
        switchEnabled.setOnCheckedChangeListener(this);
        tokenIcon.bindData(token);

        if (data.isEnabled)
        {
            layout.setAlpha(1.0f);
        }
        else
        {
            layout.setAlpha(0.4f);
        }
    }

    private void bindEmptyText(TokenCardMeta data)
    {
        tokenIcon.setVisibility(View.GONE);
        tokenName.setText(data.balance);
        switchEnabled.setOnCheckedChangeListener(null);
        switchEnabled.setChecked(data.isEnabled);
        switchEnabled.setOnCheckedChangeListener(this);
    }

    @Override
    public void onClick(View v)
    {
        switchEnabled.setChecked(!switchEnabled.isChecked());
    }

    public void setOnTokenClickListener(OnTokenManageClickListener onTokenClickListener)
    {
        this.onTokenClickListener = onTokenClickListener;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked)
    {
        if (onTokenClickListener != null)
        {
            onTokenClickListener.onTokenClick(token, position, isChecked);
        }
    }
}
