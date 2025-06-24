package com.gaaiuswallet.app.viewmodel;

import com.gaaiuswallet.app.service.AnalyticsServiceType;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class QrScannerViewModel extends BaseViewModel
{

    @Inject
    public QrScannerViewModel(AnalyticsServiceType analyticsService)
    {
        setAnalyticsService(analyticsService);
    }
}
