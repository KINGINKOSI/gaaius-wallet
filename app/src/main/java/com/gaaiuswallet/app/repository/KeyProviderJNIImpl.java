package com.gaaiuswallet.app.repository;

public class KeyProviderJNIImpl implements KeyProvider
{
    public KeyProviderJNIImpl()
    {
        System.loadLibrary("keys");
    }

    public native String getInfuraKey();

    public native String getTSInfuraKey();

    public native String getSecondaryInfuraKey();

    public native String getTertiaryInfuraKey();

    public native String getBSCExplorerKey();

    public native String getAnalyticsKey();

    public native String getEtherscanKey();

    public native String getPolygonScanKey();

    public native String getAuroraScanKey();

    public native String getCovalentKey();

    public native String getKlaytnKey();

    public native String getRampKey();

    public native String getOpenSeaKey();

    public native String getMailchimpKey();

    public native String getCoinbasePayAppId();

    public native String getWalletConnectProjectId();

    public native String getInfuraSecret();

    public native String getUnstoppableDomainsKey();

    public native String getOkLinkKey();

    public native String getOkLBKey();

    public native String getBlockPiBaobabKey();

    public native String getBlockPiCypressKey();

    public native String getBlockNativeKey();

    public native String getSmartPassKey();

    public native String getSmartPassDevKey();

    public native String getCoinGeckoKey();

    public native String getBackupKey();
}
