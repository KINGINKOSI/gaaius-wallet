package com.gaaiuswallet.app.ui;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.ui.widget.adapter.ActivityAdapter;
import com.gaaiuswallet.app.viewmodel.TokenActivityViewModel;
import com.gaaiuswallet.app.widget.ActivityHistoryList;
import com.gaaiuswallet.ethereum.EthereumNetworkBase;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TokenActivityFragment extends BaseFragment {

    private TokenActivityViewModel viewModel;

    private ActivityHistoryList history;

    private Wallet wallet;

    private Token token;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_token_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null)
        {
            viewModel = new ViewModelProvider(this)
                    .get(TokenActivityViewModel.class);

            long chainId = getArguments().getLong(C.EXTRA_CHAIN_ID, EthereumNetworkBase.MAINNET_ID);
            token = viewModel.getTokensService().getToken(chainId, getArguments().getString(C.EXTRA_ADDRESS));
            wallet = getArguments().getParcelable(C.Key.WALLET);

            history = view.findViewById(R.id.history_list);

            setUpRecentTransactionsView();
        }
    }

    private void setUpRecentTransactionsView()
    {
        ActivityAdapter adapter = new ActivityAdapter(viewModel.getTokensService(), viewModel.getTransactionsInteract(),
                viewModel.getAssetDefinitionService());
        adapter.setDefaultWallet(wallet);
        history.setupAdapter(adapter);
        history.startActivityListeners(viewModel.getRealmInstance(wallet), wallet,
                token, viewModel.getTokensService(),15);
    }
}
