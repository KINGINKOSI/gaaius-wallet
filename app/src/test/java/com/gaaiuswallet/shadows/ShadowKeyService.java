package com.gaaiuswallet.shadows;

import android.content.Context;

import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.service.AnalyticsServiceType;
import com.gaaiuswallet.app.service.KeyService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(KeyService.class)
public class ShadowKeyService
{
    @Implementation
    public void __constructor__(Context ctx, AnalyticsServiceType<AnalyticsProperties> analyticsService) {

    }
}
