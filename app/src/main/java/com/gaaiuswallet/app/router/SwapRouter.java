package com.gaaiuswallet.app.router;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.ui.Erc20DetailActivity;
import com.gaaiuswallet.app.ui.NFTActivity;
import com.gaaiuswallet.app.ui.SwapActivity;

public class SwapRouter
{
    public void open(Activity context, Token token, Wallet wallet)
    {
        Intent intent = new Intent(context, SwapActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY);
    }
}
