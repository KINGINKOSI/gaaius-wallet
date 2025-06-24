package com.gaaiuswallet.app.interact;

import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.token.entity.ContractAddress;

import io.reactivex.Completable;

public class ChangeTokenEnableInteract
{
    private final TokenRepositoryType tokenRepository;

    public ChangeTokenEnableInteract(TokenRepositoryType tokenRepository)
    {
        this.tokenRepository = tokenRepository;
    }

    public Completable setEnable(Wallet wallet, ContractAddress cAddr, boolean enabled)
    {
        tokenRepository.setEnable(wallet, cAddr, enabled);
        tokenRepository.setVisibilityChanged(wallet, cAddr);
        return Completable.fromAction(() -> {});
    }
}
