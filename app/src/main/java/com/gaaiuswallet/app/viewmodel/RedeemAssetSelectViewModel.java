package com.gaaiuswallet.app.viewmodel;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.router.RedeemSignatureDisplayRouter;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.widget.entity.TicketRangeParcel;
import com.gaaiuswallet.token.entity.TicketRange;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Created by James on 27/02/2018.
 */
@HiltViewModel
public class RedeemAssetSelectViewModel extends BaseViewModel {
    private final RedeemSignatureDisplayRouter redeemSignatureDisplayRouter;
    private final AssetDefinitionService assetDefinitionService;
    private final TokensService tokensService;
    private final GenericWalletInteract genericWalletInteract;

    @Inject
    public RedeemAssetSelectViewModel(
            RedeemSignatureDisplayRouter redeemSignatureDisplayRouter,
            AssetDefinitionService assetDefinitionService,
            TokensService tokensService,
            GenericWalletInteract genericWalletInteract)
    {
        this.redeemSignatureDisplayRouter = redeemSignatureDisplayRouter;
        this.assetDefinitionService = assetDefinitionService;
        this.tokensService = tokensService;
        this.genericWalletInteract = genericWalletInteract;
    }

    public void showRedeemSignature(Context ctx, TicketRange range, Token token)
    {
        genericWalletInteract.find()
                .subscribe(wallet -> onWallet(wallet, ctx, range, token), this::onError)
                .isDisposed();
    }

    private void onWallet(Wallet wallet, Context ctx, TicketRange range, Token token)
    {
        TicketRangeParcel parcel = new TicketRangeParcel(range);
        redeemSignatureDisplayRouter.open(ctx, wallet, token, parcel);
    }

    public TokensService getTokensService() { return tokensService; }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }
}
