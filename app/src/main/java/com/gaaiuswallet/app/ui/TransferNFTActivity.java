package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.widget.AWalletAlertDialog.ERROR;
import static com.gaaiuswallet.app.widget.AWalletAlertDialog.WARNING;
import static org.web3j.crypto.WalletUtils.isValidAddress;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.entity.EnsNodeNotSyncCallback;
import com.gaaiuswallet.app.entity.ErrorEnvelope;
import com.gaaiuswallet.app.entity.GasEstimate;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.StandardFunctionInterface;
import com.gaaiuswallet.app.entity.TransactionReturn;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.ui.QRScanning.QRScannerActivity;
import com.gaaiuswallet.app.ui.widget.TokensAdapterCallback;
import com.gaaiuswallet.app.ui.widget.adapter.NonFungibleTokenAdapter;
import com.gaaiuswallet.app.ui.widget.entity.ActionSheetCallback;
import com.gaaiuswallet.app.ui.widget.entity.AddressReadyCallback;
import com.gaaiuswallet.app.util.KeyboardUtils;
import com.gaaiuswallet.app.util.QRParser;
import com.gaaiuswallet.app.util.ShortcutUtils;
import com.gaaiuswallet.app.viewmodel.TransferTicketDetailViewModel;
import com.gaaiuswallet.app.web3.entity.Address;
import com.gaaiuswallet.app.web3.entity.Web3Transaction;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;
import com.gaaiuswallet.app.widget.AWalletConfirmationDialog;
import com.gaaiuswallet.app.widget.ActionSheetDialog;
import com.gaaiuswallet.app.widget.FunctionButtonBar;
import com.gaaiuswallet.app.widget.InputAddress;
import com.gaaiuswallet.app.widget.ProgressView;
import com.gaaiuswallet.app.widget.SignTransactionDialog;
import com.gaaiuswallet.app.widget.SystemView;
import com.gaaiuswallet.hardware.SignatureFromKey;

import org.jetbrains.annotations.NotNull;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by JB on 11/08/2021
 */
@AndroidEntryPoint
public class TransferNFTActivity extends BaseActivity implements TokensAdapterCallback, StandardFunctionInterface, AddressReadyCallback, ActionSheetCallback, EnsNodeNotSyncCallback
{
    protected TransferTicketDetailViewModel viewModel;
    private AWalletAlertDialog dialog;

    private Token token;
    private ArrayList<Pair<BigInteger, NFTAsset>> assetSelection;

    private InputAddress addressInput;
    private String sendAddress;
    private String ensAddress;

    private ActionSheetDialog actionDialog;
    private AWalletConfirmationDialog confirmationDialog;

