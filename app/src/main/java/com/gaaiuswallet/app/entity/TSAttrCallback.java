package com.gaaiuswallet.app.entity;

import com.gaaiuswallet.token.entity.TokenScriptResult;

import java.util.List;

public interface TSAttrCallback
{
    void showTSAttributes(List<TokenScriptResult.Attribute> attrs, boolean updateRequired);
}
