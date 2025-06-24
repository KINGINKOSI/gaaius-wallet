package com.gaaiuswallet.shadows;


import com.gaaiuswallet.app.di.mock.KeyProviderMockNonProductionImpl;
import com.gaaiuswallet.app.repository.KeyProvider;
import com.gaaiuswallet.app.repository.KeyProviderFactory;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(KeyProviderFactory.class)
public class ShadowKeyProviderFactoryNonProduction
{
    @Implementation
    public static KeyProvider get() {
        return new KeyProviderMockNonProductionImpl();
    }
}
