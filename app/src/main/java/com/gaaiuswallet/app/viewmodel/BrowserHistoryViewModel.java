package com.gaaiuswallet.app.viewmodel;

import com.gaaiuswallet.app.service.AnalyticsServiceType;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class BrowserHistoryViewModel extends BaseViewModel
{
    @Inject
    BrowserHistoryViewModel(AnalyticsServiceType analyticsService)
    {
        setAnalyticsService(analyticsService);
    }
}
