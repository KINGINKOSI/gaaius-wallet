package com.gaaiuswallet.app.viewmodel;

import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.repository.EthereumNetworkRepositoryType;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.TokensService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MyAddressViewModel extends BaseViewModel {
    private final EthereumNetworkRepositoryType ethereumNetworkRepository;
    private final TokensService tokenService;
    private final AssetDefinitionService assetDefinitionService;

    @Inject
    MyAddressViewModel(
            EthereumNetworkRepositoryType ethereumNetworkRepository,
            TokensService tokensService,
            AssetDefinitionService assetDefinitionService) {
        this.ethereumNetworkRepository = ethereumNetworkRepository;
        this.tokenService = tokensService;
        this.assetDefinitionService = assetDefinitionService;
    }

    public TokensService getTokenService() {
        return tokenService;
    }

    public NetworkInfo getNetworkByChain(long chainId) {
        return ethereumNetworkRepository.getNetworkByChain(chainId);
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }
}
