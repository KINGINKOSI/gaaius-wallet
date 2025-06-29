package com.gaaiuswallet.app.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.gaaiuswallet.app.util.BalanceUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Created by JB on 20/01/2022.
 */
public class EIP1559FeeOracleResult implements Parcelable
{
    public final BigInteger maxFeePerGas;
    public final BigInteger priorityFee;
    public final BigInteger baseFee;

    public EIP1559FeeOracleResult(BigInteger maxFee, BigInteger maxPriority, BigInteger base)
    {
        maxFeePerGas = fixGasPriceReturn(maxFee);  // Some chains (eg Phi) have a gas price lower than 1Gwei.
        priorityFee = fixGasPriceReturn(maxPriority);
        baseFee = base;
    }

    public EIP1559FeeOracleResult(EIP1559FeeOracleResult r)
    {
        maxFeePerGas = r.maxFeePerGas;
        priorityFee = r.priorityFee;
        baseFee = r.baseFee;
    }

    protected EIP1559FeeOracleResult(Parcel in)
    {
        maxFeePerGas = new BigInteger(in.readString(), 16);
        priorityFee = new BigInteger(in.readString(), 16);
        baseFee = new BigInteger(in.readString(), 16);
    }

    public static final Creator<EIP1559FeeOracleResult> CREATOR = new Creator<EIP1559FeeOracleResult>() {
        @Override
        public EIP1559FeeOracleResult createFromParcel(Parcel in) {
            return new EIP1559FeeOracleResult(in);
        }

        @Override
        public EIP1559FeeOracleResult[] newArray(int size) {
            return new EIP1559FeeOracleResult[size];
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
        dest.writeString(maxFeePerGas.toString(16));
        dest.writeString(priorityFee.toString(16));
        dest.writeString(baseFee.toString(16));
    }

    // Returns minimum 1 Gwei
    private BigInteger minOneGwei(BigInteger input)
    {
        return input.max(BalanceUtils.gweiToWei(BigDecimal.ONE));
    }

    //returns 1 gwei if null
    private BigInteger fixGasPriceReturn(BigInteger input)
    {
        if (input == null)
        {
            return BalanceUtils.gweiToWei(BigDecimal.ONE);
        }
        else
        {
            return input;
        }
    }
}
