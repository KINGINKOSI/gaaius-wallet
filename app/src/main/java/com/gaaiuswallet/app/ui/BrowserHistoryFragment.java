package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.ui.DappBrowserFragment.DAPP_CLICK;
import static com.gaaiuswallet.app.ui.DappBrowserFragment.DAPP_REMOVE_HISTORY;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.DApp;
import com.gaaiuswallet.app.ui.widget.OnDappClickListener;
import com.gaaiuswallet.app.ui.widget.adapter.BrowserHistoryAdapter;
import com.gaaiuswallet.app.util.DappBrowserUtils;
import com.gaaiuswallet.app.viewmodel.BrowserHistoryViewModel;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BrowserHistoryFragment extends BaseFragment
{
    private BrowserHistoryViewModel viewModel;
    private BrowserHistoryAdapter adapter;
    private AWalletAlertDialog dialog;
    private TextView clear;
    private TextView noHistory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.layout_browser_history, container, false);
        adapter = new BrowserHistoryAdapter(
                getData(),
                (OnDappClickListener) dapp -> setFragmentResult(DAPP_CLICK, dapp),
                this::onHistoryItemRemoved);
        RecyclerView list = view.findViewById(R.id.my_dapps_list);
        list.setNestedScrollingEnabled(false);
        list.setLayoutManager(new LinearLayoutManager(getActivity()));
        list.setAdapter(adapter);

        noHistory = view.findViewById(R.id.no_history);
        clear = view.findViewById(R.id.clear);
        clear.setOnClickListener(v -> {
            dialog = new AWalletAlertDialog(getActivity());
            dialog.setTitle(R.string.dialog_title_clear);
            dialog.setMessage(R.string.dialog_message_clear);
            dialog.setIcon(AWalletAlertDialog.NONE);
            dialog.setButtonText(R.string.action_clear);
            dialog.setButtonListener(v1 -> {
                clearHistory();
                dialog.dismiss();
            });
            dialog.setSecondaryButtonText(R.string.dialog_cancel_back);
            dialog.show();
        });
        viewModel = new ViewModelProvider(this).get(BrowserHistoryViewModel.class);
        showOrHideViews();
        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        viewModel.track(Analytics.Navigation.BROWSER_HISTORY);
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        adapter.clear();
    }

    private void showOrHideViews()
    {
        if (adapter.getItemCount() > 0)
        {
            clear.setVisibility(View.VISIBLE);
            noHistory.setVisibility(View.GONE);
        }
        else
        {
            clear.setVisibility(View.GONE);
            noHistory.setVisibility(View.VISIBLE);
        }
    }

    private void clearHistory()
    {
        DappBrowserUtils.clearHistory(getContext());
        adapter.setDapps(getData());
        showOrHideViews();
    }

    private void onHistoryItemRemoved(DApp dapp)
    {
        DappBrowserUtils.removeFromHistory(getContext(), dapp.getUrl());
        adapter.setDapps(getData());
        showOrHideViews();
        setFragmentResult(DAPP_REMOVE_HISTORY, dapp);
    }

    private void setFragmentResult(String key, DApp dapp)
    {
        Bundle result = new Bundle();
        result.putParcelable(key, dapp);
        getParentFragmentManager().setFragmentResult(DAPP_CLICK, result);
    }

    private List<DApp> getData()
    {
        return DappBrowserUtils.getBrowserHistory(getContext());
    }
}
