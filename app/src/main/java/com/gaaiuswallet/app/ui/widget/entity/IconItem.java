package com.gaaiuswallet.app.ui.widget.entity;

import static com.gaaiuswallet.app.repository.TokensRealmSource.databaseKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconItem {
    private final String url;

    public IconItem(String url, long chainId, String correctedAddress) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
