package com.gaaiuswallet.app.ui.widget;

import com.gaaiuswallet.app.entity.tokens.Token;

public interface OnTokenManageClickListener
{
    void onTokenClick(Token token, int position, boolean isChecked);
}
