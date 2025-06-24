package com.gaaiuswallet.app.di;

import static com.gaaiuswallet.app.service.KeystoreAccountService.KEYSTORE_FOLDER;

import android.content.Context;

import com.gaaiuswallet.app.repository.CoinbasePayRepository;
import com.gaaiuswallet.app.repository.CoinbasePayRepositoryType;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.OnRampRepository;
import com.gaaiuswallet.app.repository.OnRampRepositoryType;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.gaaiuswallet.app.repository.SharedPreferenceRepository;
import com.gaaiuswallet.app.repository.SwapRepository;
import com.gaaiuswallet.app.repository.SwapRepositoryType;
import com.gaaiuswallet.app.repository.TokenLocalSource;
import com.gaaiuswallet.app.repository.TokenRepository;
import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.app.repository.TokensMappingRepository;
import com.gaaiuswallet.app.repository.TokensMappingRepositoryType;
import com.gaaiuswallet.app.repository.TokensRealmSource;
import com.gaaiuswallet.app.repository.TransactionLocalSource;
import com.gaaiuswallet.app.repository.TransactionRepository;
import com.gaaiuswallet.app.repository.TransactionRepositoryType;
import com.gaaiuswallet.app.repository.TransactionsRealmCache;
import com.gaaiuswallet.app.repository.WalletDataRealmSource;
import com.gaaiuswallet.app.repository.WalletRepository;
import com.gaaiuswallet.app.repository.WalletRepositoryType;
import com.gaaiuswallet.app.service.AccountKeystoreService;
import com.gaaiuswallet.app.service.GAAIUSWalletNotificationService;
import com.gaaiuswallet.app.service.GAAIUSWalletService;
import com.gaaiuswallet.app.service.AnalyticsService;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.service.IPFSService;
import com.gaaiuswallet.app.service.IPFSServiceType;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.service.KeystoreAccountService;
import com.gaaiuswallet.app.service.NotificationService;
import com.gaaiuswallet.app.service.OkLinkService;
import com.gaaiuswallet.app.service.OpenSeaService;
import com.gaaiuswallet.app.service.RealmManager;
import com.gaaiuswallet.app.service.SwapService;
import com.gaaiuswallet.app.service.TickerService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.service.TransactionsNetworkClient;
import com.gaaiuswallet.app.service.TransactionsNetworkClientType;
import com.gaaiuswallet.app.service.TransactionsService;
import com.gaaiuswallet.app.service.TransactionNotificationService;
import com.google.gson.Gson;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;

@Module
@InstallIn(SingletonComponent.class)
public class RepositoriesModule
{
    @Singleton
    @Provides
    PreferenceRepositoryType providePreferenceRepository(@ApplicationContext Context context)
    {
        return new SharedPreferenceRepository(context);
    }

    @Singleton
    @Provides
    AccountKeystoreService provideAccountKeyStoreService(@ApplicationContext Context context, KeyService keyService)
    {
        File file = new File(context.getFilesDir(), KEYSTORE_FOLDER);
        return new KeystoreAccountService(file, context.getFilesDir(), keyService);
    }

    @Singleton
    @Provides
    TickerService provideTickerService(OkHttpClient httpClient, PreferenceRepositoryType sharedPrefs, TokenLocalSource localSource)
    {
        return new TickerService(httpClient, sharedPrefs, localSource);
    }

    @Singleton
    @Provides
    EthereumNetworkRepositoryType provideEthereumNetworkRepository(
        PreferenceRepositoryType preferenceRepository,
        @ApplicationContext Context context
    )
    {
        return new EthereumNetworkRepository(preferenceRepository, context);
    }

    @Singleton
    @Provides
    WalletRepositoryType provideWalletRepository(
        PreferenceRepositoryType preferenceRepositoryType,
        AccountKeystoreService accountKeystoreService,
        EthereumNetworkRepositoryType networkRepository,
        WalletDataRealmSource walletDataRealmSource,
        KeyService keyService)
    {
        return new WalletRepository(
            preferenceRepositoryType, accountKeystoreService, networkRepository, walletDataRealmSource, keyService);
    }

    @Singleton
    @Provides
    TransactionRepositoryType provideTransactionRepository(
        EthereumNetworkRepositoryType networkRepository,
        AccountKeystoreService accountKeystoreService,
        TransactionLocalSource inDiskCache,
        TransactionsService transactionsService)
    {
        return new TransactionRepository(
            networkRepository,
            accountKeystoreService,
            inDiskCache,
            transactionsService);
    }

    @Singleton
    @Provides
    OnRampRepositoryType provideOnRampRepository(@ApplicationContext Context context)
    {
        return new OnRampRepository(context);
    }

    @Singleton
    @Provides
    SwapRepositoryType provideSwapRepository(@ApplicationContext Context context)
    {
        return new SwapRepository(context);
    }

    @Singleton
    @Provides
    CoinbasePayRepositoryType provideCoinbasePayRepository()
    {
        return new CoinbasePayRepository();
    }

