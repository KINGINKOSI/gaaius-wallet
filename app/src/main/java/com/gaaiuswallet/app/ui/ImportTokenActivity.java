package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.C.IMPORT_STRING;
import static com.gaaiuswallet.app.entity.Operation.SIGN_DATA;
import static com.gaaiuswallet.ethereum.EthereumNetworkBase.GNOSIS_ID;
import static com.gaaiuswallet.token.tools.Convert.getEthString;
import static com.gaaiuswallet.token.tools.ParseMagicLink.currencyLink;
import static com.gaaiuswallet.token.tools.ParseMagicLink.spawnable;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.CryptoFunctions;
import com.gaaiuswallet.app.entity.ErrorEnvelope;
import com.gaaiuswallet.app.entity.NetworkInfo;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.tokendata.TokenTicker;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.router.HomeRouter;
import com.gaaiuswallet.app.service.TickerService;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.viewmodel.ImportTokenViewModel;
import com.gaaiuswallet.app.web3.Web3TokenView;
import com.gaaiuswallet.app.web3.entity.PageReadyCallback;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;
import com.gaaiuswallet.app.widget.AWalletConfirmationDialog;
import com.gaaiuswallet.app.widget.CertifiedToolbarView;
import com.gaaiuswallet.app.widget.SignTransactionDialog;
import com.gaaiuswallet.hardware.SignatureFromKey;
import com.gaaiuswallet.token.entity.MagicLinkData;
import com.gaaiuswallet.token.entity.TicketRange;
import com.gaaiuswallet.token.tools.Convert;
import com.gaaiuswallet.token.tools.ParseMagicLink;

import java.math.BigDecimal;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Created by James on 9/03/2018.
 */
@AndroidEntryPoint
public class ImportTokenActivity extends BaseActivity implements View.OnClickListener, SignAuthenticationCallback, PageReadyCallback
{
    private ImportTokenViewModel viewModel;
    private TicketRange ticketRange;
    private String importString;
    private AWalletAlertDialog aDialog;
    private AWalletConfirmationDialog cDialog;
    private CertifiedToolbarView toolbarView;
    private Web3TokenView tokenView;
    private LinearLayout webWrapper;

    private TextView priceETH;
    private TextView priceUSD;
    private TextView priceUSDLabel;
    private TextView importTxt;

