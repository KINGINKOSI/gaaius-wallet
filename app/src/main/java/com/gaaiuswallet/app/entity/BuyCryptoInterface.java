package com.gaaiuswallet.app.entity;

import com.gaaiuswallet.app.entity.tokens.Token;

public interface BuyCryptoInterface {
    void handleBuyFunction(Token token);
    void handleGeneratePaymentRequest(Token token);
}
