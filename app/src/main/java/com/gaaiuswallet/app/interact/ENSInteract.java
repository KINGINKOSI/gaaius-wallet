package com.gaaiuswallet.app.interact;

import com.gaaiuswallet.app.repository.TokenRepositoryType;
import com.gaaiuswallet.app.ui.widget.entity.ENSHandler;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

import io.reactivex.Single;

/**
 * Created by James on 4/12/2018.
 * Stormbird in Singapore
 */
public class ENSInteract
{
    private final TokenRepositoryType tokenRepository;

    public ENSInteract(TokenRepositoryType tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public Single<String> checkENSAddress(long chainId, String name)
    {
        if (!ENSHandler.canBeENSName(name)) return Single.fromCallable(() -> "0");
        return tokenRepository.resolveENS(chainId, name)
                .map(this::checkAddress);
    }

    private String checkAddress(String returnedAddress)
    {
        BigInteger test = Numeric.toBigInt(returnedAddress);
        if (!test.equals(BigInteger.ZERO))
        {
            return returnedAddress;
        }
        else
        {
            return "0";
        }
    }
}
