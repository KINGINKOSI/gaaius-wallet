package com.gaaiuswallet.app.entity;

import static com.gaaiuswallet.app.util.BalanceUtils.gweiToWei;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.ui.widget.entity.GasSpeed;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import timber.log.Timber;

/**
 * Created by JB on 20/01/2022.
 */
public class GasPriceSpread implements Parcelable
{
    public static long RAPID_SECONDS = 15;
    public static long FAST_SECONDS = 60;
    public static long STANDARD_SECONDS = 60 * 3;
    public static long SLOW_SECONDS = 60 * 10;

    private final boolean hasLockedGas;
    private BigInteger baseFee = BigInteger.ZERO;

    public GasSpeed getSelectedGasFee(TXSpeed currentGasSpeedIndex)
    {
        return fees.get(currentGasSpeedIndex);
    }

    public int getEntrySize()
    {
        return fees.size();
    }

    public GasSpeed getQuickestGasSpeed()
    {
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (gs != null) return gs;
        }

        //Should not reach this
        return null;
    }

    public GasSpeed getSlowestGasSpeed()
    {
        TXSpeed slowest = TXSpeed.STANDARD;
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (gs != null)
            {
                if (gs.gasPrice.maxFeePerGas.compareTo(fees.get(slowest).gasPrice.maxFeePerGas) < 0)
                {
                    slowest = txs;
                }
            }
        }

        return fees.get(slowest);
    }

    public TXSpeed getNextSpeed(TXSpeed speed)
    {
        boolean begin = false;
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (gs != null)
            {
                if (txs == speed)
                {
                    begin = true;
                }
                else if (begin)
                {
                    return txs;
                }
            }
        }

        return TXSpeed.CUSTOM;
    }

    public TXSpeed getSelectedPosition(int absoluteAdapterPosition)
    {
        int index = 0;
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (gs == null) continue;
            else if (absoluteAdapterPosition == index) return txs;
            index++;
        }

        return TXSpeed.CUSTOM;
    }

    public final long timeStamp;
    public TXSpeed speedIndex = TXSpeed.STANDARD;

    private final Map<TXSpeed, GasSpeed> fees = new HashMap<>();

    public GasPriceSpread(Context ctx, Map<Integer, EIP1559FeeOracleResult> result)
    {
        hasLockedGas = false;
        timeStamp = System.currentTimeMillis();
        if (result == null || result.size() == 0) return;
        setComponents(ctx, result);

        fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, fees.get(TXSpeed.STANDARD).gasPrice));
    }

    public GasPriceSpread(Context ctx, GasPriceSpread gs, Map<Integer, EIP1559FeeOracleResult> result)
    {
        hasLockedGas = false;
        timeStamp = System.currentTimeMillis();
        if (result == null || result.size() == 0) return;
        setComponents(ctx, result);

        GasSpeed custom = gs.getSelectedGasFee(TXSpeed.CUSTOM);

        fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), custom.seconds, custom.gasPrice));
    }

    //This is a fallback method, it should never be used
    public GasPriceSpread(Context ctx, BigInteger maxFeePerGas, BigInteger maxPriorityFeePerGas)
    {
        timeStamp = System.currentTimeMillis();

        BigInteger baseFeeApprox = maxFeePerGas.subtract(maxPriorityFeePerGas.divide(BigInteger.valueOf(2)));

        fees.put(TXSpeed.STANDARD, new GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, new EIP1559FeeOracleResult(maxFeePerGas, maxPriorityFeePerGas, baseFeeApprox)));
        fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, new EIP1559FeeOracleResult(maxFeePerGas, maxPriorityFeePerGas, baseFeeApprox)));
        hasLockedGas = false;
    }

    public GasPriceSpread(BigInteger currentAvGasPrice, boolean lockedGas)
    {
        timeStamp = System.currentTimeMillis();

        fees.put(TXSpeed.FAST, new GasSpeed("", FAST_SECONDS, new BigDecimal(currentAvGasPrice).multiply(BigDecimal.valueOf(1.2)).toBigInteger()));
        fees.put(TXSpeed.STANDARD, new GasSpeed("", STANDARD_SECONDS, currentAvGasPrice));
        hasLockedGas = lockedGas;
    }

    public GasPriceSpread(Context ctx, BigInteger gasPrice)
    {
        timeStamp = System.currentTimeMillis();

        fees.put(TXSpeed.STANDARD, new GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, gasPrice));
        fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, gasPrice));
        hasLockedGas = false;
    }

    public GasPriceSpread(Context ctx, String apiReturn) //ChainId is unused but we need to disambiguate from etherscan API return
    {
        this.timeStamp = System.currentTimeMillis();
        BigDecimal rBaseFee = BigDecimal.ZERO;
        hasLockedGas = false;

        try
        {
            JSONObject result = new JSONObject(apiReturn);
            if (result.has("estimatedBaseFee"))
            {
                rBaseFee = new BigDecimal(result.getString("estimatedBaseFee"));
            }

            EIP1559FeeOracleResult low = readFeeResult(result, "low", rBaseFee);
            EIP1559FeeOracleResult medium = readFeeResult(result, "medium", rBaseFee);
            EIP1559FeeOracleResult high = readFeeResult(result, "high", rBaseFee);

            if (low == null || medium == null || high == null)
            {
                return;
            }

            BigInteger rapidPriorityFee = (new BigDecimal(high.priorityFee)).multiply(BigDecimal.valueOf(1.2)).toBigInteger();
            EIP1559FeeOracleResult rapid = new EIP1559FeeOracleResult(high.maxFeePerGas, rapidPriorityFee, gweiToWei(rBaseFee));

            fees.put(TXSpeed.SLOW, new GasSpeed(ctx.getString(R.string.speed_slow), SLOW_SECONDS, low));
            fees.put(TXSpeed.STANDARD, new GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, medium));
            fees.put(TXSpeed.FAST, new GasSpeed(ctx.getString(R.string.speed_fast), FAST_SECONDS, high));
            fees.put(TXSpeed.RAPID, new GasSpeed(ctx.getString(R.string.speed_rapid), RAPID_SECONDS, rapid));
        }
        catch (JSONException e)
        {
            //
        }
    }

    private EIP1559FeeOracleResult readFeeResult(JSONObject result, String speed, BigDecimal rBaseFee)
    {
        EIP1559FeeOracleResult oracleResult = null;

        try
        {
            if (result.has(speed))
            {
                JSONObject thisSpeed = result.getJSONObject(speed);
                BigDecimal maxFeePerGas = new BigDecimal(thisSpeed.getString("suggestedMaxFeePerGas"));
                BigDecimal priorityFee = new BigDecimal(thisSpeed.getString("suggestedMaxPriorityFeePerGas"));
                oracleResult = new EIP1559FeeOracleResult(gweiToWei(maxFeePerGas), gweiToWei(priorityFee), gweiToWei(rBaseFee));
            }
        }
        catch (Exception e)
        {
            Timber.e("Infura GasOracle read failing; please adjust your Infura API settings.");
        }

        return oracleResult;
    }

    // For etherscan return
    public GasPriceSpread(String apiReturn)
    {
        this.timeStamp = System.currentTimeMillis();

        BigDecimal rRapid = BigDecimal.ZERO;
        BigDecimal rFast = BigDecimal.ZERO;
        BigDecimal rStandard = BigDecimal.ZERO;
        BigDecimal rSlow = BigDecimal.ZERO;
        BigDecimal rBaseFee = BigDecimal.ZERO;

        try
        {
            JSONObject result = new JSONObject(apiReturn);
            if (result.has("result"))
            {
                JSONObject data = result.getJSONObject("result");

                rFast = new BigDecimal(data.getString("FastGasPrice"));
                rRapid = rFast.multiply(BigDecimal.valueOf(1.2));
                rStandard = new BigDecimal(data.getString("ProposeGasPrice"));
                rSlow = new BigDecimal(data.getString("SafeGasPrice"));
                rBaseFee = new BigDecimal(data.getString("suggestBaseFee"));
            }
        }
        catch (JSONException e)
        {
            //
        }

        //convert to wei
        fees.put(TXSpeed.RAPID, new GasSpeed("", RAPID_SECONDS, gweiToWei(rRapid)));
        fees.put(TXSpeed.FAST, new GasSpeed("", FAST_SECONDS, gweiToWei(rFast)));
        fees.put(TXSpeed.STANDARD, new GasSpeed("", STANDARD_SECONDS, gweiToWei(rStandard)));
        fees.put(TXSpeed.SLOW, new GasSpeed("", SLOW_SECONDS, gweiToWei(rSlow)));
        baseFee = gweiToWei(rBaseFee);

        hasLockedGas = false;
    }

    public GasPriceSpread(Context ctx, GasPriceSpread gasSpread, long timestamp, Map<TXSpeed, BigInteger> feeMap, boolean locked)
    {
        this.timeStamp = timestamp;

        addLegacyGasFee(TXSpeed.RAPID, RAPID_SECONDS, ctx.getString(R.string.speed_rapid), feeMap);
        addLegacyGasFee(TXSpeed.FAST, FAST_SECONDS, ctx.getString(R.string.speed_fast), feeMap);
        addLegacyGasFee(TXSpeed.STANDARD, STANDARD_SECONDS, ctx.getString(R.string.speed_average), feeMap);
        addLegacyGasFee(TXSpeed.SLOW, SLOW_SECONDS, ctx.getString(R.string.speed_slow), feeMap);

        if (gasSpread != null)
        {
            GasSpeed custom = gasSpread.getSelectedGasFee(TXSpeed.CUSTOM);

            if (custom != null)
            {
                fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), custom.seconds, custom.gasPrice.maxFeePerGas));
            }
        }
        else
        {
            fees.put(TXSpeed.CUSTOM, new GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, feeMap.get(TXSpeed.STANDARD)));
        }
        hasLockedGas = locked;
    }

    private void addLegacyGasFee(TXSpeed speed, long seconds, String speedName, Map<TXSpeed, BigInteger> feeMap)
    {
        BigInteger gasPrice = feeMap.get(speed);
        if (gasPrice != null && gasPrice.compareTo(BigInteger.ZERO) > 0)
        {
            fees.put(speed, new GasSpeed(speedName, seconds, gasPrice));
        }
    }

    private void setComponents(Context ctx, Map<Integer, EIP1559FeeOracleResult> result)
    {
        int quarter = result.size()/4;

        fees.put(TXSpeed.RAPID, new GasSpeed(ctx.getString(R.string.speed_rapid), RAPID_SECONDS, new EIP1559FeeOracleResult(result.get(0))));
        fees.put(TXSpeed.FAST, new GasSpeed(ctx.getString(R.string.speed_fast), FAST_SECONDS, new EIP1559FeeOracleResult(result.get(quarter))));
        fees.put(TXSpeed.STANDARD, new GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, new EIP1559FeeOracleResult(result.get(quarter*2))));
        fees.put(TXSpeed.SLOW, new GasSpeed(ctx.getString(R.string.speed_slow), SLOW_SECONDS, new EIP1559FeeOracleResult(result.get(result.size()-1))));

        //now de-duplicate
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (txs == TXSpeed.STANDARD || gs == null) continue;
            if (gs.gasPrice.priorityFee.equals(fees.get(TXSpeed.STANDARD).gasPrice.priorityFee)
                && gs.gasPrice.maxFeePerGas.equals(fees.get(TXSpeed.STANDARD).gasPrice.maxFeePerGas))
            {
                fees.remove(txs);
            }
        }
    }

    public void setCustom(BigInteger maxFeePerGas, BigInteger maxPriorityFeePerGas, long fastSeconds)
    {
        GasSpeed gsCustom = fees.get(TXSpeed.CUSTOM);
        BigInteger baseFee = gsCustom.gasPrice.baseFee;
        fees.put(TXSpeed.CUSTOM, new GasSpeed(gsCustom.speed, fastSeconds, new EIP1559FeeOracleResult(maxFeePerGas, maxPriorityFeePerGas, baseFee)));
    }

    public void setCustom(@Nullable GasSpeed gs)
    {
        if (gs != null)
        {
            GasSpeed rapid = fees.get(TXSpeed.RAPID);
            BigInteger baseFee = rapid != null ? rapid.gasPrice.baseFee : gs.gasPrice.baseFee;

            GasSpeed custom = new GasSpeed(gs.speed, gs.seconds, new EIP1559FeeOracleResult(gs.gasPrice.maxFeePerGas, gs.gasPrice.priorityFee, baseFee));
            fees.put(TXSpeed.CUSTOM, custom);
        }
    }

    public void setCustom(BigInteger gasPrice, long fastSeconds)
    {
        GasSpeed gsCustom = fees.get(TXSpeed.CUSTOM);
        fees.put(TXSpeed.CUSTOM, new GasSpeed(gsCustom.speed, fastSeconds, gasPrice));
    }

    protected GasPriceSpread(Parcel in)
    {
        timeStamp = in.readLong();
        int feeCount = in.readInt();
        int feeIndex = in.readInt();
        speedIndex = TXSpeed.values()[feeIndex];
        hasLockedGas = in.readByte() == 1;

        for (int i = 0; i < feeCount; i++)
        {
            int entry = in.readInt();
            GasSpeed r = in.readParcelable(GasSpeed.class.getClassLoader());
            fees.put(TXSpeed.values()[entry], r);
        }
    }

    public static final Creator<GasPriceSpread> CREATOR = new Creator<GasPriceSpread>() {
        @Override
        public GasPriceSpread createFromParcel(Parcel in) {
            return new GasPriceSpread(in);
        }

        @Override
        public GasPriceSpread[] newArray(int size) {
            return new GasPriceSpread[size];
        }
    };

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeLong(timeStamp);
        dest.writeInt(fees.size());
        dest.writeInt(speedIndex.ordinal());
        dest.writeByte(hasLockedGas ? (byte) 1 : (byte) 0);

        for (Map.Entry<TXSpeed, GasSpeed> entry : fees.entrySet())
        {
            dest.writeInt(entry.getKey().ordinal());
            dest.writeParcelable(entry.getValue(), flags);
        }
    }

    public void addCustomGas(long seconds, EIP1559FeeOracleResult fee)
    {
        GasSpeed currentCustom = fees.get(TXSpeed.CUSTOM);
        fees.put(TXSpeed.CUSTOM,
                new GasSpeed(currentCustom.speed, seconds, fee));
    }

    public EIP1559FeeOracleResult getCurrentGasFee()
    {
        return fees.get(this.speedIndex).gasPrice;
    }

    public long getCurrentTimeEstimate()
    {
        return fees.get(this.speedIndex).seconds;
    }

    public GasSpeed getGasSpeed()
    {
        return fees.get(this.speedIndex);
    }

    public boolean hasCustom()
    {
        GasSpeed custom = fees.get(TXSpeed.CUSTOM);
        return (custom != null && custom.seconds != 0);
    }

    public boolean hasLockedGas() { return hasLockedGas; }

    public BigInteger getBaseFee() { return baseFee; }

    public boolean isResultValid()
    {
        for (TXSpeed txs : TXSpeed.values())
        {
            GasSpeed gs = fees.get(txs);
            if (gs != null && gs.gasPrice.maxFeePerGas.compareTo(BigInteger.ZERO) > 0) return true;
        }

        return false;
    }

    public int findItem(TXSpeed currentGasSpeedIndex)
    {
        int index = 0;
        for (TXSpeed txs : TXSpeed.values())
        {
            if (txs == currentGasSpeedIndex) return index;
            if (fees.get(txs) != null) index++;
        }

        return 0;
    }

    public GasSpeed getCustom()
    {
        return fees.get(TXSpeed.CUSTOM);
    }
}
