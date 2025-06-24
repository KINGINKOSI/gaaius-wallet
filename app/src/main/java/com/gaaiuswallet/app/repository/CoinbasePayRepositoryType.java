package com.gaaiuswallet.app.repository;

import com.gaaiuswallet.app.entity.coinbasepay.DestinationWallet;

import java.util.List;

public interface CoinbasePayRepositoryType {
    String getUri(DestinationWallet.Type type, String json, List<String> list);
}