    @Singleton
    @Provides
    TransactionLocalSource provideTransactionInDiskCache(RealmManager realmManager)
    {
        return new TransactionsRealmCache(realmManager);
    }

    @Singleton
    @Provides
    TransactionsNetworkClientType provideBlockExplorerClient(
        OkHttpClient httpClient,
        Gson gson,
        RealmManager realmManager)
    {
        return new TransactionsNetworkClient(httpClient, gson, realmManager);
    }

    @Singleton
    @Provides
    TokenRepositoryType provideTokenRepository(
        EthereumNetworkRepositoryType ethereumNetworkRepository,
        TokenLocalSource tokenLocalSource,
        @ApplicationContext Context context,
        TickerService tickerService)
    {
        return new TokenRepository(
            ethereumNetworkRepository,
            tokenLocalSource,
            context,
            tickerService);
    }

    @Singleton
    @Provides
    TokenLocalSource provideRealmTokenSource(RealmManager realmManager, EthereumNetworkRepositoryType ethereumNetworkRepository, TokensMappingRepositoryType tokensMappingRepository)
    {
        return new TokensRealmSource(realmManager, ethereumNetworkRepository, tokensMappingRepository);
    }

    @Singleton
    @Provides
    WalletDataRealmSource provideRealmWalletDataSource(RealmManager realmManager)
    {
        return new WalletDataRealmSource(realmManager);
    }

    @Singleton
    @Provides
    TokensService provideTokensServices(EthereumNetworkRepositoryType ethereumNetworkRepository,
                                        TokenRepositoryType tokenRepository,
                                        TickerService tickerService,
                                        OpenSeaService openseaService,
                                        AnalyticsServiceType analyticsService,
                                        OkHttpClient client)
    {
        return new TokensService(ethereumNetworkRepository, tokenRepository, tickerService, openseaService, analyticsService, client);
    }

    @Singleton
    @Provides
    IPFSServiceType provideIPFSService(OkHttpClient client)
    {
        return new IPFSService(client);
    }

    @Singleton
    @Provides
    TransactionsService provideTransactionsServices(TokensService tokensService,
                                                    EthereumNetworkRepositoryType ethereumNetworkRepositoryType,
                                                    TransactionsNetworkClientType transactionsNetworkClientType,
                                                    TransactionLocalSource transactionLocalSource,
                                                    TransactionNotificationService transactionNotificationService)
    {
        return new TransactionsService(tokensService, ethereumNetworkRepositoryType, transactionsNetworkClientType, transactionLocalSource, transactionNotificationService);
    }

    @Singleton
    @Provides
    GasService provideGasService(EthereumNetworkRepositoryType ethereumNetworkRepository, OkHttpClient client, RealmManager realmManager)
    {
        return new GasService(ethereumNetworkRepository, client, realmManager);
    }

    @Singleton
    @Provides
    OpenSeaService provideOpenseaService()
    {
        return new OpenSeaService();
    }

    @Singleton
    @Provides
    SwapService provideSwapService()
    {
        return new SwapService();
    }

    @Singleton
    @Provides
    GAAIUSWalletService provideFeemasterService(OkHttpClient okHttpClient, Gson gson)
    {
        return new GAAIUSWalletService(okHttpClient, gson);
    }

    @Singleton
    @Provides
    NotificationService provideNotificationService(@ApplicationContext Context ctx)
    {
        return new NotificationService(ctx);
    }

    @Singleton
    @Provides
    AssetDefinitionService providingAssetDefinitionServices(IPFSServiceType ipfsService, @ApplicationContext Context ctx, NotificationService notificationService, RealmManager realmManager,
                                                            TokensService tokensService, TokenLocalSource tls,
                                                            GAAIUSWalletService alphaService)
    {
        return new AssetDefinitionService(ipfsService, ctx, notificationService, realmManager, tokensService, tls, alphaService);
    }

    @Singleton
    @Provides
    KeyService provideKeyService(@ApplicationContext Context ctx, AnalyticsServiceType analyticsService)
    {
        return new KeyService(ctx, analyticsService);
    }

    @Singleton
    @Provides
    AnalyticsServiceType provideAnalyticsService(@ApplicationContext Context ctx, PreferenceRepositoryType preferenceRepository)
    {
        return new AnalyticsService(ctx, preferenceRepository);
    }

    @Singleton
    @Provides
    TokensMappingRepositoryType provideTokensMappingRepository(@ApplicationContext Context ctx)
    {
        return new TokensMappingRepository(ctx);
    }

    @Singleton
    @Provides
    TransactionNotificationService provideTransactionNotificationService(@ApplicationContext Context ctx,
                                                                         PreferenceRepositoryType preferenceRepositoryType)
    {
        return new TransactionNotificationService(ctx, preferenceRepositoryType);
    }

    @Singleton
    @Provides
    GAAIUSWalletNotificationService provideGAAIUSWalletNotificationService(WalletRepositoryType walletRepository)
    {
        return new GAAIUSWalletNotificationService(walletRepository);
    }
}
