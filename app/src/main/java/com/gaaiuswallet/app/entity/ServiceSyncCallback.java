package com.gaaiuswallet.app.entity;

import com.gaaiuswallet.app.service.TokensService;

/**
 * Created by JB on 2/12/2021.
 */
public interface ServiceSyncCallback
{
    void syncComplete(TokensService svs, int syncCount);
}
