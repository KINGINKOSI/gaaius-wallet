package com.gaaiuswallet.app.router;

import android.content.Context;
import android.content.Intent;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.ui.RedeemAssetSelectActivity;
import com.gaaiuswallet.app.entity.tokens.Token;

/**
 * Created by James on 27/02/2018.
 */

public class RedeemAssetSelectRouter
{
    public void open(Context context, Token token) {
        Intent intent = new Intent(context, RedeemAssetSelectActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }
}
