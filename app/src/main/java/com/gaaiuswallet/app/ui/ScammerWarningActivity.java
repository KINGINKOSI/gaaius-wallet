package com.gaaiuswallet.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.BackupOperationType;
import com.gaaiuswallet.app.entity.BackupState;
import com.gaaiuswallet.app.entity.CreateWalletCallbackInterface;
import com.gaaiuswallet.app.entity.Operation;
import com.gaaiuswallet.app.entity.SignAuthenticationCallback;
import com.gaaiuswallet.app.entity.StandardFunctionInterface;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.ui.QRScanning.DisplayUtils;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.viewmodel.BackupKeyViewModel;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;
import com.gaaiuswallet.app.widget.FunctionButtonBar;
import com.gaaiuswallet.app.widget.LayoutCallbackListener;
import com.gaaiuswallet.app.widget.PasswordInputView;
import com.gaaiuswallet.app.widget.SignTransactionDialog;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;

import static com.gaaiuswallet.app.C.Key.WALLET;
import static com.gaaiuswallet.app.entity.BackupState.ENTER_BACKUP_STATE_HD;
import static com.gaaiuswallet.app.entity.BackupState.ENTER_JSON_BACKUP;
import static com.gaaiuswallet.app.entity.BackupState.SHOW_SEED_PHRASE_SINGLE;
import static com.gaaiuswallet.app.entity.BackupState.UPGRADE_KEY_SECURITY;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ScammerWarningActivity extends BaseActivity
{
    private FunctionButtonBar functionButtonBar;
    private Wallet wallet;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        lockOrientation();
        toolbar();
        wallet = getIntent().getParcelableExtra(WALLET);
        setShowSeedPhraseSplash();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void lockOrientation()
    {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        else
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void setShowSeedPhraseSplash()
    {
        setContentView(R.layout.activity_show_seed);
        initViews();
        functionButtonBar.setPrimaryButtonText(R.string.show_seed_phrase);
        functionButtonBar.setPrimaryButtonClickListener(v -> {
            openBackupKeyActivity();
        });
    }

    private void openBackupKeyActivity()
    {
        Intent intent = new Intent(this, BackupKeyActivity.class);
        intent.putExtra(WALLET, wallet);
        intent.putExtra("STATE", SHOW_SEED_PHRASE_SINGLE);
        startActivity(intent);
    }

    private void initViews()
    {
        functionButtonBar = findViewById(R.id.layoutButtons);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        toolbar();
        setTitle(getString(R.string.empty));
    }
}
