package com.gaaiuswallet.app.repository;

import com.gaaiuswallet.app.entity.OnRampContract;
import com.gaaiuswallet.app.entity.tokens.Token;

public interface OnRampRepositoryType {
    String getUri(String address, Token token);

    OnRampContract getContract(Token token);
}
