package com.gaaiuswallet.app.di.mock;

import com.gaaiuswallet.app.repository.KeyProvider;

public class KeyProviderMockImpl implements KeyProvider
{
    private static final String FAKE_KEY_FOR_TESTING = "fake-key-for-testing";

    @Override
    public String getBSCExplorerKey()
    {
        return "mock-bsc-explorer-key";
    }

    @Override
    public String getAnalyticsKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getEtherscanKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getPolygonScanKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getAuroraScanKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getCovalentKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getKlaytnKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getInfuraKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getSecondaryInfuraKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getTertiaryInfuraKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getRampKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getOpenSeaKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getMailchimpKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getCoinbasePayAppId()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getWalletConnectProjectId()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getInfuraSecret()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getTSInfuraKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getUnstoppableDomainsKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getOkLinkKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getOkLBKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getBlockPiBaobabKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getBlockPiCypressKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getBlockNativeKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getSmartPassKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getSmartPassDevKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getCoinGeckoKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }

    @Override
    public String getBackupKey()
    {
        return FAKE_KEY_FOR_TESTING;
    }
}
