package com.gaaiuswallet.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.gaaiuswallet.app.entity.tokens.TokenPerformance;
import com.gaaiuswallet.app.entity.tokens.TokenPortfolio;
import com.gaaiuswallet.app.entity.tokens.TokenStats;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.TokenInfoFragment;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TokenInfoViewModel extends BaseViewModel {
    private final MutableLiveData<String> marketPrice = new MutableLiveData<>();
    private final MutableLiveData<TokenPortfolio> portfolio = new MutableLiveData<>();
    private final MutableLiveData<TokenPerformance> performance = new MutableLiveData<>();
    private final MutableLiveData<TokenStats> stats = new MutableLiveData<>();

    private final AssetDefinitionService assetDefinitionService;
    private final TokensService tokensService;

    @Inject
    public TokenInfoViewModel(AssetDefinitionService assetDefinitionService,
                              TokensService tokensService)
    {
        this.assetDefinitionService = assetDefinitionService;
        this.tokensService = tokensService;
    }

    public LiveData<String> marketPrice()
    {
        return marketPrice;
    }

    public LiveData<TokenPortfolio> portfolio()
    {
        return portfolio;
    }

    public LiveData<TokenPerformance> performance()
    {
        return performance;
    }

    public LiveData<TokenStats> stats()
    {
        return stats;
    }

    public TokensService getTokensService() { return tokensService; }

    public AssetDefinitionService getAssetDefinitionService() { return assetDefinitionService; }

    public void fetchPortfolio()
    {
        TokenPortfolio tokenPortfolio = new TokenPortfolio();

        // TODO: Do calculations here

        portfolio.postValue(tokenPortfolio);
    }

    public void fetchPerformance()
    {
        TokenPerformance tokenPerformance = new TokenPerformance();

        // TODO: Do calculations here

        performance.postValue(tokenPerformance);
    }

    public void fetchStats()
    {
        TokenStats tokenStats = new TokenStats();

        // TODO: Do calculations here

        stats.postValue(tokenStats);
    }
}
