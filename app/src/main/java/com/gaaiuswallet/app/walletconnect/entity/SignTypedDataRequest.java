package com.gaaiuswallet.app.walletconnect.entity;

import com.gaaiuswallet.app.entity.CryptoFunctions;
import com.gaaiuswallet.token.entity.EthereumTypedMessage;
import com.gaaiuswallet.token.entity.Signable;

public class SignTypedDataRequest extends BaseRequest
{
    public SignTypedDataRequest(String params)
    {
        super(params, WCEthereumSignMessage.WCSignType.TYPED_MESSAGE);
    }

    public String getWalletAddress()
    {
        return params.get(0);
    }

    public Signable getSignable()
    {
        return new EthereumTypedMessage(getMessage(), "", 0, new CryptoFunctions());
    }

    @Override
    public Signable getSignable(long callbackId, String origin)
    {
        return new EthereumTypedMessage(getMessage(), origin, callbackId, new CryptoFunctions());
    }
}
