package com.gaaiuswallet.app.web3;

import com.gaaiuswallet.token.entity.EthereumMessage;

public interface OnSignMessageListener {
    void onSignMessage(EthereumMessage message);
}
