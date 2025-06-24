package com.gaaiuswallet.app.entity;

public interface HomeCommsInterface
{
    void requestNotificationPermission();
    void backupSuccess(String keyAddress);
    void resetTokens();
    void resetTransactions();
}