    @Nullable
    private Disposable calcGasCost;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_nft);
        viewModel = new ViewModelProvider(this)
                .get(TransferTicketDetailViewModel.class);

        long chainId = getIntent().getLongExtra(C.EXTRA_CHAIN_ID, com.gaaiuswallet.ethereum.EthereumNetworkBase.MAINNET_ID);
        String walletAddress = getIntent().getStringExtra(C.Key.WALLET);
        viewModel.loadWallet(walletAddress);
        token = viewModel.getTokenService().getToken(walletAddress, chainId, getIntent().getStringExtra(C.EXTRA_ADDRESS));

        String tokenIds = getIntent().getStringExtra(C.EXTRA_TOKENID_LIST);
        List<BigInteger> tokenIdList = token.stringHexToBigIntegerList(tokenIds);
        List<NFTAsset> assets = getIntent().getParcelableArrayListExtra(C.EXTRA_NFTASSET_LIST);
        assetSelection = formAssetSelection(tokenIdList, assets);

        toolbar();
        SystemView systemView = findViewById(R.id.system_view);
        systemView.hide();
        ProgressView progressView = findViewById(R.id.progress_view);
        progressView.hide();

        addressInput = findViewById(R.id.input_address);
        addressInput.setAddressCallback(this);
        //addressInput.setEnsNodeNotSyncCallback(this);

        sendAddress = null;
        ensAddress = null;

        viewModel.progress().observe(this, systemView::showProgress);
        viewModel.queueProgress().observe(this, progressView::updateProgress);
        viewModel.pushToast().observe(this, this::displayToast);
        viewModel.newTransaction().observe(this, this::onTransaction);
        viewModel.error().observe(this, this::onError);
        viewModel.transactionFinalised().observe(this, this::txWritten);
        viewModel.transactionError().observe(this, this::txError);
        //we should import a token and a list of chosen ids
        RecyclerView list = findViewById(R.id.listTickets);

        NonFungibleTokenAdapter adapter = new NonFungibleTokenAdapter(null, token, assetSelection, viewModel.getAssetDefinitionService());
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        final FunctionButtonBar functionBar = findViewById(R.id.layoutButtons);

        functionBar.setupFunctions(this, new ArrayList<>(Collections.singletonList(R.string.action_transfer)));
        functionBar.revealButtons();

        setupScreen();
    }

    private boolean confirmRemoveShortcuts(ArrayList<Pair<BigInteger, NFTAsset>> tokenIdList, Token token)
    {
        List<String> shortcutIds = ShortcutUtils.getShortcutIds(getApplicationContext(), token, tokenIdList);
        if (!shortcutIds.isEmpty())
        {
            ShortcutUtils.showConfirmationDialog(this, shortcutIds, getString(R.string.remove_shortcut_reminder), this);
            return true;
        }
        else
        {
            return false;
        }
    }

    private void setupScreen()
    {
        addressInput.setVisibility(View.GONE);
        addressInput.setVisibility(View.VISIBLE);
        setTitle(getString(R.string.send_tokens));
    }

    private void onTransaction(String success)
    {
        hideDialog();
        dialog = new AWalletAlertDialog(this);
        dialog.setTitle(R.string.transaction_succeeded);
        dialog.setMessage(success);
        dialog.setIcon(AWalletAlertDialog.SUCCESS);
        dialog.setButtonText(R.string.button_ok);
        dialog.setButtonListener(v -> finish());

        dialog.show();
    }

    private void hideDialog()
    {
        if (dialog != null && dialog.isShowing())
        {
            dialog.dismiss();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        viewModel.resetSignDialog();
    }

    private void onError(@NotNull ErrorEnvelope error)
    {
        hideDialog();
        dialog = new AWalletAlertDialog(this);
        dialog.setIcon(AWalletAlertDialog.ERROR);
        dialog.setTitle(R.string.error_transaction_failed);
        dialog.setMessage(error.message);
        dialog.setCancelable(true);
        dialog.setButtonText(R.string.button_ok);
        dialog.setButtonListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        viewModel.prepare(token);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        viewModel.stopGasSettingsFetch();
        if (confirmationDialog != null && confirmationDialog.isShowing())
        {
            confirmationDialog.dismiss();
            confirmationDialog = null;
        }
    }

    @Override
    public void onTokenClick(View view, Token token, List<BigInteger> ids, boolean selection)
    {
        Context context = view.getContext();
        //TODO: what action should be performed when clicking on a range?
    }

    @Override
    public void onLongTokenClick(View view, Token token, List<BigInteger> tokenId)
    {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10)
        {
            requestCode = SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS;
        }

        switch (requestCode)
        {
            case C.BARCODE_READER_REQUEST_CODE:
                switch (resultCode)
                {
                    case Activity.RESULT_OK:
                        if (data != null)
                        {
                            String barcode = data.getStringExtra(C.EXTRA_QR_CODE);

                            //if barcode is still null, ensure we don't GPF
                            if (barcode == null)
                            {
                                Toast.makeText(this, R.string.toast_qr_code_no_address, Toast.LENGTH_SHORT).show();
                                return;
                            }

                            QRParser parser = QRParser.getInstance(EthereumNetworkBase.extraChains());
                            String extracted_address = parser.extractAddressFromQrString(barcode);
                            if (extracted_address == null)
                            {
                                Toast.makeText(this, R.string.toast_qr_code_no_address, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            addressInput.setAddress(extracted_address);
                        }
                        break;
                    case QRScannerActivity.DENY_PERMISSION:
                        showCameraDenied();
                        break;
                    default:
                        Timber.tag("SEND").e(String.format(getString(R.string.barcode_error_format),
                                "Code: " + resultCode
                        ));
                        break;
                }
                break;
            case C.COMPLETED_TRANSACTION:
                Intent i = new Intent();
                i.putExtra(C.EXTRA_TXHASH, data.getStringExtra(C.EXTRA_TXHASH));
                setResult(RESULT_OK, new Intent());
                finish();
                break;
            case SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS:
                if (actionDialog != null && actionDialog.isShowing())
                    actionDialog.completeSignRequest(resultCode == RESULT_OK);
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showCameraDenied()
    {
        dialog = new AWalletAlertDialog(this);
        dialog.setTitle(R.string.title_dialog_error);
        dialog.setMessage(R.string.error_camera_permission_denied);
        dialog.setIcon(ERROR);
        dialog.setButtonText(R.string.button_ok);
        dialog.setButtonListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    @Override
    public void addressReady(String address, String ensName)
    {
        sendAddress = address;
        ensAddress = ensName;
        //complete the transfer
        if (TextUtils.isEmpty(address) || !isValidAddress(address))
        {
            //show address error
            addressInput.setError(getString(R.string.error_invalid_address));
        }
        else
        {
            calculateTransactionCost();
        }
    }

    @Override
    public void showTransferToken(List<BigInteger> selection)
    {
        if (!confirmRemoveShortcuts(assetSelection, token))
        {
            KeyboardUtils.hideKeyboard(getCurrentFocus());
            addressInput.getAddress();
        }
    }

    private void calculateTransactionCost()
    {
        if ((calcGasCost != null && !calcGasCost.isDisposed()) ||
                (actionDialog != null && actionDialog.isShowing())) return;

        final String txSendAddress = sendAddress;
        sendAddress = null;

        final byte[] transactionBytes = token.getTransferBytes(txSendAddress, assetSelection);

        calculateEstimateDialog();
        //form payload and calculate tx cost
        calcGasCost = viewModel.calculateGasEstimate(viewModel.getWallet(), transactionBytes, token.tokenInfo.chainId, token.getAddress(), BigDecimal.ZERO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(estimate -> checkConfirm(estimate, transactionBytes, token.getAddress(), txSendAddress),
                        error -> handleError(error, transactionBytes, token.getAddress(), txSendAddress));
    }

    private void handleError(Throwable throwable, final byte[] transactionBytes, final String txSendAddress, final String resolvedAddress)
    {
        Timber.e(throwable);
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        displayErrorMessage(throwable.getMessage());
    }


    private void calculateEstimateDialog()
    {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new AWalletAlertDialog(this);
        dialog.setTitle(getString(R.string.calc_gas_limit));
        dialog.setIcon(AWalletAlertDialog.NONE);
        dialog.setProgressMode();
        dialog.setCancelable(false);
        dialog.show();
    }

    /**
     * Called to check if we're ready to send user to confirm screen / activity sheet popup
     */
    private void checkConfirm(GasEstimate estimate, final byte[] transactionBytes, final String txSendAddress, final String resolvedAddress)
    {
        Web3Transaction w3tx = new Web3Transaction(
                new Address(txSendAddress),
                new Address(token.getAddress()),
                BigInteger.ZERO,
                BigInteger.ZERO,
                estimate.getValue(),
                -1,
                Numeric.toHexString(transactionBytes),
                -1);

        if (estimate.hasError() || estimate.getValue().equals(BigInteger.ZERO))
        {
            estimateError(estimate, w3tx, transactionBytes, txSendAddress, resolvedAddress);
        }
        else
        {
            if (dialog != null && dialog.isShowing())
            {
                dialog.dismiss();
            }

            actionDialog = new ActionSheetDialog(this, w3tx, token, ensAddress,
                    resolvedAddress, viewModel.getTokenService(), this);
            actionDialog.setCanceledOnTouchOutside(false);
            actionDialog.show();
        }
    }

    /**
     * ActionSheetCallback, comms hooks for the ActionSheetDialog to trigger authentication & send transactions
     *
     * @param callback
     */
    @Override
    public void getAuthorisation(SignAuthenticationCallback callback)
    {
        viewModel.getAuthentication(this, viewModel.getWallet(), callback);
    }

    @Override
    public void sendTransaction(Web3Transaction finalTx)
    {
        viewModel.requestSignature(finalTx, viewModel.getWallet(), token.tokenInfo.chainId);
    }

    @Override
    public void completeSendTransaction(Web3Transaction tx, SignatureFromKey signature)
    {
        viewModel.sendTransaction(viewModel.getWallet(), token.tokenInfo.chainId, tx, signature);
    }

    @Override
    public void dismissed(String txHash, long callbackId, boolean actionCompleted)
    {
        //ActionSheet was dismissed
        if (!TextUtils.isEmpty(txHash))
        {
            Intent intent = new Intent();
            intent.putExtra(C.EXTRA_TXHASH, txHash);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void notifyConfirm(String mode)
    {
        AnalyticsProperties props = new AnalyticsProperties();
        props.put(Analytics.PROPS_ACTION_SHEET_MODE, mode);
        viewModel.track(Analytics.Action.ACTION_SHEET_COMPLETED, props);
    }

    ActivityResultLauncher<Intent> getGasSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> actionDialog.setCurrentGasIndex(result));

    @Override
    public ActivityResultLauncher<Intent> gasSelectLauncher()
    {
        return getGasSettings;
    }

    @Override
    public WalletType getWalletType()
    {
        return viewModel.getWallet().type;
    }

    @Override
    public GasService getGasService()
    {
        return viewModel.getGasService();
    }

    private void txWritten(TransactionReturn txReturn)
    {
        actionDialog.transactionWritten(txReturn.hash);
    }

    //Transaction failed to be sent
    private void txError(TransactionReturn txError)
    {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new AWalletAlertDialog(this);
        dialog.setIcon(ERROR);
        dialog.setTitle(R.string.error_transaction_failed);
        dialog.setMessage(txError.throwable.getMessage());
        dialog.setButtonText(R.string.button_ok);
        dialog.setButtonListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
        actionDialog.dismiss();
    }

    private void estimateError(GasEstimate estimate, final Web3Transaction w3tx, final byte[] transactionBytes, final String txSendAddress, final String resolvedAddress)
    {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new AWalletAlertDialog(this);
        dialog.setIcon(WARNING);
        dialog.setTitle(estimate.hasError() ?
            R.string.dialog_title_gas_estimation_failed :
            R.string.confirm_transaction
        );
        String message = estimate.hasError() ?
            getString(R.string.dialog_message_gas_estimation_failed, estimate.getError()) :
            getString(R.string.error_transaction_may_fail);
        dialog.setMessage(message);
        dialog.setButtonText(R.string.action_proceed);
        dialog.setSecondaryButtonText(R.string.action_cancel);
        dialog.setButtonListener(v -> {
            BigInteger gasEstimate = GasService.getDefaultGasLimit(token, w3tx);
            checkConfirm(new GasEstimate(gasEstimate), transactionBytes, txSendAddress, resolvedAddress);
        });

        dialog.setSecondaryButtonListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private ArrayList<Pair<BigInteger, NFTAsset>> formAssetSelection(List<BigInteger> tokenIdList, List<NFTAsset> assets)
    {
        ArrayList<Pair<BigInteger, NFTAsset>> assetList = new ArrayList<>();
        if (tokenIdList.size() != assets.size())
        {
            if (BuildConfig.DEBUG) //warn developer of problem
            {
                throw new RuntimeException("Token ID list size != Asset size");
            }
            else if (assets.size() < tokenIdList.size())  //ensure below code doesn't crash
            {
                tokenIdList = tokenIdList.subList(0, assets.size());
            }
        }

        for (int i = 0; i < tokenIdList.size(); i++)
        {
            assetList.add(new Pair<>(tokenIdList.get(i), assets.get(i)));
        }

        return assetList;
    }

    @Override
    public void onNodeNotSynced()
    {
        Timber.d("onNodeNotSynced: ");
        if (dialog != null && dialog.isShowing())
        {
            dialog.dismiss();
        }
        try
        {
            dialog = new AWalletAlertDialog(this, R.drawable.ic_warning);
            dialog.setTitle(R.string.title_ens_lookup_warning);
            dialog.setMessage(R.string.message_ens_node_not_sync);
            dialog.setButtonText(R.string.action_cancel);
            dialog.setButtonListener(v -> dialog.dismiss());
            dialog.setSecondaryButtonText(R.string.ignore);
            dialog.setSecondaryButtonListener(v -> {
                //addressInput.setEnsHandlerNodeSyncFlag(false);  // skip node sync check
                // re enter current input to resolve again
                String currentInput = addressInput.getEditText().getText().toString();
                addressInput.getEditText().setText("");
                addressInput.getEditText().setText(currentInput);
                addressInput.getEditText().setSelection(currentInput.length());
                dialog.dismiss();
            });
            dialog.show();
        }
        catch (Exception e)
        {
            Timber.e(e, "onNodeNotSynced: ");
        }
    }
}
