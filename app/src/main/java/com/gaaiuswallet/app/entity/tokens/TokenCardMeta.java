package com.gaaiuswallet.app.entity.tokens;

import static com.gaaiuswallet.app.ui.widget.entity.ChainItem.CHAIN_ITEM_WEIGHT;
import static com.gaaiuswallet.app.ui.widget.holder.TokenHolder.CHECK_MARK;
import static com.gaaiuswallet.ethereum.EthereumNetworkBase.MAINNET_ID;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import com.gaaiuswallet.app.entity.ContractType;
import com.gaaiuswallet.app.entity.tokendata.TokenGroup;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.app.repository.EthereumNetworkRepository;
import com.gaaiuswallet.app.repository.TokensRealmSource;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.token.entity.ContractAddress;

import java.math.BigInteger;

/**
 * Created by JB on 12/07/2020.
 */
public class TokenCardMeta implements Comparable<TokenCardMeta>, Parcelable
{
    public final String tokenId;
    public long lastUpdate;
    public long lastTxUpdate;
    private final long nameWeight;
    public final ContractType type;
    public final String balance;
    private final String filterText;

    public final TokenGroup group;

    /*
    Initial value is False as Token considered to be Hidden
     */
    public boolean isEnabled = false;

    public TokenCardMeta(long chainId, String tokenAddress, String balance, long timeStamp, AssetDefinitionService svs, String name, String symbol, ContractType type, TokenGroup group)
    {
        this(chainId, tokenAddress, balance, timeStamp, svs, name, symbol, type, group, "");
    }

    public TokenCardMeta(long chainId, String tokenAddress, String balance, long timeStamp, AssetDefinitionService svs, String name, String symbol, ContractType type, TokenGroup group, String attnId)
    {
        this.tokenId = TokensRealmSource.databaseKey(chainId, tokenAddress)
                + (!TextUtils.isEmpty(attnId) ? ("-" + attnId) : "") + (group == TokenGroup.ATTESTATION ? Attestation.ATTESTATION_SUFFIX : "");
        this.lastUpdate = timeStamp;
        this.type = type;
        this.balance = balance;
        this.nameWeight = calculateTokenNameWeight(chainId, tokenAddress, svs, name, symbol, isEthereum(), group, Math.abs(attnId.hashCode()));
        this.filterText = symbol + "'" + name;
        this.group = group;
    }

    public String getAttestationId()
    {
        //should end with ATTESTATION_SUFFIX
        if (tokenId.endsWith(Attestation.ATTESTATION_SUFFIX))
        {
            int sepIndex = tokenId.indexOf("-");
            sepIndex = tokenId.indexOf("-", sepIndex+1);
            return tokenId.substring(sepIndex+1, tokenId.length() - 4);
        }
        else
        {
            return "";
        }
    }

    public TokenCardMeta(long chainId, String tokenAddress, String balance, long timeStamp, long lastTxUpdate, ContractType type, TokenGroup group)
    {
        this.tokenId = TokensRealmSource.databaseKey(chainId, tokenAddress) + (group == TokenGroup.ATTESTATION ? Attestation.ATTESTATION_SUFFIX : "");
        this.lastUpdate = timeStamp;
        this.lastTxUpdate = lastTxUpdate;
        this.type = type;
        this.balance = balance;
        this.nameWeight = 1000;
        this.filterText = null;
        this.group = group;
    }

