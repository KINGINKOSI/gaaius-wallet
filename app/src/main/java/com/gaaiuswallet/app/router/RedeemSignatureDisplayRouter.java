package com.gaaiuswallet.app.router;

import android.content.Context;
import android.content.Intent;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.ui.RedeemSignatureDisplayActivity;
import com.gaaiuswallet.app.ui.widget.entity.TicketRangeParcel;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.Wallet;

/**
 * Created by James on 25/01/2018.
 */

public class RedeemSignatureDisplayRouter {
    public void open(Context context, Wallet wallet, Token token, TicketRangeParcel range) {
        Intent intent = new Intent(context, RedeemSignatureDisplayActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.TICKET_RANGE, range);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}