package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.C.Key.WALLET;
import static com.gaaiuswallet.app.C.SIGNAL_NFT_SYNC;
import static com.gaaiuswallet.app.C.SYNC_STATUS;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.StandardFunctionInterface;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.ui.widget.adapter.TabPagerAdapter;
import com.gaaiuswallet.app.util.TabUtils;
import com.gaaiuswallet.app.viewmodel.NFTViewModel;
import com.gaaiuswallet.app.widget.CertifiedToolbarView;
import com.gaaiuswallet.app.widget.FunctionButtonBar;
import com.gaaiuswallet.ethereum.EthereumNetworkBase;
import com.gaaiuswallet.token.entity.XMLDsigDescriptor;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NFTActivity extends BaseActivity implements StandardFunctionInterface
{
    private NFTViewModel viewModel;
    private Wallet wallet;
    private Token token;
    private boolean isGridView;
    private MenuItem sendMultipleTokensMenuItem;
    private MenuItem switchToGridViewMenuItem;
    private MenuItem switchToListViewMenuItem;
    private NFTAssetsFragment assetsFragment;

    private final ActivityResultLauncher<Intent> handleTransactionSuccess = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getData() == null) return;
                String transactionHash = result.getData().getStringExtra(C.EXTRA_TXHASH);
                //process hash
                if (!TextUtils.isEmpty(transactionHash))
                {
                    Intent intent = new Intent();
                    intent.putExtra(C.EXTRA_TXHASH, transactionHash);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nft);
        toolbar();
        initViewModel();
        getIntentData();
        setTitle(token.tokenInfo.name);
        isGridView = !hasTokenScriptOverride(token) && token.isERC875();
        setupViewPager();

        //check NFT events, expedite balance update
        syncListener();
        viewModel.checkEventsForToken(token);
        viewModel.updateAttributes(token);
    }

    private void syncListener()
    {
        getSupportFragmentManager()
                .setFragmentResultListener(SIGNAL_NFT_SYNC, this, (requestKey, b) ->
                {
                    CertifiedToolbarView certificateToolbar = findViewById(R.id.certified_toolbar);
                    if (!b.getBoolean(SYNC_STATUS, false))
                    {
                        certificateToolbar.nftSyncComplete();
                    }
                    else
                    {
                        certificateToolbar.showNFTSync();
                    }
                });
    }

    private boolean hasTokenScriptOverride(Token t)
    {
        return viewModel.getAssetDefinitionService().hasTokenView(t, AssetDefinitionService.ASSET_SUMMARY_VIEW_NAME);
    }

    private void onSignature(XMLDsigDescriptor descriptor)
    {
        CertifiedToolbarView certificateToolbar = findViewById(R.id.certified_toolbar);
        certificateToolbar.onSigData(descriptor, this);
    }

    public void storeAsset(BigInteger tokenId, NFTAsset asset)
    {
        viewModel.getTokensService().storeAsset(token, tokenId, asset);
    }

    private void initViewModel()
    {
        viewModel = new ViewModelProvider(this)
                .get(NFTViewModel.class);
        viewModel.sig().observe(this, this::onSignature);
        viewModel.tokenUpdate().observe(this, this::onBalanceUpdate);
        viewModel.scriptUpdateInProgress().observe(this, this::startScriptDownload);
        viewModel.newScriptFound().observe(this, this::newScriptFound);
    }

    private void newScriptFound(Boolean scriptUpdated)
    {
        //determinate signature
        if (token != null && scriptUpdated)
        {
            CertifiedToolbarView certificateToolbar = findViewById(R.id.certified_toolbar);
            certificateToolbar.stopDownload();
            certificateToolbar.setVisibility(View.VISIBLE);
            viewModel.checkTokenScriptValidity(token);
        }
    }

    private void startScriptDownload(Boolean status)
    {
        CertifiedToolbarView certificateToolbar = findViewById(R.id.certified_toolbar);
        certificateToolbar.setVisibility(View.VISIBLE);
        if (status)
        {
            certificateToolbar.startDownload();
        }
        else
        {
            certificateToolbar.stopDownload();
            certificateToolbar.hideCertificateResource();
        }
    }

    private void onBalanceUpdate(Token token)
    {
        assetsFragment.updateToken(token); //ensure token has the latest assets

        if (isGridView)
        {
            assetsFragment.showGridView();
        }
        else
        {
            assetsFragment.showListView();
        }
    }

    private void getIntentData()
    {
        Intent data = getIntent();
        if (data != null)
        {
            wallet = data.getParcelableExtra(WALLET);
            long chainId = data.getLongExtra(C.EXTRA_CHAIN_ID, EthereumNetworkBase.MAINNET_ID);
            String address = data.getStringExtra(C.EXTRA_ADDRESS);
            token = viewModel.getTokensService().getToken(chainId, address);
            viewModel.checkTokenScriptValidity(token);
            viewModel.checkForNewScript(token);
        }
        else
        {
            finish();
        }
    }

    private void setupViewPager()
    {
        NFTInfoFragment infoFragment = new NFTInfoFragment();
        assetsFragment = new NFTAssetsFragment();
        TokenActivityFragment tokenActivityFragment = new TokenActivityFragment();

        Bundle bundle = new Bundle();
        bundle.putLong(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
        bundle.putString(C.EXTRA_ADDRESS, token.getAddress());
        bundle.putParcelable(WALLET, wallet);
        infoFragment.setArguments(bundle);
        assetsFragment.setArguments(bundle);
        tokenActivityFragment.setArguments(bundle);

        List<Pair<String, Fragment>> pages = new ArrayList<>();
//        pages.add(0, new Pair<>("Info", infoFragment));
        pages.add(0, new Pair<>("Assets", assetsFragment));
        pages.add(1, new Pair<>("Activity", tokenActivityFragment));

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new TabPagerAdapter(this, pages));
        viewPager.setOffscreenPageLimit(pages.size());
        setupTabs(viewPager, pages);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (assetsFragment == null) recreate();
    }

    private void setupTabs(ViewPager2 viewPager, List<Pair<String, Fragment>> pages)
    {
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, viewPager,
                ((tab, position) -> tab.setText(pages.get(position).first))
        ).attach();

        TabUtils.decorateTabLayout(this, tabLayout);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener()
        {
            @Override
            public void onTabSelected(TabLayout.Tab tab)
            {
                switch (tab.getPosition())
                {
                    case 0:
                        showMenu();
                        break;
                    default:
                        hideMenu();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab)
            {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab)
            {

            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if (isGridView)
        {
            switchToListViewMenuItem.setVisible(true);
            switchToGridViewMenuItem.setVisible(false);
        }
        else
        {
            switchToListViewMenuItem.setVisible(false);
            switchToGridViewMenuItem.setVisible(!hasTokenScriptOverride(token));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_nft_display, menu);
        sendMultipleTokensMenuItem = menu.findItem(R.id.action_send_multiple_tokens);
        switchToGridViewMenuItem = menu.findItem(R.id.action_grid_view);
        switchToListViewMenuItem = menu.findItem(R.id.action_list_view);
        if (token.isBatchTransferAvailable())
        {
            sendMultipleTokensMenuItem.setVisible(true);
            sendMultipleTokensMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
            switchToGridViewMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
            switchToListViewMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        else
        {
            sendMultipleTokensMenuItem.setVisible(false);
            switchToGridViewMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
            switchToListViewMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.action_list_view)
        {
            isGridView = false;
            switchToListViewMenuItem.setVisible(false);
            switchToGridViewMenuItem.setVisible(true);
            assetsFragment.showListView();
        }
        else if (item.getItemId() == R.id.action_grid_view)
        {
            isGridView = true;
            switchToListViewMenuItem.setVisible(true);
            switchToGridViewMenuItem.setVisible(false);
            assetsFragment.showGridView();
        }
        else if (item.getItemId() == R.id.action_send_multiple_tokens)
        {
            handleTransactionSuccess.launch(viewModel.openSelectionModeIntent(this, token, wallet));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        viewModel.onDestroy();
    }

    private void hideMenu()
    {
        if (sendMultipleTokensMenuItem != null)
        {
            sendMultipleTokensMenuItem.setVisible(false);
        }
        if (switchToGridViewMenuItem != null)
        {
            switchToGridViewMenuItem.setVisible(false);
        }
        if (switchToListViewMenuItem != null)
        {
            switchToListViewMenuItem.setVisible(false);
        }
    }

    private void showMenu()
    {
        if (isGridView)
        {
            switchToListViewMenuItem.setVisible(true);
            switchToGridViewMenuItem.setVisible(false);
        }
        else
        {
            switchToListViewMenuItem.setVisible(false);
            switchToGridViewMenuItem.setVisible(!hasTokenScriptOverride(token));
        }

        if (token.isBatchTransferAvailable())
        {
            sendMultipleTokensMenuItem.setVisible(true);
        }
    }
}