    public TokenCardMeta(Token token, String filterText)
    {
        this.tokenId = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress());
        this.lastUpdate = token.updateBlancaTime;
        this.lastTxUpdate = token.lastTxCheck;
        this.type = token.getInterfaceSpec();
        this.balance = token.balance.toString();
        this.nameWeight = calculateTokenNameWeight(token.tokenInfo.chainId, token.tokenInfo.address, null, token.getName(), token.getSymbol(), isEthereum(), token.group, 0);
        this.filterText = filterText;
        this.group = token.group;
        this.isEnabled = TextUtils.isEmpty(filterText) || !filterText.equals(CHECK_MARK);
    }

    protected TokenCardMeta(Parcel in)
    {
        tokenId = in.readString();
        lastUpdate = in.readLong();
        lastTxUpdate = in.readLong();
        nameWeight = in.readLong();
        type = ContractType.values()[in.readInt()];
        balance = in.readString();
        filterText = in.readString();
        int groupOrdinal = in.readInt();
        group = TokenGroup.values()[groupOrdinal];
    }

    public long getNameWeight()
    {
        return nameWeight;
    }

    public ContractAddress getContractAddress()
    {
        return new ContractAddress(getChain(), getAddress());
    }

    public static long groupWeight(TokenGroup group)
    {
        return group.ordinal() + 1;
    }

    public String getFilterText()
    {
        return filterText;
    }

    public static final Creator<TokenCardMeta> CREATOR = new Creator<TokenCardMeta>() {
        @Override
        public TokenCardMeta createFromParcel(Parcel in) {
            return new TokenCardMeta(in);
        }

        @Override
        public TokenCardMeta[] newArray(int size) {
            return new TokenCardMeta[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(tokenId);
        dest.writeLong(lastUpdate);
        dest.writeLong(lastTxUpdate);
        dest.writeLong(nameWeight);
        dest.writeInt(type.ordinal());
        dest.writeString(balance);
        dest.writeString(filterText);
        dest.writeInt(group.ordinal());
    }

    public String getAddress()
    {
        return tokenId.substring(0, tokenId.indexOf('-'));
    }

    public boolean hasPositiveBalance()
    {
        return balance != null && !balance.equals("0");
    }

    public boolean hasValidName()
    {
        return nameWeight < Long.MAX_VALUE;
    }

    public long getChain()
    {
        String[] parts = tokenId.split("-");
        if (parts.length > 1)
        {
            return Long.parseLong(parts[1]);
        }
        else
        {
            return MAINNET_ID;
        }
    }

    public BigInteger getTokenID()
    {
        String[] parts = tokenId.split("-");
        if (parts.length > 2)
        {
            return new BigInteger(parts[2]);
        }
        else
        {
            return BigInteger.ZERO;
        }
    }

    private long calculateTokenNameWeight(long chainId, String tokenAddress, AssetDefinitionService svs, String tokenName, String symbol, boolean isEth, TokenGroup group, int attnId)
    {
        long weight = 1000; //ensure base eth types are always displayed first
        String name = svs != null ? svs.getTokenName(chainId, tokenAddress, 1) : null;
        if (name != null)
        {
            name += symbol;
        }
        else
        {
            name = tokenName + symbol;
        }

        if (chainId == EthereumNetworkRepository.getOverrideToken().chainId && tokenAddress.equalsIgnoreCase(EthereumNetworkRepository.getOverrideToken().address))
        {
            return CHAIN_ITEM_WEIGHT + 1;
        }
        else if (isEth)
        {
            return CHAIN_ITEM_WEIGHT + 1 + EthereumNetworkBase.getChainOrdinal(chainId);
        }

        if (TextUtils.isEmpty(name))
        {
            return hasPositiveBalance() ? Long.MAX_VALUE - Math.abs(tokenAddress.hashCode()) :
                    Long.MAX_VALUE - Math.abs(tokenAddress.hashCode())/2;
        }

        int i = 4;
        int pos = 0;

        while (i >= 0 && pos < name.length())
        {
            char c = name.charAt(pos++);
            int w = tokeniseCharacter(c);
            if (w > 0)
            {
                int component = (int)Math.pow(26, i)*w;
                weight += component;
                i--;
            }
        }

        weight += tokenAddress.hashCode()%1753; //prime modulus factor for disambiguation

        if (weight < 2) weight = 2;

        if (group == TokenGroup.ATTESTATION)
        {
            weight += (attnId + 1);
        }

        return weight;
    }

    private static int tokeniseCharacter(char c)
    {
        int w = Character.toLowerCase(c) - 'a' + 1;
        if (w > 'z')
        {
            //could be ideographic, in which case we may want to display this first
            //just use a modulus
            w = w % 10;
        }
        else if (w < 0)
        {
            //must be a number
            w = 1 + (c - '0');
        }
        else
        {
            w += 10;
        }

        return w;
    }

    @Override
    public int compareTo(@NonNull TokenCardMeta otherToken)
    {
        long result = nameWeight - otherToken.nameWeight;
        if (result < Integer.MIN_VALUE) result = Integer.MIN_VALUE;
        if (result > Integer.MAX_VALUE) result = Integer.MAX_VALUE;
        return (int) result;
    }

    public boolean isEthereum()
    {
        return type == ContractType.ETHEREUM;
    }

    public TokenGroup getTokenGroup() {
        return group;
    }

    public boolean isNFT()
    {
        return group == TokenGroup.NFT;
    }

    public float calculateBalanceUpdateWeight()
    {
        float updateWeight = 0;
        //calculate balance update time
        long timeDiff = (System.currentTimeMillis() - lastUpdate) / DateUtils.SECOND_IN_MILLIS;

        if (isEthereum())
        {
            if (timeDiff > 30)
            {
                updateWeight = 2.0f;
            }
            else
            {
                updateWeight = 1.0f;
            }
        }
        else if (hasValidName())
        {
            if (isNFT())
            {
                //ERC721 types which usually get their balance from opensea. Still need to check the balance for stale tokens to spot a positive -> zero balance transition
                updateWeight = (timeDiff > 30) ? 0.25f : 0.01f;
            }
            else if (isEnabled)
            {
                updateWeight = hasPositiveBalance() ? 1.0f : 0.1f; //30 seconds
            }
            else
            {
                updateWeight = 0.1f; //1 minute
            }
        }
        return updateWeight;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public long getUID()
    {
        return tokenId.hashCode();
    }

    public boolean equals(TokenCardMeta other)
    {
        return (tokenId.equalsIgnoreCase(other.tokenId));
    }

    //30 seconds for enabled with balance, 120 seconds for enabled zero balance, 300 for not enabled
    //Note that if we pick up a balance change for non-enabled token that hasn't been locked out, it'll appear with the transfer sweep
    public long calculateUpdateFrequency()
    {
        long timeInSeconds = isEnabled ? (hasPositiveBalance() ? 30 : 120) : 300;
        return timeInSeconds * DateUtils.SECOND_IN_MILLIS;
    }
}
