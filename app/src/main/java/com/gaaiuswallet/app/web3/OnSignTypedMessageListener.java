package com.gaaiuswallet.app.web3;


import com.gaaiuswallet.token.entity.EthereumTypedMessage;

public interface OnSignTypedMessageListener {
    void onSignTypedMessage(EthereumTypedMessage message);
}
