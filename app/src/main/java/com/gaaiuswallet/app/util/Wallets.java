package com.gaaiuswallet.app.util;

import com.gaaiuswallet.app.entity.Wallet;

import java.util.ArrayList;

public class Wallets
{
    public static Wallet[] filter(Wallet[] wallets)
    {
        ArrayList<Wallet> list = new ArrayList<>();
        for (Wallet w : wallets)
        {
            if (!w.watchOnly())
                list.add(w);
        }

        return list.toArray(new Wallet[0]);
    }
}
