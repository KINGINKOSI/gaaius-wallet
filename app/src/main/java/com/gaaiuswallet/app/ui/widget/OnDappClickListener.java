package com.gaaiuswallet.app.ui.widget;

import java.io.Serializable;

import com.gaaiuswallet.app.entity.DApp;

public interface OnDappClickListener extends Serializable {
    void onDappClick(DApp dapp);
}
