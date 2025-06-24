package com.gaaiuswallet.app.entity;
import com.gaaiuswallet.app.entity.cryptokeys.KeyEncodingType;
import com.gaaiuswallet.app.service.KeyService;

public interface ImportWalletCallback
{
    void walletValidated(String address, KeyEncodingType type, KeyService.AuthenticationLevel level);
}
