package com.gaaiuswallet.shadows;

import android.content.Context;

import com.gaaiuswallet.app.service.AnalyticsService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(AnalyticsService.class)
public class ShadowAnalyticsService
{
    @Implementation
    public void __constructor__(Context context, ShadowPreferenceRepository preferenceRepository) {
    }

    @Implementation
    public void identify(String uuid) {
    }
}
