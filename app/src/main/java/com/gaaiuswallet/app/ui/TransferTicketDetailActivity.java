package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.C.EXTRA_STATE;
import static com.gaaiuswallet.app.C.EXTRA_TOKENID_LIST;
import static com.gaaiuswallet.app.C.GAS_LIMIT_MIN;
import static com.gaaiuswallet.app.C.PRUNE_ACTIVITY;
import static com.gaaiuswallet.app.entity.Operation.SIGN_DATA;
import static com.gaaiuswallet.app.widget.AWalletAlertDialog.ERROR;
import static com.gaaiuswallet.app.widget.AWalletAlertDialog.WARNING;
import static com.gaaiuswallet.ethereum.EthereumNetworkBase.MAINNET_ID;
import static org.web3j.crypto.WalletUtils.isValidAddress;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.analytics.Analytics;
import com.gaaiuswallet.app.entity.AnalyticsProperties;
import com.gaaiuswallet.app.entity.CustomViewSettings;
import com.gaaiuswallet.app.entity.DisplayState;
import com.gaaiuswallet.app.entity.ErrorEnvelope;
import com.gaaiuswallet.app.entity.GasEstimate;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.StandardFunctionInterface;
import com.gaaiuswallet.app.entity.TransactionReturn;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.entity.tokens.ERC721Token;
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
import com.gaaiuswallet.app.util.Utils;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by James on 21/02/2018.
 */
