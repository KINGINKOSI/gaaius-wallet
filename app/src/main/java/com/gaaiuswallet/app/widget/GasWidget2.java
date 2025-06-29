package com.gaaiuswallet.app.widget;

import static com.gaaiuswallet.ethereum.EthereumNetworkBase.MAINNET_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.C;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.ActionSheetInterface;
import com.gaaiuswallet.app.entity.GasPriceSpread;
import com.gaaiuswallet.app.entity.TXSpeed;
import com.gaaiuswallet.app.entity.analytics.ActionSheetMode;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.repository.TokensRealmSource;
import com.gaaiuswallet.app.repository.entity.Realm1559Gas;
import com.gaaiuswallet.app.repository.entity.RealmGasSpread;
import com.gaaiuswallet.app.repository.entity.RealmTokenTicker;
import com.gaaiuswallet.app.service.GasService;
import com.gaaiuswallet.app.service.TickerService;
import com.gaaiuswallet.app.service.TokensService;
import com.gaaiuswallet.app.ui.GasSettingsActivity;
import com.gaaiuswallet.app.ui.widget.entity.GasSpeed;
import com.gaaiuswallet.app.ui.widget.entity.GasWidgetInterface;
import com.gaaiuswallet.app.util.BalanceUtils;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.web3.entity.Web3Transaction;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.realm.Realm;
import io.realm.RealmQuery;

/**
 * Created by JB on 20/01/2022.
 */
public class GasWidget2 extends LinearLayout implements Runnable, GasWidgetInterface
{
    private GasPriceSpread gasSpread;
    private Realm1559Gas realmGasSpread;
    private TokensService tokensService;
    private BigInteger customGasLimit;    //from slider
    private BigInteger presetGasLimit;    //this is the gas limit used for the presets. It will use, in order of priority: gas estimate from node, gas from dapp tx, calculated gas
    private BigInteger transactionValue;  //'value' base token amount from dapp transaction
    private BigInteger adjustedValue;     //adjusted value, in case we are use 'all funds' to wipe an account.
    private BigInteger initialGasPrice;   //gasprice from dapp transaction
    private Token token;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView speedText;
    private final TextView timeEstimate;
    private final LinearLayout gasWarning;
    private final LinearLayout speedWarning;
    private final Context context;

    private TXSpeed currentGasSpeedIndex = TXSpeed.STANDARD;
    private long customNonce = -1;
    private BigInteger resendGasPrice = BigInteger.ZERO;
    private long gasEstimateTime = 0;
    private ActionSheetInterface actionSheetInterface;

    //Need to track user selected gas limit & calculated gas limit
    //At initial setup, we have the limit from the tx or default: presetGasLimit
    //Then we receive the limit from the dry run: presetGasLimit
    //Then, we may have user selected limit (or may not) : customGasLimit

    public GasWidget2(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
        inflate(ctx, R.layout.item_gas_settings, this);

        context = ctx;
        speedText = findViewById(R.id.text_speed);
        timeEstimate = findViewById(R.id.text_time_estimate);
        gasWarning = findViewById(R.id.layout_gas_warning);
        speedWarning = findViewById(R.id.layout_speed_warning);
    }

