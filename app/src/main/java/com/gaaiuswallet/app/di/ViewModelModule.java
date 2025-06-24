package com.gaaiuswallet.app.di;

import com.gaaiuswallet.app.interact.ChangeTokenEnableInteract;
import com.gaaiuswallet.app.interact.CreateTransactionInteract;
import com.gaaiuswallet.app.interact.DeleteWalletInteract;
import com.gaaiuswallet.app.interact.ExportWalletInteract;
import com.gaaiuswallet.app.interact.FetchTokensInteract;
import com.gaaiuswallet.app.interact.FetchTransactionsInteract;
import com.gaaiuswallet.app.interact.FetchWalletsInteract;
import com.gaaiuswallet.app.interact.FindDefaultNetworkInteract;
import com.gaaiuswallet.app.interact.GenericWalletInteract;
import com.gaaiuswallet.app.interact.ImportWalletInteract;
import com.gaaiuswallet.app.interact.MemPoolInteract;
import com.gaaiuswallet.app.interact.SetDefaultWalletInteract;
import com.gaaiuswallet.app.interact.SignatureGenerateInteract;
import com.gaaiuswallet.app.repository.CurrencyRepository;
import com.gaaiuswallet.app.repository.CurrencyRepositoryType;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.LocaleRepository;
import com.gaaiuswallet.app.repository.LocaleRepositoryType;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.app.repository.TransactionRepositoryType;
import com.gaaiuswallet.app.repository.WalletRepositoryType;
import com.gaaiuswallet.app.router.CoinbasePayRouter;
import com.gaaiuswallet.app.router.ExternalBrowserRouter;
import com.gaaiuswallet.app.router.HomeRouter;
import com.gaaiuswallet.app.router.ImportTokenRouter;
import com.gaaiuswallet.app.router.ImportWalletRouter;
import com.gaaiuswallet.app.router.ManageWalletsRouter;
import com.gaaiuswallet.app.router.MyAddressRouter;
import com.gaaiuswallet.app.router.RedeemSignatureDisplayRouter;
import com.gaaiuswallet.app.router.SellDetailRouter;
import com.gaaiuswallet.app.router.TokenDetailRouter;
import com.gaaiuswallet.app.router.TransferTicketDetailRouter;
import com.gaaiuswallet.app.service.AnalyticsServiceType;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ViewModelComponent;

@Module
@InstallIn(ViewModelComponent.class)
/** Module for providing dependencies to viewModels.
 * All bindings of modules from BuildersModule is shifted here as they were injected in activity for ViewModelFactory but not needed in Hilt
 * */
public class ViewModelModule {

    @Provides
    FetchWalletsInteract provideFetchWalletInteract(WalletRepositoryType walletRepository) {
        return new FetchWalletsInteract(walletRepository);
    }

    @Provides
    SetDefaultWalletInteract provideSetDefaultAccountInteract(WalletRepositoryType accountRepository) {
        return new SetDefaultWalletInteract(accountRepository);
    }

    @Provides
    ImportWalletRouter provideImportAccountRouter() {
        return new ImportWalletRouter();
    }

    @Provides
    HomeRouter provideHomeRouter() {
        return new HomeRouter();
    }

    @Provides
    FindDefaultNetworkInteract provideFindDefaultNetworkInteract(
            EthereumNetworkRepositoryType networkRepository) {
        return new FindDefaultNetworkInteract(networkRepository);
    }

    @Provides
    ImportWalletInteract provideImportWalletInteract(
            WalletRepositoryType walletRepository) {
        return new ImportWalletInteract(walletRepository);
    }

    @Provides
    ExternalBrowserRouter externalBrowserRouter() {
        return new ExternalBrowserRouter();
    }

    @Provides
    FetchTransactionsInteract provideFetchTransactionsInteract(TransactionRepositoryType transactionRepository,
                                                               TokenRepositoryType tokenRepositoryType) {
        return new FetchTransactionsInteract(transactionRepository, tokenRepositoryType);
    }

    @Provides
    CreateTransactionInteract provideCreateTransactionInteract(TransactionRepositoryType transactionRepository,
                                                               AnalyticsServiceType analyticsService) {
        return new CreateTransactionInteract(transactionRepository, analyticsService);
    }

    @Provides
    MyAddressRouter provideMyAddressRouter() {
        return new MyAddressRouter();
    }

    @Provides
    CoinbasePayRouter provideCoinbasePayRouter() {
        return new CoinbasePayRouter();
    }

    @Provides
    FetchTokensInteract provideFetchTokensInteract(TokenRepositoryType tokenRepository) {
        return new FetchTokensInteract(tokenRepository);
    }

    @Provides
    SignatureGenerateInteract provideSignatureGenerateInteract(WalletRepositoryType walletRepository) {
        return new SignatureGenerateInteract(walletRepository);
    }

    @Provides
    MemPoolInteract provideMemPoolInteract(TokenRepositoryType tokenRepository) {
        return new MemPoolInteract(tokenRepository);
    }

    @Provides
    TransferTicketDetailRouter provideTransferTicketRouter() {
        return new TransferTicketDetailRouter();
    }

    @Provides
    LocaleRepositoryType provideLocaleRepository(PreferenceRepositoryType preferenceRepository) {
        return new LocaleRepository(preferenceRepository);
    }

    @Provides
    CurrencyRepositoryType provideCurrencyRepository(PreferenceRepositoryType preferenceRepository) {
        return new CurrencyRepository(preferenceRepository);
    }

    @Provides
    TokenDetailRouter provideErc20DetailRouterRouter() {
        return new TokenDetailRouter();
    }

    @Provides
    GenericWalletInteract provideGenericWalletInteract(WalletRepositoryType walletRepository) {
        return new GenericWalletInteract(walletRepository);
    }

    @Provides
    ChangeTokenEnableInteract provideChangeTokenEnableInteract(TokenRepositoryType tokenRepository) {
        return new ChangeTokenEnableInteract(tokenRepository);
    }

    @Provides
    ManageWalletsRouter provideManageWalletsRouter() {
        return new ManageWalletsRouter();
    }

    @Provides
    SellDetailRouter provideSellDetailRouter() {
        return new SellDetailRouter();
    }

    @Provides
    DeleteWalletInteract provideDeleteAccountInteract(
            WalletRepositoryType accountRepository) {
        return new DeleteWalletInteract(accountRepository);
    }

    @Provides
    ExportWalletInteract provideExportWalletInteract(
            WalletRepositoryType walletRepository) {
        return new ExportWalletInteract(walletRepository);
    }

    @Provides
    ImportTokenRouter provideImportTokenRouter() {
        return new ImportTokenRouter();
    }

    @Provides
    RedeemSignatureDisplayRouter provideRedeemSignatureDisplayRouter() {
        return new RedeemSignatureDisplayRouter();
    }
}
