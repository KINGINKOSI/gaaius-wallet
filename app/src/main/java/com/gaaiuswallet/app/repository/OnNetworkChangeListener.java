package com.gaaiuswallet.app.repository;

import com.gaaiuswallet.app.entity.NetworkInfo;

public interface OnNetworkChangeListener {
	void onNetworkChanged(NetworkInfo networkInfo);
}
