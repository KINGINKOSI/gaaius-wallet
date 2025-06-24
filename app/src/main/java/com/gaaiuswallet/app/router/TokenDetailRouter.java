package com.gaaiuswallet.app.router;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.ui.AssetDisplayActivity;
import com.gaaiuswallet.app.ui.Erc20DetailActivity;
import com.gaaiuswallet.app.ui.NFTActivity;
import com.gaaiuswallet.app.ui.NFTAssetDetailActivity;

public class TokenDetailRouter
{
    public Intent makeERC20DetailsIntent(Context context, String address, String symbol, int decimals, boolean isToken, Wallet wallet, Token token, boolean hasDefinition)
    {
        Intent intent = new Intent(context, Erc20DetailActivity.class);
        intent.putExtra(C.EXTRA_SENDING_TOKENS, isToken);
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address);
        intent.putExtra(C.EXTRA_SYMBOL, symbol);
        intent.putExtra(C.EXTRA_DECIMALS, decimals);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition);
        return intent;
    }

    public void open(Activity context, String address, String symbol, int decimals, boolean isToken, Wallet wallet, Token token, boolean hasDefinition)
    {
        Intent intent = makeERC20DetailsIntent(context, address, symbol, decimals, isToken, wallet, token, hasDefinition);
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY);
    }

    public void open(Activity context, Token token, Wallet wallet, boolean hasDefinition)
    {
        Intent intent = new Intent(context, NFTActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.EXTRA_HAS_DEFINITION, hasDefinition);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY);
    }

    public void open(Activity activity, Token token, Wallet wallet)
    {
        Intent intent = new Intent(activity, NFTActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.WALLET, wallet);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        activity.startActivityForResult(intent, C.TERMINATE_ACTIVITY);
    }

    public void openLegacyToken(Activity context, Token token, Wallet wallet)
    {
        Intent intent = new Intent(context, AssetDisplayActivity.class);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress());
        intent.putExtra(C.Key.WALLET, wallet);
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivityForResult(intent, C.TERMINATE_ACTIVITY);
    }

    public void openAttestation(Activity context, Token token, Wallet wallet, NFTAsset asset)
    {
        Intent intent = new Intent(context, NFTAssetDetailActivity.class);
        intent.putExtra(C.Key.WALLET, wallet);
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        intent.putExtra(C.EXTRA_ADDRESS, token.tokenInfo.address);
        intent.putExtra(C.EXTRA_TOKEN_ID, token.getUUID().toString());
        intent.putExtra(C.EXTRA_ATTESTATION_ID, asset.getAttestationID());
        intent.putExtra(C.EXTRA_NFTASSET, asset);
        context.startActivityForResult(intent, C.TERMINATE_ACTIVITY);
    }
}
