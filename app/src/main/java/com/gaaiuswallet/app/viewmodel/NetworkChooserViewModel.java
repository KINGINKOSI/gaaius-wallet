package com.gaaiuswallet.app.viewmodel;

import android.app.Activity;
import android.content.Intent;

import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.NetworkToggleActivity;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class NetworkChooserViewModel extends BaseViewModel
{
    private final EthereumNetworkRepositoryType networkRepository;
    private final TokensService tokensService;

    @Inject
    public NetworkChooserViewModel(EthereumNetworkRepositoryType ethereumNetworkRepositoryType,
                                   TokensService tokensService)
    {
        this.networkRepository = ethereumNetworkRepositoryType;
        this.tokensService = tokensService;
    }

    public NetworkInfo[] getNetworkList()
    {
        return networkRepository.getAvailableNetworkList();
    }

    public List<Long> getFilterNetworkList()
    {
        return networkRepository.getFilterNetworkList();
    }

    public void openSelectNetworkFilters(Activity ctx, int requestCode)
    {
        Intent intent = new Intent(ctx, NetworkToggleActivity.class);
        ctx.startActivityForResult(intent, requestCode);
    }

    public NetworkInfo getNetworkByChain(long chainId)
    {
        return networkRepository.getNetworkByChain(chainId);
    }

    public long getSelectedNetwork()
    {
        NetworkInfo browserNetwork = networkRepository.getActiveBrowserNetwork();
        if (browserNetwork != null)
        {
            return browserNetwork.chainId;
        }
        else return -1;
    }

    public TokensService getTokensService()
    {
        return tokensService;
    }
}
