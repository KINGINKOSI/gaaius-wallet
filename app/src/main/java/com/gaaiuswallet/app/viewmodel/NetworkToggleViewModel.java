package com.gaaiuswallet.app.viewmodel;

import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.repository.PreferenceRepositoryType;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.widget.entity.NetworkItem;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class NetworkToggleViewModel extends BaseViewModel
{
    private final EthereumNetworkRepositoryType networkRepository;
    private final TokensService tokensService;
    private final PreferenceRepositoryType preferenceRepository;

    @Inject
    public NetworkToggleViewModel(EthereumNetworkRepositoryType ethereumNetworkRepositoryType,
                                  TokensService tokensService,
                                  PreferenceRepositoryType preferenceRepository,
                                  AnalyticsServiceType analyticsService)
    {
        this.networkRepository = ethereumNetworkRepositoryType;
        this.tokensService = tokensService;
        this.preferenceRepository = preferenceRepository;
        setAnalyticsService(analyticsService);
    }

    public NetworkInfo[] getNetworkList()
    {
        return networkRepository.getAvailableNetworkList();
    }

    public void setFilterNetworks(List<Long> selectedItems, boolean hasSelected, boolean shouldBlankUserSelection)
    {
        NetworkInfo activeNetwork = networkRepository.getActiveBrowserNetwork();
        long activeNetworkId = -99;
        if (activeNetwork != null)
        {
            activeNetworkId = networkRepository.getActiveBrowserNetwork().chainId;
        }

        //Mark dappbrowser network as deselected if appropriate
        boolean deselected = true;
        Long[] selectedIds = new Long[selectedItems.size()];
        int index = 0;
        for (Long selectedId : selectedItems)
        {
            if (EthereumNetworkRepository.hasRealValue(selectedId) && activeNetworkId == selectedId)
            {
                deselected = false;
            }
            selectedIds[index++] = selectedId;
        }

        if (deselected) networkRepository.setActiveBrowserNetwork(null);
        networkRepository.setFilterNetworkList(selectedIds);
        tokensService.setupFilter(hasSelected && !shouldBlankUserSelection);

        if (shouldBlankUserSelection) preferenceRepository.blankHasSetNetworkFilters();

        preferenceRepository.commit();
    }

    public NetworkInfo getNetworkByChain(long chainId)
    {
        return networkRepository.getNetworkByChain(chainId);
    }

    public List<NetworkItem> getNetworkList(boolean isMainNet)
    {
        List<NetworkItem> networkList = new ArrayList<>();
        List<Long> filterIds = networkRepository.getSelectedFilters();

        for (NetworkInfo info : getNetworkList())
        {
            if (info != null && EthereumNetworkRepository.hasRealValue(info.chainId) == isMainNet)
            {
                networkList.add(new NetworkItem(info.name, info.chainId, filterIds.contains(info.chainId)));
            }
        }

        return networkList;
    }

    public void removeCustomNetwork(long chainId)
    {
        networkRepository.removeCustomRPCNetwork(chainId);
    }

    public TokensService getTokensService()
    {
        return tokensService;
    }

    public List<Long> getActiveNetworks()
    {
        return networkRepository.getFilterNetworkList();
    }

    public void setTestnetEnabled(boolean enabled)
    {
        preferenceRepository.setTestnetEnabled(enabled);
    }
}
