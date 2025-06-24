package com.gaaiuswallet.app.repository;

import android.util.Pair;

import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.ImageEntry;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokendata.TokenGroup;
import com.gaaiuswallet.app.entity.tokendata.TokenTicker;
import com.gaaiuswallet.app.entity.tokens.Attestation;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.entity.tokens.TokenCardMeta;
import com.gaaiuswallet.app.entity.tokens.TokenInfo;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.token.entity.ContractAddress;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.reactivex.Single;
import io.realm.Realm;

public interface TokenLocalSource
{
    Single<Token> saveToken(Wallet wallet, Token token);

    Single<Token[]> saveTokens(Wallet wallet, Token[] items);

    boolean updateTokenBalance(Wallet wallet, Token token, BigDecimal balance, List<BigInteger> balanceArray);

    Token fetchToken(long chainId, Wallet wallet, String address);

    void setEnable(Wallet wallet, ContractAddress cAddr, boolean isEnabled);

    String getTokenImageUrl(long chainId, String address);

    void deleteRealmTokens(Wallet wallet, List<TokenCardMeta> tcmList);

    void storeTokenUrl(List<ImageEntry> entries);

    Token initNFTAssets(Wallet wallet, Token tokens);

    Single<TokenCardMeta[]> fetchTokenMetas(Wallet wallet, List<Long> networkFilters,
                                            AssetDefinitionService svs);

    Single<TokenCardMeta[]> fetchAllTokenMetas(Wallet wallet, List<Long> networkFilters,
                                               String seachTerm);

    TokenCardMeta[] fetchTokenMetasForUpdate(Wallet wallet, List<Long> networkFilters);

    Single<Token[]> fetchAllTokensWithNameIssue(String walletAddress, List<Long> networkFilters);

    Single<ContractAddress[]> fetchAllTokensWithBlankName(String walletAddress, List<Long> networkFilters);

    Single<Integer> fixFullNames(Wallet wallet, AssetDefinitionService svs);

    void updateEthTickers(Map<Long, TokenTicker> ethTickers);

    void updateERC20Tickers(long chainId, Map<String, TokenTicker> erc20Tickers);

    void removeOutdatedTickers();

    Realm getRealmInstance(Wallet wallet);

    Realm getTickerRealmInstance();

    TokenTicker getCurrentTicker(Token token);

    TokenTicker getCurrentTicker(String key);

    void setVisibilityChanged(Wallet wallet, ContractAddress cAddr);

    boolean getEnabled(Token token);

    void updateNFTAssets(String wallet, Token erc721Token, List<BigInteger> additions, List<BigInteger> removals);

    void storeAsset(String wallet, Token token, BigInteger tokenId, NFTAsset asset);

    Single<Pair<Double, Double>> getTotalValue(String currentAddress, List<Long> networkFilters);

    Map<String, Long> getTickerTimeMap(long chainId, List<TokenCardMeta> erc20Tokens);

    void deleteTickers();

    Single<List<String>> getTickerUpdateList(List<Long> networkFilter);

    TokenGroup getTokenGroup(long chainId, String address, ContractType type);

    void updateTicker(long chainId, String address, TokenTicker ticker);
    Single<TokenInfo> storeTokenInfo(Wallet wallet, TokenInfo tInfo, ContractType type);

    Token fetchAttestation(long chainId, Wallet wallet, String address, String attnId);

    List<Token> fetchAttestations(long chainId, String walletAddress, String tokenAddress);
    List<Token> fetchAttestations(String walletAddress);
}