@AndroidEntryPoint
public class TransferTicketDetailActivity extends BaseActivity
        implements TokensAdapterCallback, StandardFunctionInterface, AddressReadyCallback, ActionSheetCallback
{
    protected TransferTicketDetailViewModel viewModel;
    private SystemView systemView;
    private ProgressView progressView;
    private AWalletAlertDialog dialog;
    private FunctionButtonBar functionBar;
    private Token token;
    private NonFungibleTokenAdapter adapter;
    private TextView validUntil;
    private TextView textQuantity;
    private String ticketIds;
    private List<BigInteger> selection;
    private DisplayState transferStatus;
    private InputAddress addressInput;
    private String sendAddress;
    private String ensAddress;
    private ActionSheetDialog actionDialog;
    private AWalletConfirmationDialog confirmationDialog;
    private AppCompatRadioButton pickLink;
    private AppCompatRadioButton pickTransfer;
    private LinearLayout pickTicketQuantity;
    private LinearLayout pickTransferMethod;
    private LinearLayout pickExpiryDate;
    private LinearLayout buttonLinkPick;
    private LinearLayout buttonTransferPick;
    private EditText expiryDateEditText;
    private EditText expiryTimeEditText;
    private DatePickerDialog datePickerDialog;
    private TimePickerDialog timePickerDialog;
    private SignAuthenticationCallback signCallback;
    private ActivityResultLauncher<Intent> getGasSettings;
    private ActivityResultLauncher<Intent> transferLinkFinalResult;

    @Nullable
    private Disposable calcGasCost;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transfer_detail);

        toolbar();

        getIntentData();

        initViewModel();

        initViews();

        initResultLaunchers();

        setupScreen();
    }

    private void getIntentData()
    {
        long chainId = getIntent().getLongExtra(C.EXTRA_CHAIN_ID, MAINNET_ID);
        token = viewModel.getTokenService().getToken(chainId, getIntent().getStringExtra(C.EXTRA_ADDRESS));
        ticketIds = getIntent().getStringExtra(EXTRA_TOKENID_LIST);
        transferStatus = DisplayState.values()[getIntent().getIntExtra(EXTRA_STATE, 0)];
        selection = token.stringHexToBigIntegerList(ticketIds);
    }

    private void initResultLaunchers()
    {
        getGasSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> actionDialog.setCurrentGasIndex(result));

        transferLinkFinalResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(PRUNE_ACTIVITY)); //TODO: implement prune via result codes
                });
    }

    private void initViews()
    {
        systemView = findViewById(R.id.system_view);
        systemView.hide();
        progressView = findViewById(R.id.progress_view);
        progressView.hide();

        addressInput = findViewById(R.id.input_address);
        addressInput.setAddressCallback(this);

        sendAddress = null;
        ensAddress = null;

        //we should import a token and a list of chosen ids
        RecyclerView list = findViewById(R.id.listTickets);
        adapter = new NonFungibleTokenAdapter(null, token, selection, viewModel.getAssetDefinitionService());
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        textQuantity = findViewById(R.id.text_quantity);
        validUntil = findViewById(R.id.text_valid_until);

        pickTicketQuantity = findViewById(R.id.layout_ticket_quantity);
        pickTransferMethod = findViewById(R.id.layout_choose_method);
        pickExpiryDate = findViewById(R.id.layout_date_picker);
        functionBar = findViewById(R.id.layoutButtons);

        expiryDateEditText = findViewById(R.id.edit_expiry_date);
        expiryDateEditText.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @Override
            @SuppressLint("StringFormatInvalid")
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                validUntil.setText(getString(R.string.link_valid_until, s.toString(), expiryTimeEditText.getText().toString()));
            }

            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });

        expiryTimeEditText = findViewById(R.id.edit_expiry_time);
        expiryTimeEditText.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @SuppressLint("StringFormatInvalid")
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                validUntil.setText(getString(R.string.link_valid_until, expiryDateEditText.getText().toString(), s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });

        pickLink = findViewById(R.id.radio_pickup_link);
        pickTransfer = findViewById(R.id.radio_transfer_now);

        buttonLinkPick = findViewById(R.id.layout_link_pick);
        buttonTransferPick = findViewById(R.id.layout_transfer_now);

        functionBar.setupFunctions(this, new ArrayList<>(Collections.singletonList(R.string.action_next)));
        functionBar.revealButtons();
    }

    private void initViewModel()
    {
        viewModel = new ViewModelProvider(this)
                .get(TransferTicketDetailViewModel.class);
        viewModel.progress().observe(this, systemView::showProgress);
        viewModel.queueProgress().observe(this, progressView::updateProgress);
        viewModel.pushToast().observe(this, this::displayToast);
        viewModel.newTransaction().observe(this, this::onTransaction);
        viewModel.error().observe(this, this::onError);
        viewModel.universalLinkReady().observe(this, this::linkReady);
        viewModel.transactionFinalised().observe(this, this::txWritten);
        viewModel.transactionError().observe(this, this::txError);
    }

    //TODO: This is repeated code also in SellDetailActivity. Probably should be abstracted out into generic view code routine
    private void initQuantitySelector()
    {
        pickTicketQuantity.setVisibility(View.VISIBLE);
        RelativeLayout plusButton = findViewById(R.id.layout_quantity_add);
        plusButton.setOnClickListener(v -> {
            int quantity = Integer.parseInt(textQuantity.getText().toString());
            if ((quantity + 1) <= adapter.getTicketRangeCount())
            {
                quantity++;
                textQuantity.setText(String.valueOf(quantity));
                selection = token.pruneIDList(ticketIds, quantity);
            }
        });

        RelativeLayout minusButton = findViewById(R.id.layout_quantity_minus);
        minusButton.setOnClickListener(v -> {
            int quantity = Integer.parseInt(textQuantity.getText().toString());
            if ((quantity - 1) > 0)
            {
                quantity--;
                textQuantity.setText(String.valueOf(quantity));
                selection = token.pruneIDList(ticketIds, 1);
            }
        });

        textQuantity.setText("1");
        selection = token.pruneIDList(ticketIds, 1);
    }

    private void setupRadioButtons()
    {
        buttonLinkPick.setSelected(true);
        buttonLinkPick.setOnClickListener((View v) -> {
            pickLink.setChecked(true);
            pickTransfer.setChecked(false);
            buttonLinkPick.setSelected(true);
            buttonTransferPick.setSelected(false);
        });

        buttonTransferPick.setOnClickListener((View v) -> {
            pickLink.setChecked(false);
            pickTransfer.setChecked(true);
            buttonLinkPick.setSelected(false);
            buttonTransferPick.setSelected(true);
        });

        pickLink.setOnClickListener((View v) -> {
            pickLink.setChecked(true);
            pickTransfer.setChecked(false);
            buttonLinkPick.setSelected(true);
            buttonTransferPick.setSelected(false);
        });

        pickTransfer.setOnClickListener((View v) -> {
            pickLink.setChecked(false);
            pickTransfer.setChecked(true);
            buttonLinkPick.setSelected(false);
            buttonTransferPick.setSelected(true);
        });
    }

    private DisplayState getNextState()
    {
        DisplayState newState = DisplayState.NO_ACTION;

        switch (transferStatus)
        {
            case CHOOSE_QUANTITY:
                if (CustomViewSettings.hasDirectTransfer())
                {
                    newState = DisplayState.PICK_TRANSFER_METHOD;
                }
                else
                {
                    newState = DisplayState.TRANSFER_USING_LINK;
                }
                break;
            case PICK_TRANSFER_METHOD:
                if (pickTransfer.isChecked())
                {
                    newState = DisplayState.TRANSFER_TO_ADDRESS;
                }
                else
                {
                    newState = DisplayState.TRANSFER_USING_LINK;
                }
                break;
            case TRANSFER_USING_LINK:
                //generate link
                getAuthenticationForLinkGeneration();
                break;
            case TRANSFER_TO_ADDRESS:
                addressInput.getAddress(); // ask address module to supply address
                break;
        }

        return newState;
    }

    private void getAuthenticationForLinkGeneration()
    {
        signCallback = new SignAuthenticationCallback()
        {
            @Override
            public void gotAuthorisation(boolean gotAuth)
            {
                if (gotAuth) viewModel.completeAuthentication(SIGN_DATA);
                else viewModel.failedAuthentication(SIGN_DATA);

                if (gotAuth)
                {
                    if (token.isERC721Ticket())
                    {
                        viewModel.generateSpawnLink(selection, token.getAddress(), calculateExpiryTime());
                    }
                    else
                    {
                        viewModel.generateUniversalLink(token.getTransferListFormat(selection), token.getAddress(), calculateExpiryTime());
                    }
                }
                else
                {
                    //display fail auth
                    onError(new ErrorEnvelope(getString(R.string.authentication_failed)));
                }
            }

            @Override
            public void cancelAuthentication()
            {

            }

            @Override
            public void gotSignature(SignatureFromKey signature)
            {
                //TODO: Hardware
            }
        };

        viewModel.getAuthorisation(this, signCallback);
    }

    private long calculateExpiryTime()
    {
        String expiryDate = expiryDateEditText.getText().toString();
        String expiryTime = expiryTimeEditText.getText().toString();
        String tempDateString = expiryDate + " " + expiryTime;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Date date;
        String dateString = "";
        long UTCTimeStamp = 0;
        try
        {
            date = simpleDateFormat.parse(tempDateString);
            dateString = simpleDateFormat.format(date);
            Timber.d("date : %s", dateString);
            UTCTimeStamp = (date.getTime()) / 1000;
        }
        catch (ParseException e)
        {
            Timber.e(e, e.getMessage());
        }

        return UTCTimeStamp;
    }

    private void setupScreen()
    {
        addressInput.setVisibility(View.GONE);
        pickTicketQuantity.setVisibility(View.GONE);
        pickTransferMethod.setVisibility(View.GONE);
        pickExpiryDate.setVisibility(View.GONE);

        switch (transferStatus)
        {
            case CHOOSE_QUANTITY:
                initQuantitySelector();
                pickTicketQuantity.setVisibility(View.VISIBLE);
                String typeName = viewModel.getAssetDefinitionService().getTokenName(token.tokenInfo.chainId, token.tokenInfo.address, 1);
                setTitle(getString(R.string.title_select_ticket_quantity, typeName != null ? typeName : getString(R.string.ticket)));
                break;
            case PICK_TRANSFER_METHOD:
                setupRadioButtons();
                pickTransferMethod.setVisibility(View.VISIBLE);
                setTitle(R.string.title_select_transfer_method);
                break;
            case TRANSFER_USING_LINK:
                initDatePicker();
                initTimePicker();
                expiryDateEditText.setOnClickListener(v -> datePickerDialog.show());
                expiryTimeEditText.setOnClickListener(v -> timePickerDialog.show());
                pickExpiryDate.setVisibility(View.VISIBLE);
                setTitle(R.string.title_set_universal_link_expiry);
                break;
            case TRANSFER_TO_ADDRESS:
                addressInput.setVisibility(View.VISIBLE);
                setTitle(R.string.title_input_wallet_address);
                break;
        }
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

    private void onProgress(boolean shouldShowProgress)
    {
        hideDialog();
        if (shouldShowProgress)
        {
            dialog = new AWalletAlertDialog(this);
            dialog.setIcon(AWalletAlertDialog.NONE);
            dialog.setTitle(R.string.title_dialog_sending);
            dialog.setMessage(R.string.transfer);
            dialog.setProgressMode();
            dialog.setCancelable(false);
            dialog.show();
        }
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

    private void transferTicketFinal(String to)
    {
        //complete the transfer
        onProgress(true);

        viewModel.createTokenTransfer(
                to,
                token,
                token.getTransferListFormat(selection));
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
                //signCallback.gotAuthorisation(resultCode == RESULT_OK);
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

    private void linkReady(String universalLink)
    {
        int quantity = 1;
        if (selection != null)
        {
            quantity = selection.size();
        }
        int ticketName = (quantity > 1) ? R.string.tickets : R.string.ticket;
        String qty = quantity + " " +
                getResources().getString(ticketName) + "\n" +
                getString(R.string.universal_link_expiry_on) + expiryDateEditText.getText().toString() + " " + expiryTimeEditText.getText().toString();

        confirmationDialog = new AWalletConfirmationDialog(this);
        confirmationDialog.setTitle(R.string.generate_pick_up_link);
        confirmationDialog.setSmallText(R.string.generate_free_transfer_link);
        confirmationDialog.setMediumText(qty);
        confirmationDialog.setPrimaryButtonText(R.string.send_universal_link);
        confirmationDialog.setSecondaryButtonText(R.string.dialog_cancel_back);
        confirmationDialog.setPrimaryButtonListener(v1 -> transferLinkFinal(universalLink));
        confirmationDialog.setSecondaryButtonListener(v1 -> confirmationDialog.dismiss());
        confirmationDialog.show();
    }

    private void handleERC875Transfer(final String to, final String ensName)
    {
        //how many indices are we selling?
        int quantity = selection.size();
        int ticketName = (quantity > 1) ? R.string.tickets : R.string.ticket;

        String toAddress = (ensName == null) ? to : ensName;

        String qty = quantity + " " +
                getResources().getString(ticketName) + "\n" +
                getResources().getString(R.string.to) + " " +
                toAddress;

        signCallback = new SignAuthenticationCallback()
        {
            @Override
            public void gotAuthorisation(boolean gotAuth)
            {
                if (gotAuth) viewModel.completeAuthentication(SIGN_DATA);
                else viewModel.failedAuthentication(SIGN_DATA);

                if (gotAuth) transferTicketFinal(to);
            }

            @Override
            public void cancelAuthentication()
            {
                AWalletAlertDialog dialog = new AWalletAlertDialog(getParent());
                dialog.setIcon(AWalletAlertDialog.NONE);
                dialog.setTitle(R.string.authentication_cancelled);
                dialog.setButtonText(R.string.ok);
                dialog.setButtonListener(v -> {
                    dialog.dismiss();
                });
                dialog.setCancelable(true);
                dialog.show();
            }

            @Override
            public void gotSignature(SignatureFromKey signature)
            {
                //TODO: Hardware
            }
        };

        confirmationDialog = new AWalletConfirmationDialog(this);
        confirmationDialog.setTitle(R.string.title_transaction_details);
        confirmationDialog.setSmallText(R.string.confirm_transfer_details);
        confirmationDialog.setMediumText(qty);
        confirmationDialog.setPrimaryButtonText(R.string.transfer_tickets);
        confirmationDialog.setSecondaryButtonText(R.string.dialog_cancel_back);
        confirmationDialog.setPrimaryButtonListener(v1 -> viewModel.getAuthorisation(this, signCallback));
        confirmationDialog.setSecondaryButtonListener(v1 -> confirmationDialog.dismiss());
        confirmationDialog.show();
    }

    private void transferLinkFinal(String universalLink)
    {
        //create share intent
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, universalLink);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Magic Link");
        sendIntent.setType("text/plain");
        transferLinkFinalResult.launch(Intent.createChooser(sendIntent, "Share via"));
    }

    private void initDatePicker()
    {
        String dateFormat = "dd/MM/yyyy";
        Calendar newCalendar = Calendar.getInstance();
        SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat, Locale.ENGLISH);

        datePickerDialog = new DatePickerDialog(this, (view, year, monthOfYear, dayOfMonth) -> {
            Calendar newDate = Calendar.getInstance();
            newDate.set(year, monthOfYear, dayOfMonth);
            expiryDateEditText.setText(dateFormatter.format(newDate.getTime()));
        }, newCalendar.get(Calendar.YEAR), newCalendar.get(Calendar.MONTH), newCalendar.get(Calendar.DAY_OF_MONTH));

        //set default for tomorrow
        long tomorrowStamp = System.currentTimeMillis() + 1000 * 60 * 60 * 24;
        Date tomorrow = new Date(tomorrowStamp);
        expiryDateEditText.setText(dateFormatter.format(tomorrow.getTime()));
    }

    private void initTimePicker()
    {
        Calendar newCalendar = Calendar.getInstance();
        timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            expiryTimeEditText.setText(time);
        }, newCalendar.get(Calendar.HOUR_OF_DAY), newCalendar.get(Calendar.MINUTE), true);

        //set for now
        String time = String.format(Locale.getDefault(), "%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().get(Calendar.MINUTE));
        expiryTimeEditText.setText(time);
    }

    @Override
    public void handleClick(String action, int actionId)
    {
        //clicked the next button
        if (actionId == R.string.action_next)
        {
            KeyboardUtils.hideKeyboard(getCurrentFocus());
            addressInput.getAddress();

            if (addressInput.getVisibility() != View.VISIBLE)
            {
                // go to next screen
                viewModel.openTransferState(this, token, Utils.bigIntListToString(selection, false), getNextState());
            }
        }
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
            if (token instanceof ERC721Token)
            {
                calculateTransactionCost();
            }
            else
            {
                handleERC875Transfer(address, ensName);
            }
        }
    }

    private void calculateTransactionCost()
    {
        if ((calcGasCost != null && !calcGasCost.isDisposed()) ||
                (actionDialog != null && actionDialog.isShowing())) return;

        final String txSendAddress = sendAddress;
        sendAddress = null;

        final byte[] transactionBytes = viewModel.getERC721TransferBytes(txSendAddress, token.getAddress(), ticketIds, token.tokenInfo.chainId);
        if (token.isEthereum())
        {
            checkConfirm(new GasEstimate(BigInteger.valueOf(GAS_LIMIT_MIN)), transactionBytes, txSendAddress, txSendAddress);
        }
        else
        {
            calculateEstimateDialog();
            //form payload and calculate tx cost
            calcGasCost = viewModel.calculateGasEstimate(viewModel.getWallet(), transactionBytes, token.tokenInfo.chainId, token.getAddress(), BigDecimal.ZERO)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(estimate -> checkConfirm(estimate, transactionBytes, token.getAddress(), txSendAddress),
                            error -> handleError(error, transactionBytes, token.getAddress(), txSendAddress));
        }
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

    private void txWritten(TransactionReturn transactionReturn)
    {
        actionDialog.transactionWritten(transactionReturn.hash);
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
}
