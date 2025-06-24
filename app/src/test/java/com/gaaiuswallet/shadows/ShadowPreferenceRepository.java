package com.gaaiuswallet.shadows;

import android.content.Context;

import com.gaaiuswallet.app.repository.SharedPreferenceRepository;
import com.gaaiuswallet.app.service.AnalyticsService;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(SharedPreferenceRepository.class)
public class ShadowPreferenceRepository
{
    @Implementation
    public void __constructor__(Context context) {
    }
}
