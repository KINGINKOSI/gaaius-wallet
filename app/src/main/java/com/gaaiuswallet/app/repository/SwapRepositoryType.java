package com.gaaiuswallet.app.repository;

import com.gaaiuswallet.app.entity.lifi.SwapProvider;

import java.util.List;

public interface SwapRepositoryType
{
    List<SwapProvider> getProviders();
}
