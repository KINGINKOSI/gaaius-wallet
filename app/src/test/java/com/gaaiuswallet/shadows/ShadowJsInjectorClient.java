package com.gaaiuswallet.shadows;

import android.content.Context;

import com.gaaiuswallet.app.web3.JsInjectorClient;
import com.getkeepsafe.relinker.ReLinker;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(JsInjectorClient.class)
public class ShadowJsInjectorClient
{
    @Implementation
    protected void __constructor__(Context context) {
    }
}
