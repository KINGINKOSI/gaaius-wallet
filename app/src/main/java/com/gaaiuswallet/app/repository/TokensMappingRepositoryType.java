package com.gaaiuswallet.app.repository;

import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.tokendata.TokenGroup;
import com.gaaiuswallet.token.entity.ContractAddress;

public interface TokensMappingRepositoryType
{
    TokenGroup getTokenGroup(long chainId, String address, ContractType type);

    ContractAddress getBaseToken(long chainId, String address);
}