    private LinearLayout costLayout;
    private boolean usingFeeMaster = false;
    private final String paymasterUrlPrefix = "https://paymaster.stormbird.sg/api/";
    private final String TAG = "ITA";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_import_token);
        toolbar();

        setTitle(getString(R.string.toolbar_header_importing_tickets));

        importString = getIntent().getStringExtra(IMPORT_STRING);
        priceETH = findViewById(R.id.textImportPrice);
        priceUSD = findViewById(R.id.textImportPriceUSD);
        priceUSDLabel = findViewById(R.id.fiat_price_txt);
        toolbarView = findViewById(R.id.certified_toolbar);
        tokenView = findViewById(R.id.web3_tokenview);
        webWrapper = findViewById(R.id.layout_webwrapper);
        priceETH.setVisibility(View.GONE);
        priceUSD.setVisibility(View.GONE);
        priceUSDLabel.setVisibility(View.GONE);

        importTxt = findViewById(R.id.textImport);
        costLayout = findViewById(R.id.cost_layout);

        setTicket(false, true, false);

        Button importTickets = findViewById(R.id.import_ticket);
        importTickets.setOnClickListener(this);
        importTickets.setAlpha(0.4f);
        Button cancel = findViewById(R.id.cancel_button);
        cancel.setOnClickListener(this);

        viewModel = new ViewModelProvider(this)
                .get(ImportTokenViewModel.class);
        viewModel.importRange().observe(this, this::onImportRange);
        viewModel.invalidRange().observe(this, this::invalidTicket);
        viewModel.invalidTime().observe(this, this::invalidTime);
        viewModel.newTransaction().observe(this, this::onTransaction);
        viewModel.error().observe(this, this::onError);
        viewModel.txError().observe(this, this::onTxError);
        viewModel.invalidLink().observe(this, this::onBadLink);
        viewModel.network().observe(this, this::onNetwork);
        viewModel.checkContractNetwork().observe(this, this::checkContractNetwork);
        viewModel.ticketNotValid().observe(this, this::onInvalidTicket);
        viewModel.feemasterAvailable().observe(this, this::onFeemasterAvailable);
        viewModel.sig().observe(this, sigData -> toolbarView.onSigData(sigData, this));
        viewModel.tickerUpdate().observe(this, this::onTickerUpdate);

        ticketRange = null;
    }

    private void onError(ErrorEnvelope errorEnvelope)
    {
        Timber.tag(TAG).d(errorEnvelope.message);
    }

    private void checkContractNetwork(String contractAddress)
    {
        //check for currency link - currently only xDAI
        MagicLinkData data = viewModel.getSalesOrder();
        if (data.chainId > 0)
        {
            viewModel.switchNetwork(data.chainId);
            viewModel.loadToken();
        }
        else
        {
            //Legacy support
            switch (data.contractType)
            {
                case currencyLink:
                    //for currency drop link, check xDai first, then other networks
                    viewModel.switchNetwork(GNOSIS_ID);
                    viewModel.checkTokenNetwork(contractAddress, "requiredPrefix");
                    break;
                default:
                    viewModel.checkTokenNetwork(contractAddress, "name");
                    break;
            }
        }
    }

    private void onNetwork(NetworkInfo networkInfo)
    {
        TextView networkText = findViewById(R.id.textNetworkName);
        networkText.setText(networkInfo.name);
    }

    private void onInvalidTicket(Boolean aBoolean)
    {
        TextView tv = findViewById(R.id.text_ticket_range);
        tv.setVisibility(View.GONE);
        importTxt.setVisibility(View.GONE);
        setTicket(false, false, true);
        //bad link
        hideDialog();
        aDialog = new AWalletAlertDialog(this);
        aDialog.setIcon(AWalletAlertDialog.ERROR);
        aDialog.setTitle(R.string.ticket_not_valid);
        aDialog.setMessage(R.string.ticket_not_valid_body);
        aDialog.setButtonText(R.string.action_cancel);
        aDialog.setButtonListener(v -> aDialog.dismiss());
        aDialog.show();
    }

    private void onBadLink(Boolean aBoolean)
    {
        TextView tv = findViewById(R.id.text_ticket_range);
        tv.setVisibility(View.GONE);
        importTxt.setVisibility(View.GONE);
        setTicket(false, false, true);
        //bad link
        hideDialog();
        aDialog = new AWalletAlertDialog(this);
        aDialog.setIcon(AWalletAlertDialog.ERROR);
        aDialog.setTitle(R.string.bad_import_link);
        aDialog.setMessage(R.string.bad_import_link_body);
        aDialog.setButtonText(R.string.action_cancel);
        aDialog.setButtonListener(v -> aDialog.dismiss());
        aDialog.show();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        hideDialog();
        viewModel.resetSignDialog();
    }

    //TODO: Use Activity Launcher model (eg see tokenManagementLauncher in WalletFragment)
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            new HomeRouter().open(this, true);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void handleBackPressed()
    {
        setResult(RESULT_CANCELED);
        new HomeRouter().open(this, true);
        finish();
    }

    private void setTicket(boolean ticket, boolean progress, boolean invalid)
    {
        LinearLayout progress_ticket = findViewById(R.id.layout_select_overlay);
        RelativeLayout valid_ticket = findViewById(R.id.layout_select_ticket);
        LinearLayout invalid_ticket = findViewById(R.id.layout_select_invalid);
        if (ticket)
        {
            valid_ticket.setVisibility(View.VISIBLE);
            costLayout.setVisibility(View.VISIBLE);
        }
        else
        {
            valid_ticket.setVisibility(View.GONE);
        }

        if (progress) {
            progress_ticket.setVisibility(View.VISIBLE);
        }
        else
        {
            progress_ticket.setVisibility(View.GONE);
        }

        if (invalid) {
            invalid_ticket.setVisibility(View.VISIBLE);
        }
        else
        {
            invalid_ticket.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        viewModel.prepare(importString);
    }

    private void onTickerUpdate(TokenTicker ticker)
    {
        MagicLinkData order = viewModel.getSalesOrder();
        double conversionRate = Double.valueOf(ticker.price);
        String currencyStr = TickerService.getCurrencyString(order.price * conversionRate);
        priceUSD.setText(currencyStr);
        priceUSD.setVisibility(View.VISIBLE);
        priceUSDLabel.setText(TickerService.getCurrencySymbolTxt());
        priceUSDLabel.setVisibility(View.VISIBLE);
    }

    private void onImportRange(TicketRange importTokens)
    {
        setTicket(true, false, false);
        usingFeeMaster = false;

        //now update the import token
        ticketRange = importTokens;
        MagicLinkData order = viewModel.getSalesOrder();

        //get current symbol
        String networkSymbol = viewModel.network().getValue().symbol;
        String ethPrice = getEthString(order.price) + " " + networkSymbol;

        updateImportPageForFunction();

        if (order.price == 0)
        {
            if (paymasterUrlPrefix != null)
            {
                viewModel.checkFeemaster(paymasterUrlPrefix);
                priceETH.setText(R.string.check_feemaster);
                return;
            }
            else
            {
                priceETH.setText(R.string.free_import_with_gas);
                displayImportAction();
            }

            priceETH.setVisibility(View.VISIBLE);
        }
        else
        {
            priceETH.setText(ethPrice);
            priceETH.setVisibility(View.VISIBLE);
            Button importTickets = findViewById(R.id.import_ticket);
            importTickets.setText(R.string.action_purchase);
            displayImportAction();
        }
    }

    private void updateImportPageForFunction()
    {
        MagicLinkData data = viewModel.getSalesOrder();
        switch (data.contractType)
        {
            case spawnable:
                importTxt.setText(R.string.ticket_import_valid);
                setTitle(getString(R.string.import_spawnable));
                break;
            case currencyLink:
                importTxt.setText(R.string.currency_drop);
                setTitle(getString(R.string.currency_import));
                setTicket(false, false, false); //switch off all ticket info
                //show currency to import
                LinearLayout currencyCard = findViewById(R.id.layout_currency_import);
                currencyCard.setVisibility(View.VISIBLE);
                TextView currency = findViewById(R.id.text_currency_message);
                BigDecimal ethValue = Convert.fromWei(Convert.toWei(new BigDecimal(data.amount), Convert.Unit.SZABO), Convert.Unit.ETHER);
                String networkSymbol = viewModel.network().getValue().symbol;
                String message = getString(R.string.you_will_receive) + " " + ethValue.toPlainString() + " " + networkSymbol;
                currency.setText(message);
                findViewById(R.id.text_total_cost).setVisibility(View.GONE);
                break;
            default:
                importTxt.setText(R.string.ticket_import_valid);
                break;
        }

        viewModel.checkTokenScriptSignature(data.chainId, data.contractAddress);
    }

    private void displayImportAction()
    {
        Token token = viewModel.getImportToken();
        Button importTickets = findViewById(R.id.import_ticket);
        importTickets.setVisibility(View.VISIBLE);
        importTickets.setAlpha(1.0f);
        MagicLinkData data = viewModel.getSalesOrder();
        if (token != null)
        {
            tokenView.setChainId(token.tokenInfo.chainId);
        }

        switch (data.contractType)
        {
            case spawnable:
                importTickets.setText(R.string.action_import);
                if (token != null) 
                    tokenView.displayTicketHolder(token, ticketRange, viewModel.getAssetDefinitionService());
                break;
            case currencyLink:
                importTickets.setText(R.string.action_import);
                break;
            default:
                importTxt.setText(R.string.ticket_import_valid);
                if (token != null)
                    tokenView.displayTicketHolder(token, ticketRange, viewModel.getAssetDefinitionService());
                break;
        }

        if (tokenView != null)
        {
            tokenView.setOnReadyCallback(this);
        }
    }

    private void onFeemasterAvailable(Boolean available)
    {
        priceETH.setVisibility(View.VISIBLE);
        usingFeeMaster = available;
        if (available)
        {
            priceETH.setText(R.string.free_import);
            displayImportAction();
        }
        else
        {
            switch (viewModel.getSalesOrder().contractType)
            {
                case currencyLink:
                    priceUSD.setVisibility(View.GONE);
                    priceUSDLabel.setVisibility(View.GONE);
                    importTxt.setVisibility(View.GONE);
                    findViewById(R.id.text_total_cost).setVisibility(View.GONE);
                    priceETH.setText(R.string.feemaster_service_not_available);
                    break;
                default:
                    priceETH.setText(R.string.free_import_with_gas);
                    priceUSD.setVisibility(View.GONE);
                    priceUSDLabel.setVisibility(View.GONE);
                    displayImportAction();
            }
        }
    }

    private void invalidTime(Integer integer)
    {
        MagicLinkData order = viewModel.getSalesOrder();
        importTxt.setText(R.string.ticket_range_expired);

        setTicket(false, false, true);
        Token t = viewModel.getImportToken();
        TextView tv = findViewById(R.id.text_ticket_range);
        String importText = order.ticketCount + "x ";
        importText += t.getTokenName(viewModel.getAssetDefinitionService(), order.ticketCount);

        tv.setText(importText);
    }

    private void invalidTicket(int count)
    {
        MagicLinkData order = viewModel.getSalesOrder();
        if (count == 0)
        {
            importTxt.setText(R.string.ticket_already_imported);
        }
        else
        {
            importTxt.setText(R.string.ticket_range_inavlid);
        }

        setTicket(false, false, true);
        Token t = viewModel.getImportToken();
        TextView tv = findViewById(R.id.text_ticket_range);
        String importText = order.ticketCount + "x ";
        importText += t.getTokenName(viewModel.getAssetDefinitionService(), order.ticketCount);
        tv.setText(importText);
        //Note: it's actually not possible to pull the event or anything like that since we can't get the tokenID if it's been imported.
    }

    private void onProgress(boolean shouldShowProgress) {
        hideDialog();
        if (shouldShowProgress) {
            aDialog = new AWalletAlertDialog(this);
            aDialog.setTitle(R.string.title_dialog_sending);
            aDialog.setProgressMode();
            aDialog.setCancelable(false);
            aDialog.show();
        }
    }

    private void hideDialog() {
        if (aDialog != null && aDialog.isShowing()) {
            aDialog.dismiss();
        }

        if (cDialog != null && cDialog.isShowing()) {
            cDialog.dismiss();
        }
    }

    private void confirmPurchaseDialog() {
        hideDialog();
        String currencySymbol = viewModel.getNetwork().symbol;
        MagicLinkData order = viewModel.getSalesOrder();
        cDialog = new AWalletConfirmationDialog(this);
        cDialog.setTitle(R.string.confirm_purchase);
        String ticketLabel = order.ticketCount > 1 ? getString(R.string.tickets) : getString(R.string.ticket);
        cDialog.setSmallText(getString(R.string.total_cost_for_x_tickets, order.ticketCount, ticketLabel));
        cDialog.setMediumText(getString(R.string.total_cost, getEthString(order.price), currencySymbol));
        cDialog.setPrimaryButtonText(R.string.confirm_purchase_button_text);
        cDialog.setSecondaryButtonText(R.string.dialog_cancel_back);
        cDialog.setPrimaryButtonListener(v -> {
            viewModel.getAuthorisation(this, this);
            cDialog.dismiss();
        });
        cDialog.setSecondaryButtonText(R.string.dialog_cancel_back);
        cDialog.setSecondaryButtonListener(v -> cDialog.dismiss());
        cDialog.show();
    }

    private void onTransaction(String hash) {
        onProgress(false);
        hideDialog();
        aDialog = new AWalletAlertDialog(this);
        aDialog.setTitle(R.string.transaction_succeeded);
        aDialog.setMessage(hash);
        aDialog.setButtonText(R.string.copy);
        aDialog.setButtonListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("transaction hash",
                    viewModel.getNetwork().getEtherscanUri(hash).toString());
            clipboard.setPrimaryClip(clip);
            aDialog.dismiss();
            new HomeRouter().open(this, true);
            finish();
        });
        aDialog.setOnCancelListener(v -> {
            new HomeRouter().open(this, true);
            finish();
        });
        aDialog.show();
    }

    private void onTxError(ErrorEnvelope error) {
        hideDialog();
        aDialog = new AWalletAlertDialog(this);
        aDialog.setIcon(AWalletAlertDialog.ERROR);
        aDialog.setTitle(R.string.error_import_failed);
        aDialog.setMessage(error.message);
        aDialog.setCancelable(true);
        aDialog.setButtonText(R.string.button_ok);
        aDialog.setButtonListener(v -> {
            aDialog.dismiss();
        });
        aDialog.show();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        viewModel.onDestroy();
    }

    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.import_ticket)
        {
            if (ticketRange != null)
            {
                if (viewModel.getSalesOrder().price > 0.0)
                {
                    confirmPurchaseDialog();
                }
                else
                {
                    onProgress(true);
                    completeImport();
                }
            }
            else if (viewModel.getSalesOrder().contractType == currencyLink)
            {
                onProgress(true);
                completeCurrencyImport();
            }
        }
        else if (v.getId() == R.id.cancel_button)
        {
            new HomeRouter().open(this, true);
            finish();
        }
    }

    private void completeImport()
    {
        if (paymasterUrlPrefix != null && usingFeeMaster)
        {
            viewModel.importThroughFeemaster(paymasterUrlPrefix);
        }
        else
        {
            viewModel.getAuthorisation(this, this);
        }
    }

    private void completeCurrencyImport()
    {
        //attempt to import through the server
        if (usingFeeMaster)
        {
            viewModel.importThroughFeemaster(paymasterUrlPrefix);
        }
        else
        {
            viewModel.performImport();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode,resultCode,intent);

        if (requestCode >= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS && requestCode <= SignTransactionDialog.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + 10)
        {
            gotAuthorisation(resultCode == RESULT_OK);
        }
    }

    /**
     * Return from key authorisation either through activity result (for PIN) or directly through the callback from fingerprint unlock
     * @param gotAuth authorisation was successful
     */
    @Override
    public void gotAuthorisation(boolean gotAuth)
    {
        if (gotAuth) viewModel.completeAuthentication(SIGN_DATA);
        else viewModel.failedAuthentication(SIGN_DATA);
        viewModel.performImport();
        onProgress(true);
    }

    @Override
    public void cancelAuthentication()
    {
        AWalletAlertDialog dialog = new AWalletAlertDialog(this);
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

    @Override
    public void onPageLoaded(WebView view)
    {
        webWrapper.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPageRendered(WebView view)
    {
        webWrapper.setVisibility(View.VISIBLE);
    }
}
