package com.gaaiuswallet.app.entity.ticker;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by JB on 21/04/2021.
 */
public class CoinGeckoTicker extends BaseTicker
{
    public CoinGeckoTicker(String address, double fiatPrice, BigDecimal fiatChange)
    {
        super(address, fiatPrice, fiatChange);
    }

    public static List<CoinGeckoTicker> buildTickerList(String jsonData, String currencyIsoSymbol, double currentConversionRate) throws JSONException
    {
        List<CoinGeckoTicker> res = new ArrayList<>();
        JSONObject data = new JSONObject(jsonData);
        if (data.names() == null) return res;

        for (int i = 0; i < data.names().length(); i++)
        {
            String address = data.names().get(i).toString();
            JSONObject obj = data.getJSONObject(address);
            double fiatPrice = 0.0;
            String fiatChangeStr;
            if (obj.has(currencyIsoSymbol.toLowerCase()))
            {
                fiatPrice = obj.getDouble(currencyIsoSymbol.toLowerCase());
                fiatChangeStr = obj.getString(currencyIsoSymbol.toLowerCase() + "_24h_change");
            }
            else if (obj.has("usd"))
            {
                fiatPrice = obj.getDouble("usd") * currentConversionRate;
                fiatChangeStr = obj.getString("usd_24h_change");
            }
            else
            {
                continue; //handle empty/corrupt returns
            }

            res.add(new CoinGeckoTicker(address, fiatPrice, getFiatChange(fiatChangeStr)));
        }

        return res;
    }
}
