package com.gaaiuswallet.app;

import com.gaaiuswallet.token.entity.CryptoFunctionsInterface;
import com.gaaiuswallet.token.entity.ProviderTypedData;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Base64;

import wallet.core.jni.Hash;

public class CryptoFunctions implements CryptoFunctionsInterface
{
    @Override
    public byte[] Base64Decode(String message)
    {
        return Base64.getUrlDecoder().decode(message);
    }

    @Override
    public byte[] Base64Encode(byte[] data)
    {
        return Base64.getUrlEncoder().encode(data);
    }

    @Override
    public BigInteger signedMessageToKey(byte[] data, byte[] signature) throws SignatureException
    {
        Sign.SignatureData sigData = sigFromByteArray(signature);
        if (sigData == null) return BigInteger.ZERO;
        else return Sign.signedMessageToKey(data, sigData);
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
        return null;
    }

    @Override
    public CharSequence formatEIP721Message(String messageData)
    {
        return null;
    }

    @Override
    public long getChainId(String messageData)
    {
        return -1;
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
            e.printStackTrace();
        }

        return new byte[0];
    }

    public static Sign.SignatureData sigFromByteArray(byte[] sig)
    {
        if (sig.length < 64 || sig.length > 65)
        {
            return null;
        }

        byte subv = sig[64];
        if (subv < 27) subv += 27;

        byte[] subrRev = Arrays.copyOfRange(sig, 0, 32);
        byte[] subsRev = Arrays.copyOfRange(sig, 32, 64);

        return new Sign.SignatureData(subv, subrRev, subsRev);
    }
}