    // Called once from ActionSheet constructor
    public void setupWidget(TokensService svs, Token t, Web3Transaction tx, ActionSheetInterface actionSheetIf)
    {
        tokensService = svs;
        token = t;
        transactionValue = tx.value;
        adjustedValue = tx.value;
        initialGasPrice = tx.gasPrice;
        customNonce = tx.nonce;
        actionSheetInterface = actionSheetIf;

        if (tx.gasLimit.equals(BigInteger.ZERO)) //dapp didn't specify a limit, use default limits until node returns an estimate (see setGasEstimate())
        {
            presetGasLimit = GasService.getDefaultGasLimit(token, tx);
        }
        else
        {
            presetGasLimit = tx.gasLimit;
        }

        customGasLimit = presetGasLimit;

        setupGasSpeeds(tx);
        startGasListener();

        if (!tokensService.hasLockedGas(token.tokenInfo.chainId))
        {
            findViewById(R.id.edit_text).setVisibility(View.VISIBLE);
            setOnClickListener(v -> {
                Token baseEth = tokensService.getToken(token.tokenInfo.chainId, token.getWallet());
                Intent intent = new Intent(context, GasSettingsActivity.class);
                intent.putExtra(C.EXTRA_SINGLE_ITEM, currentGasSpeedIndex.ordinal());
                intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId);
                intent.putExtra(C.EXTRA_CUSTOM_GAS_LIMIT, customGasLimit.toString());
                intent.putExtra(C.EXTRA_GAS_LIMIT_PRESET, presetGasLimit.toString());
                intent.putExtra(C.EXTRA_TOKEN_BALANCE, baseEth.balance.toString());
                intent.putExtra(C.EXTRA_AMOUNT, transactionValue.toString());
                intent.putExtra(C.EXTRA_GAS_PRICE, gasSpread);  //Parcelised
                intent.putExtra(C.EXTRA_NONCE, customNonce);
                intent.putExtra(C.EXTRA_1559_TX, true);
                intent.putExtra(C.EXTRA_MIN_GAS_PRICE, resendGasPrice.longValue());
                actionSheetInterface.gasSelectLauncher().launch(intent);
            });
        }
    }

    //set custom fee if specified by tx feed
    private void setupGasSpeeds(Web3Transaction w3tx)
    {
        try (Realm realm = tokensService.getTickerRealmInstance())
        {
            Realm1559Gas gasReturn = realm.where(Realm1559Gas.class)
                .equalTo("chainId", token.tokenInfo.chainId).findFirst();

            if (gasReturn != null)
            {
                initGasSpeeds(gasReturn);
            }
            else
            {
                // Couldn't get current gas. Add a blank custom gas speed node
                gasSpread = new GasPriceSpread(getContext(), w3tx.maxFeePerGas, w3tx.maxPriorityFeePerGas);
            }
        }

        if (w3tx.maxFeePerGas.compareTo(BigInteger.ZERO) > 0 && w3tx.maxPriorityFeePerGas.compareTo(BigInteger.ZERO) > 0)
        {
            gasSpread.setCustom(w3tx.maxFeePerGas, w3tx.maxPriorityFeePerGas, GasPriceSpread.FAST_SECONDS);
        }
    }

    @Override
    public void onDestroy()
    {
        if (realmGasSpread != null) realmGasSpread.removeAllChangeListeners();
    }

    /**
     * This function is the leaf for when the user clicks on a gas setting; fast, slow, custom, etc
     *
     * @param gasSelectionIndex
     * @param maxFeePerGas
     * @param maxPriorityFee
     * @param custGasLimit
     * @param expectedTxTime
     * @param nonce
     */
    public void setCurrentGasIndex(int gasSelectionIndex, BigInteger maxFeePerGas, BigInteger maxPriorityFee, BigDecimal custGasLimit, long expectedTxTime, long nonce)
    {
        if (gasSelectionIndex < TXSpeed.values().length)
        {
            currentGasSpeedIndex = TXSpeed.values()[gasSelectionIndex];
        }

        customNonce = nonce;
        customGasLimit = custGasLimit.toBigInteger();

        if (maxFeePerGas.compareTo(BigInteger.ZERO) > 0 && maxPriorityFee.compareTo(BigInteger.ZERO) > 0)
        {
            gasSpread.setCustom(maxFeePerGas, maxPriorityFee, expectedTxTime);
        }

        tokensService.track(currentGasSpeedIndex.name());
        handler.post(this);
    }

    public boolean checkSufficientGas()
    {
        BigInteger useGasLimit = getUseGasLimit();
        boolean sufficientGas = true;
        GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);

        //Calculate total network fee here:
        BigDecimal networkFee = new BigDecimal(gs.gasPrice.maxFeePerGas.multiply(useGasLimit));
        Token base = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet());

        if (token.isEthereum() && token.balance.subtract(new BigDecimal(transactionValue).add(networkFee)).compareTo(BigDecimal.ZERO) < 0)
        {
            sufficientGas = false;
        }
        else if (!token.isEthereum() && base.balance.subtract(networkFee).compareTo(BigDecimal.ZERO) < 0)
        {
            sufficientGas = false;
        }

        if (!sufficientGas)
        {
            gasWarning.setVisibility(View.VISIBLE);
        }
        else
        {
            gasWarning.setVisibility(View.GONE);
        }

        return sufficientGas;
    }

    private BigInteger getLegacyGasPrice()
    {
        BigInteger legacyPrice = initialGasPrice;
        try(Realm realm = tokensService.getTickerRealmInstance())
        {
            RealmGasSpread rgs = realm.where(RealmGasSpread.class)
                .equalTo("chainId", token.tokenInfo.chainId).findFirst();

            if (rgs != null)
            {
                legacyPrice = rgs.getGasFee(TXSpeed.STANDARD);
            }
        }
        catch (Exception e)
        {
            //
        }

        return legacyPrice;
    }

    private BigInteger getUseGasLimit()
    {
        if (currentGasSpeedIndex == TXSpeed.CUSTOM)
        {
            return customGasLimit;
        }
        else
        {
            return presetGasLimit;
        }
    }

    private RealmQuery<Realm1559Gas> getGasQuery2()
    {
        return tokensService.getTickerRealmInstance().where(Realm1559Gas.class)
                .equalTo("chainId", token.tokenInfo.chainId);
    }

    private void startGasListener()
    {
        if (realmGasSpread != null) realmGasSpread.removeAllChangeListeners();
        realmGasSpread = getGasQuery2().findFirstAsync();
        realmGasSpread.addChangeListener(realmSpread -> {
            if (realmGasSpread.isValid())
            {
                initGasSpeeds((Realm1559Gas) realmSpread);
            }
        });
    }

    private void initGasSpeeds(Realm1559Gas gs)
    {
        try
        {
            GasSpeed custom = getCustomGasSpeed();
            gasSpread = new GasPriceSpread(getContext(), gs.getResult());
            gasSpread.setCustom(custom);
            gasEstimateTime = gs.getTimeStamp();

            //if we have mainnet then show timings, otherwise no timing, if the token has fiat value, show fiat value of gas, so we need the ticker
            handler.post(this);
        }
        catch (Exception e)
        {
            currentGasSpeedIndex = TXSpeed.STANDARD;
            if (BuildConfig.DEBUG) e.printStackTrace();
        }
    }

    /**
     * Update the UI with the gas value and expected transaction time (if main net).
     * Note - there is no ticker listener - it's unlikely any ticker change would produce a noticeable change in the gas price
     */
    @Override
    public void run()
    {
        GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);
        if (gs == null) return;

        Token baseCurrency = tokensService.getTokenOrBase(token.tokenInfo.chainId, token.getWallet());
        BigInteger networkFee = (gs.gasPrice.baseFee.add(gs.gasPrice.priorityFee)).multiply(getUseGasLimit());
        String gasAmountInBase = BalanceUtils.getSlidingBaseValue(new BigDecimal(networkFee), baseCurrency.tokenInfo.decimals, GasSettingsActivity.GAS_PRECISION);
        if (gasAmountInBase.equals("0")) gasAmountInBase = "0.0001";
        String displayStr = context.getString(R.string.gas_amount, gasAmountInBase, baseCurrency.getSymbol());

        //Can we display value for gas?
        try (Realm realm = tokensService.getTickerRealmInstance())
        {
            RealmTokenTicker rtt = realm.where(RealmTokenTicker.class)
                    .equalTo("contract", TokensRealmSource.databaseKey(token.tokenInfo.chainId, "eth"))
                    .findFirst();

            if (rtt != null)
            {
                //calculate equivalent fiat
                double cryptoRate = Double.parseDouble(rtt.getPrice());
                double cryptoAmount = BalanceUtils.weiToEth(new BigDecimal(networkFee)).doubleValue();
                displayStr += context.getString(R.string.gas_fiat_suffix,
                        TickerService.getCurrencyString(cryptoAmount * cryptoRate),
                        rtt.getCurrencySymbol());

                if (token.tokenInfo.chainId == MAINNET_ID && gs.seconds > 0)
                {
                    displayStr += context.getString(R.string.gas_time_suffix,
                            Utils.shortConvertTimePeriodInSeconds(gs.seconds, context));
                }
            }
        }
        catch (Exception e)
        {
            //
        }
        timeEstimate.setText(displayStr);
        speedText.setText(gs.speed);

        if (currentGasSpeedIndex == TXSpeed.CUSTOM)
        {
            checkCustomGasPrice(gasSpread.getSelectedGasFee(TXSpeed.CUSTOM).gasPrice.maxFeePerGas);
        }
        else
        {
            speedWarning.setVisibility(View.GONE);
        }
        checkSufficientGas();
        manageWarnings();

        if (gasPriceReady(gasEstimateTime))
        {
            actionSheetInterface.gasEstimateReady();
            setGasReadyStatus(true);
        }
        else
        {
            setGasReadyStatus(false);
        }
    }

    private void setGasReadyStatus(boolean ready)
    {
        findViewById(R.id.view_spacer).setVisibility(ready ? View.VISIBLE : View.GONE);
        findViewById(R.id.gas_fetch_wait).setVisibility(ready ? View.GONE : View.VISIBLE);
    }

    @Override
    public BigInteger getGasPrice()
    {
        BigInteger gasPrice = getLegacyGasPrice();
        BigDecimal networkFee = new BigDecimal(gasPrice.multiply(getUseGasLimit()));
        adjustedValue = token.balance.subtract(networkFee).toBigInteger();
        if (adjustedValue.compareTo(BigInteger.ZERO) < 0) // insufficient gas available
        {
            adjustedValue = BigInteger.ZERO;
        }

        return gasPrice;
    }

    @Override
    public BigInteger getGasPrice(BigInteger defaultPrice)
    {
        if (gasSpread != null && gasSpread.getSelectedGasFee(currentGasSpeedIndex) != null)
        {
            GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);
            return gs.gasPrice.maxFeePerGas;
        }
        else
        {
            return defaultPrice;
        }
    }

    @Override
    public BigInteger getGasMax()
    {
        GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);
        return gs.gasPrice.maxFeePerGas;
    }

    @Override
    public BigInteger getPriorityFee()
    {
        GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);
        return gs.gasPrice.priorityFee;
    }

    @Override
    public boolean isSendingAll(Web3Transaction tx)
    {
        return false;
    }

    @Override
    public boolean gasPriceReady()
    {
        return gasPriceReady(gasEstimateTime);
    }

    @Override
    public BigInteger getValue()
    {
        return transactionValue;
    }

    private void checkCustomGasPrice(BigInteger customGasPrice)
    {
        double dGasPrice = customGasPrice.doubleValue();
        GasSpeed ug = gasSpread.getQuickestGasSpeed();
        GasSpeed lg = gasSpread.getSlowestGasSpeed();

        if (resendGasPrice.compareTo(BigInteger.ZERO) > 0)
        {
            if (dGasPrice > (3.0 * resendGasPrice.doubleValue()))
            {
                showCustomSpeedWarning(true);
            }
            else
            {
                speedWarning.setVisibility(View.GONE);
            }
        }
        else if (dGasPrice < lg.gasPrice.maxFeePerGas.doubleValue())
        {
            showCustomSpeedWarning(false);
        }
        else if (dGasPrice > 2.0 * ug.gasPrice.maxFeePerGas.doubleValue())
        {
            showCustomSpeedWarning(true);
        }
        else
        {
            speedWarning.setVisibility(View.GONE);
        }
    }

    public void setupResendSettings(ActionSheetMode mode, BigInteger minGas)
    {
        resendGasPrice = minGas;
        TextView speedupNote = findViewById(R.id.text_speedup_note);
        //If user wishes to cancel transaction, otherwise default is speed it up.
        if (mode == ActionSheetMode.CANCEL_TRANSACTION)
        {
            speedupNote.setText(R.string.text_cancel_note);
        }
        else
        {
            speedupNote.setText(R.string.text_speedup_note);
        }
        speedupNote.setVisibility(View.VISIBLE);
    }

    private void showCustomSpeedWarning(boolean high)
    {
        TextView warningText = findViewById(R.id.text_speed_warning);

        if (high)
        {
            warningText.setText(getResources().getString(R.string.speed_high_gas));
        }
        else
        {
            warningText.setText(getResources().getString(R.string.speed_too_low));
        }
        speedWarning.setVisibility(View.VISIBLE);
    }

    private void manageWarnings()
    {
        if (gasWarning.getVisibility() == VISIBLE || speedWarning.getVisibility() == VISIBLE)
        {
            speedText.setVisibility(View.GONE);
            if (gasWarning.getVisibility() == VISIBLE && speedWarning.getVisibility() == VISIBLE)
            {
                speedWarning.setVisibility(View.GONE);
            }
        }
        else
        {
            speedText.setVisibility(View.VISIBLE);
        }
    }

    public BigInteger getGasLimit()
    {
        return getUseGasLimit();
    }

    public long getNonce()
    {
        if (currentGasSpeedIndex == TXSpeed.CUSTOM)
        {
            return customNonce;
        }
        else
        {
            return -1;
        }
    }

    public long getExpectedTransactionTime()
    {
        GasSpeed gs = gasSpread.getSelectedGasFee(currentGasSpeedIndex);
        return gs.seconds;
    }

    /**
     * Node eth_gasEstimate returned a transaction estimate
     *
     * @param estimate
     */
    public void setGasEstimate(BigInteger estimate)
    {
        if (!customGasLimit.equals(estimate) && estimate.longValue() > C.GAS_LIMIT_MIN) //some kind of contract interaction
        {
            estimate = estimate.multiply(BigInteger.valueOf(6)).divide(BigInteger.valueOf(5)); // increase estimate by 20% to be safe
        }

        setGasEstimateExact(estimate);
    }

    @Override
    public void setGasEstimateExact(BigInteger estimate)
    {
        if (customGasLimit.equals(presetGasLimit))
        {
            customGasLimit = estimate;
        }

        //presets always use estimate if available
        presetGasLimit = estimate;

        //now update speeds
        handler.post(this);
    }

    private GasSpeed getCustomGasSpeed()
    {
        if (gasSpread != null)
        {
            return gasSpread.hasCustom() ? gasSpread.getCustom() : null;
        }
        else
        {
            return null;
        }
    }
}

