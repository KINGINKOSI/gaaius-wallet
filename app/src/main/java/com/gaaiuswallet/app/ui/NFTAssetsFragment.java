package com.gaaiuswallet.app.ui;


import static android.app.Activity.RESULT_OK;
import static com.gaaiuswallet.app.C.SIGNAL_NFT_SYNC;
import static com.gaaiuswallet.app.C.SYNC_STATUS;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.ui.widget.OnAssetClickListener;
import com.gaaiuswallet.app.ui.widget.TokensAdapterCallback;
import com.gaaiuswallet.app.ui.widget.adapter.NFTAssetsAdapter;
import com.gaaiuswallet.app.ui.widget.adapter.NonFungibleTokenAdapter;
import com.gaaiuswallet.app.ui.widget.divider.ItemOffsetDecoration;
import com.gaaiuswallet.app.util.ShortcutUtils;
import com.gaaiuswallet.app.viewmodel.NFTAssetsViewModel;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;
import com.gaaiuswallet.ethereum.EthereumNetworkBase;

import java.math.BigInteger;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public class NFTAssetsFragment extends BaseFragment implements OnAssetClickListener, TokensAdapterCallback {
    private final Handler delayHandler = new Handler(Looper.getMainLooper());
    NFTAssetsViewModel viewModel;
    private Token token;
    private Wallet wallet;
    private RecyclerView recyclerView;
    private ItemOffsetDecoration gridItemDecoration;
    private EditText search;
    private LinearLayout searchLayout;
    private RecyclerView.Adapter<?> adapter;

    private final ActivityResultLauncher<Intent> handleTransactionSuccess = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getData() == null) return;
                String transactionHash = result.getData().getStringExtra(C.EXTRA_TXHASH);
                //process hash
                if (!TextUtils.isEmpty(transactionHash))
                {
                    Intent intent = new Intent();
                    intent.putExtra(C.EXTRA_TXHASH, transactionHash);
                    requireActivity().setResult(RESULT_OK, intent);
                    requireActivity().finish();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_nft_assets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null)
        {
            viewModel = new ViewModelProvider(this)
                    .get(NFTAssetsViewModel.class);

            long chainId = getArguments().getLong(C.EXTRA_CHAIN_ID, EthereumNetworkBase.MAINNET_ID);
            token = viewModel.getTokensService().getToken(chainId, getArguments().getString(C.EXTRA_ADDRESS));
            wallet = getArguments().getParcelable(C.Key.WALLET);

            recyclerView = view.findViewById(R.id.recycler_view);

            search = view.findViewById(R.id.edit_search);

            searchLayout = view.findViewById(R.id.layout_search_tokens);

            gridItemDecoration = new ItemOffsetDecoration(requireContext(), R.dimen.grid_divider_offset);

            if (hasTokenScriptOverride(token) && token.isERC875())
            {
                showListView();
            }
            else
            {
                showGridView();
            }
        }
    }

    @Override
    public void onAssetClicked(Pair<BigInteger, NFTAsset> item)
    {
        if (item.second.isCollection())
        {
            handleTransactionSuccess.launch(viewModel.showAssetListDetails(requireContext(), wallet, token, item.second));
        }
        else
        {
            handleTransactionSuccess.launch(viewModel.showAssetDetails(requireContext(), wallet, token, item.first, item.second));
        }
    }

    @Override
    public void onAssetLongClicked(Pair<BigInteger, NFTAsset> item)
    {
        showCreateShortcutsDialog(item);
    }

    private void showCreateShortcutsDialog(Pair<BigInteger, NFTAsset> asset)
    {
        AWalletAlertDialog cDialog = new AWalletAlertDialog(requireContext());
        cDialog.setCancelable(true);
        cDialog.setTitle(R.string.title_activity_confirmation);
        cDialog.setMessage(getString(R.string.create_shortcut_for_token));
        cDialog.setButtonText(R.string.ok);
        cDialog.setButtonListener(v -> {
            createShortcuts(asset);
            cDialog.dismiss();
        });
        cDialog.setSecondaryButtonText(R.string.action_cancel);
        cDialog.setSecondaryButtonListener(view -> cDialog.dismiss());
        cDialog.show();
    }

    private void createShortcuts(Pair<BigInteger, NFTAsset> pair)
    {
        Intent intent = viewModel.showAssetDetails(requireContext(), wallet, token, pair.first, null);
        intent.setAction(C.ACTION_TOKEN_SHORTCUT);
        intent.putExtra(C.Key.WALLET, wallet.address);
        ShortcutUtils.createShortcut(pair, intent, requireContext(), token);
        Toast.makeText(requireContext(), R.string.toast_shortcut_created, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTokenClick(View view, Token token, List<BigInteger> tokenIds, boolean selected)
    {
        NFTAsset asset = token.getAssetForToken(tokenIds.get(0));
        handleTransactionSuccess.launch(viewModel.showAssetDetails(requireContext(), wallet, token, tokenIds.get(0), asset));
    }

    @Override
    public void onLongTokenClick(View view, Token token, List<BigInteger> tokenIds)
    {

    }

    public void updateToken(Token newToken)
    {
        token = newToken;
    }

    public void showGridView()
    {
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.addItemDecoration(gridItemDecoration);
        recyclerView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface));
        initAndAttachAdapter(true);
    }

    public void showListView()
    {
        try
        {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerView.removeItemDecoration(gridItemDecoration);
            recyclerView.setPadding(0, 0, 0, 0);
            initAndAttachAdapter(false);
        }
        catch (Exception e)
        {
            Timber.e(e);
        }
    }

    private void initAndAttachAdapter(boolean isGridView)
    {
        if (hasTokenScriptOverride(token) && token.isERC875())
        {
            searchLayout.setVisibility(View.GONE);
            adapter = new NonFungibleTokenAdapter(this, token, viewModel.getAssetDefinitionService(), viewModel.getOpenseaService(), isGridView);
        }
        else
        {
            searchLayout.setVisibility(View.VISIBLE);
            adapter = new NFTAssetsAdapter(getActivity(), token, this, viewModel.getOpenseaService(), isGridView);
            search.addTextChangedListener(setupTextWatcher((NFTAssetsAdapter)adapter));

            attachAttestations();
        }

        recyclerView.setAdapter(adapter);
        checkSyncStatus();
    }

    private void attachAttestations()
    {
        //has attestations?
        List<Token> attns = viewModel.getTokensService().getAttestations(token.tokenInfo.chainId, token.getAddress());
        if (attns.size() > 0)
        {
            ((NFTAssetsAdapter) adapter).attachAttestations(attns.toArray(new Token[0]));
        }
    }

    private void checkSyncStatus()
    {
        if (token == null || token.getTokenAssets() == null) return;
        Bundle result = new Bundle();
        result.putBoolean(SYNC_STATUS, token.getTokenCount() != token.getTokenAssets().size());
        getParentFragmentManager().setFragmentResult(SIGNAL_NFT_SYNC, result);
        forceRedraw();
    }

    // This is a trick to fix a bug
    private void forceRedraw()
    {
        search.setText("");
    }

    private boolean hasTokenScriptOverride(Token t)
    {
        return viewModel.getAssetDefinitionService().hasTokenView(t, AssetDefinitionService.ASSET_SUMMARY_VIEW_NAME);
    }

    private TextWatcher setupTextWatcher(NFTAssetsAdapter adapter)
    {
        return new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            @Override
            public void afterTextChanged(final Editable searchFilter)
            {
                delayHandler.postDelayed(() -> {
                    if (adapter != null)
                    {
                        adapter.filter(searchFilter.toString());
                    }
                }, 200);
            }
        };
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (adapter instanceof NFTAssetsAdapter)
        {
            ((NFTAssetsAdapter)adapter).onDestroy();
        }
    }
}
