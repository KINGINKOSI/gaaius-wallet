package com.gaaiuswallet.app.entity;

import android.util.Base64;

import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.token.entity.CryptoFunctionsInterface;
import com.gaaiuswallet.token.entity.ProviderTypedData;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;

import timber.log.Timber;
import wallet.core.jni.Hash;

public class CryptoFunctions implements CryptoFunctionsInterface
{
    @Override
    public byte[] Base64Decode(String message)
    {
        return Base64.decode(message, Base64.URL_SAFE);
    }

    @Override
    public byte[] Base64Encode(byte[] data)
    {
        return Base64.encode(data, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    @Override
    public BigInteger signedMessageToKey(byte[] data, byte[] signature) throws SignatureException
    {
        Sign.SignatureData sigData = sigFromByteArray(signature);
        if (sigData == null) return BigInteger.ZERO;
        return Sign.signedMessageToKey(data, sigData);
    }

    @Override
    public String getAddressFromKey(BigInteger recoveredKey)
    {
        return Keys.getAddress(recoveredKey);
    }

    @Override
    public byte[] keccak256(byte[] message)
    {
        return Hash.keccak256(message);
    }

    @Override
    public CharSequence formatTypedMessage(ProviderTypedData[] rawData)
    {
        return Utils.formatTypedMessage(rawData);
    }

    @Override
    public CharSequence formatEIP721Message(String messageData)
    {
        CharSequence msgData = "";
        try
        {
            StructuredDataEncoder eip721Object = new StructuredDataEncoder(messageData);
            msgData = Utils.formatEIP721Message(eip721Object);
        }
        catch (Exception e)
        {
            Timber.e(e);
        }

        return msgData;
    }

    @Override
    public long getChainId(String messageData)
    {
        long chainId = -1;
        try
        {
            StructuredDataEncoder eip721Object = new StructuredDataEncoder(messageData);
            return Long.parseLong(eip721Object.jsonMessageObject.getDomain().getChainId());
        }
        catch (Exception e)
        {
            Timber.e(e);
        }
        return chainId;
    }

    @Override
    public byte[] getStructuredData(String messageData)
    {
        try
        {
            StructuredDataEncoder eip721Object = new StructuredDataEncoder(messageData);
            return eip721Object.getStructuredData();
        }
        catch (Exception e)
        {
            Timber.e(e);
        }

        return new byte[0];
    }

    public static Sign.SignatureData sigFromByteArray(byte[] sig)
    {
        if (sig.length < 64 || sig.length > 65) return null;

        byte   subv = sig[64];
        if (subv < 27) subv += 27;

        byte[] subrRev = Arrays.copyOfRange(sig, 0, 32);
        byte[] subsRev = Arrays.copyOfRange(sig, 32, 64);

        BigInteger r = new BigInteger(1, subrRev);
        BigInteger s = new BigInteger(1, subsRev);

        return new Sign.SignatureData(subv, subrRev, subsRev);
    }
}

