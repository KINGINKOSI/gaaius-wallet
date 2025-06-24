package com.gaaiuswallet.app.service;

import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.entity.Transaction;
import com.gaaiuswallet.app.entity.TransactionMeta;
import com.gaaiuswallet.app.entity.transactionAPI.TransferFetchType;
import com.gaaiuswallet.app.entity.transactions.TransferEvent;

import java.util.List;
import java.util.Map;

import io.reactivex.Single;

public interface TransactionsNetworkClientType
{
    Single<Transaction[]> storeNewTransactions(TokensService svs, NetworkInfo networkInfo, String tokenAddress, long lastBlock);

    Single<TransactionMeta[]> fetchMoreTransactions(TokensService svs, NetworkInfo network, long lastTxTime);

    Single<Map<String, List<TransferEvent>>> readTransfers(String currentAddress, NetworkInfo networkByChain, TokensService tokensService, TransferFetchType tfType);

    void checkRequiresAuxReset(String walletAddr);
}
