package com.gaaiuswallet.app.ui;

import static com.gaaiuswallet.app.service.KeystoreAccountService.KEYSTORE_FOLDER;
import static com.gaaiuswallet.app.widget.AWalletAlertDialog.ERROR;
import static com.gaaiuswallet.app.widget.AWalletAlertDialog.WARNING;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.gaaiuswallet.app.BuildConfig;
import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.AuthenticationCallback;
import com.gaaiuswallet.app.entity.AuthenticationFailType;
import com.gaaiuswallet.app.entity.Operation;
import com.gaaiuswallet.app.entity.StandardFunctionInterface;
import com.gaaiuswallet.app.entity.Wallet;
import com.gaaiuswallet.app.entity.WalletType;
import com.gaaiuswallet.app.service.KeyService;
import com.gaaiuswallet.app.service.KeystoreAccountService;
import com.gaaiuswallet.app.util.Utils;
import com.gaaiuswallet.app.viewmodel.BackupKeyViewModel;
import com.gaaiuswallet.app.widget.AWalletAlertDialog;
import com.gaaiuswallet.app.widget.CopyTextView;
import com.gaaiuswallet.app.widget.FunctionButtonBar;
import com.gaaiuswallet.app.widget.SignTransactionDialog;

import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletFile;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;
import wallet.core.jni.CoinType;
import wallet.core.jni.HDWallet;
import wallet.core.jni.PrivateKey;

/**
 * Created by JB on 24/08/2022.
 *
 * NB: do not use any of this code anywhere else in the wallet! This is purely for key diagnostics to help support diagnose issues with keys
 */
@AndroidEntryPoint
public class WalletDiagnosticActivity extends BaseActivity implements StandardFunctionInterface
{
    private BackupKeyViewModel viewModel;
    private AWalletAlertDialog dialog;

    private Wallet wallet;

    private boolean isLegacyKeystore = false;
    private boolean isKeyStore = false;
    private boolean isSeedPhrase = false;
    private boolean isLocked = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_diagnostic);
        toolbar();
        setTitle(getString(R.string.key_diagnostic));

        FunctionButtonBar functionBar = findViewById(R.id.layoutButtons);
        functionBar.setupFunctions(this, new ArrayList<>(Collections.singletonList(R.string.run_key_diagnostic)));
        functionBar.revealButtons();

        if (getIntent() != null)
        {
            wallet = (Wallet) getIntent().getExtras().get("wallet");
        }
        else
        {
            finish();
        }

        initViewModel();
        startKeyDiagnostic();
    }

    @Override
    public void handleClick(String action, int actionId)
    {
        //test cipher
        doUnlock(new UnlockCallback()
        {
            @Override
            public void carryOn(boolean passed)
            {
                Pair<KeyService.KeyExceptionType, String> res = viewModel.testCipher(wallet.address, KeyService.LEGACY_CIPHER_ALGORITHM);
                switch (res.first)
                {
                    case UNKNOWN:
                    case REQUIRES_AUTH:
                        showError("Unknown Failure");
                        break;
                    case INVALID_CIPHER:
                        isLegacyKeystore = false;
                        break;
                    case SUCCESSFUL_DECODE:
                        isLegacyKeystore = true;
                        evaluateKey();
                        break;
                    case IV_NOT_FOUND:
                        showError("IV File not found");
                        return;
                    case ENCRYPTED_FILE_NOT_FOUND:
                        showError("Encrypted Data File not found");
                        return;
                }

                testKeyStore();
            }
        });
    }

    private void startKeyDiagnostic()
    {
        LinearLayout successOverlay = findViewById(R.id.layout_success_overlay);
        if (successOverlay != null) successOverlay.setVisibility(View.GONE);

        setCurrentKeyType();
        boolean hasKey = scanForKey();

        if (hasKey)
        {
            unlockKeyIfRequired();
        }
        else
        {
            showError("Unable to find enclave key for this wallet. Is it a watch wallet?");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    private void initViewModel()
    {
        viewModel = new ViewModelProvider(this)
                .get(BackupKeyViewModel.class);
    }

    private boolean scanForKey()
    {
        TextView status = findViewById(R.id.key_in_enclave);
        if (viewModel.hasKey(wallet.address))
        {
            status.setText(R.string.key_found);
            status.setTextColor(getColor(R.color.green));
            return true;
        }
        else
        {
            status.setText(R.string.key_not_found);
            status.setTextColor(getColor(R.color.danger));
            return false;
        }
    }

    private void setCurrentKeyType()
    {
        TextView status = findViewById(R.id.key_type);
        String walletType = wallet.type.toString();
        status.setText(walletType);
    }

    private void unlockKeyIfRequired()
    {
        TextView lockedState = findViewById(R.id.key_is_locked);
        //first test key to see if it's unlocked
        if (!isLocked)
        {
            Pair<KeyService.KeyExceptionType, String> res = viewModel.testCipher(wallet.address, KeyService.LEGACY_CIPHER_ALGORITHM);

            isLocked = (res.first == KeyService.KeyExceptionType.REQUIRES_AUTH);
            lockedState.setText(isLocked ? "Locked" : "Unlocked");
            lockedState.setTextColor(isLocked ? getColor(R.color.green) : getColor(R.color.danger));
        }
    }

    // Finally, test if key matches up with what's stored in the database
    private void evaluateKey()
    {
        WalletType actualType = getActualKeyType();
        if (actualType == WalletType.NOT_DEFINED) return;

        switch (wallet.type)
        {
            case WATCH:
            case NOT_DEFINED:
            case TEXT_MARKER:
            case LARGE_TITLE:
                break;
            case KEYSTORE:
                if (!isKeyStore)
                {
                    suggestCorrectWallet("Database says Keystore but tests show: " + actualType.toString(), actualType);
                }
                else
                {
                    showSuccess();
                }
                break;
            case HDKEY:
                if (!isSeedPhrase)
                {
                    suggestCorrectWallet("Database says Seed Phrase but tests show: " + actualType.toString(), actualType);
                }
                else
                {
                    showSuccess();
                }
                break;
            case KEYSTORE_LEGACY:
                if (!isLegacyKeystore)
                {
                    suggestCorrectWallet("Database says Keystore Legacy but tests show: " + actualType.toString(), actualType);
                }
                else
                {
                    showSuccess();
                }
                break;
        }
    }

    private WalletType getActualKeyType()
    {
        if (isLegacyKeystore)
        {
            return WalletType.KEYSTORE_LEGACY;
        }
        else if (isSeedPhrase)
        {
            return WalletType.HDKEY;
        }
        else if (isKeyStore)
        {
            return WalletType.KEYSTORE;
        }
        else
        {
            return WalletType.NOT_DEFINED;
        }
    }

    private void suggestCorrectWallet(String suggest, WalletType type)
    {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new AWalletAlertDialog(this);
        dialog.setTitle(R.string.key_status);
        dialog.setMessage("Database keytype mismatch. " + suggest);
        dialog.setIcon(WARNING);
        dialog.setButtonText(R.string.fix_key_state);
        dialog.setButtonListener(v -> {
            updateKeyState(type);
        });
        dialog.setSecondaryButtonText(R.string.action_cancel);
        dialog.setSecondaryButtonListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateKeyState(WalletType type)
    {
        wallet.type = type;
        viewModel.storeWallet(wallet)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::completeUpdate, error -> showError(error.getMessage()))
                .isDisposed();
    }

    private void completeUpdate(Wallet wallet)
    {
        LinearLayout successOverlay = findViewById(R.id.layout_success_overlay);
        if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);
        //restart key scan
        handler.postDelayed(this::startKeyDiagnostic, 1000);
    }

    private void showSuccess()
    {
        final LinearLayout successOverlay = findViewById(R.id.layout_success_overlay);
        if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);
        //restart key scan
        handler.postDelayed(() -> {
            if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        }, 1000);
    }

    //Now test if this is a v2 keystore
    private void testKeyStore()
    {
        Pair<KeyService.KeyExceptionType, String> res = viewModel.testCipher(wallet.address, KeyService.CIPHER_ALGORITHM);
        switch (res.first)
        {
            case UNKNOWN:
            case REQUIRES_AUTH:
                showError("Unknown Failure");
                break;
            case INVALID_CIPHER:
                isKeyStore = false;
                showError("Key Failure: Invalid Cipher");
                break;
            case SUCCESSFUL_DECODE:
                //may or may not be a keystore, could be a seed phrase
                boolean testKey = testKeyType(res.second);
                if (testKey) evaluateKey();
                break;
            case IV_NOT_FOUND:
                showError("IV File not found");
                return;
            case ENCRYPTED_FILE_NOT_FOUND:
                showError("Encrypted Data File not found");
                return;
        }
    }

    private void exposeStatus()
    {
        LinearLayout llStatus = findViewById(R.id.layout_status);
        llStatus.setVisibility(View.VISIBLE);
        CopyTextView pkView = findViewById(R.id.copy_pk);
        pkView.setVisibility(View.GONE);
    }

    private boolean testKeyType(String keyData)
    {
        //could either be a seed phrase or a keystore
        Pattern pattern = Pattern.compile(ImportSeedFragment.validator, Pattern.MULTILINE);
        TextView status = findViewById(R.id.text_status);
        CopyTextView pubKeyText = findViewById(R.id.copy_public);
        exposeStatus();

        //first check for seed phrase
        final Matcher matcher = pattern.matcher(keyData);
        if (!matcher.find())
        {
            int wordCount = wordCount(keyData);

            if (wordCount == 12 || wordCount == 18 || wordCount == 24)
            {
                //is valid seed phrase
                HDWallet newWallet = new HDWallet(keyData, "");
                PrivateKey pk = newWallet.getKeyForCoin(CoinType.ETHEREUM);
                status.setText(R.string.seed_phrase_public_key);
                status.setTextColor(getColor(R.color.green));
                pubKeyText.setText(Numeric.toHexString(pk.getPublicKeySecp256k1(false).data()));

                CopyTextView pkView = findViewById(R.id.copy_pk);
                pkView.setVisibility(View.VISIBLE);
                String pkStr = (new BigInteger(1, pk.data())).toString(16);
                pkView.setFixedText(pkStr);
                isSeedPhrase = true;
                return true;
            }
        }

        if (!isSeedPhrase)
        {
            //attempt to recover the key
            File keyFolder = new File(getFilesDir(), KEYSTORE_FOLDER);
            try
            {
                Credentials credentials = KeystoreAccountService.getCredentialsWithThrow(keyFolder, wallet.address, keyData);

                if (credentials == null)
                {
                    showError("Unable to find Keystore File");
                }
                else
                {
                    status.setText(R.string.keystore_public_key);
                    status.setTextColor(getColor(R.color.green));
                    pubKeyText.setText(credentials.getEcKeyPair().getPublicKey().toString(16));

                    //show PK
                    CopyTextView pkView = findViewById(R.id.copy_pk);
                    pkView.setVisibility(View.VISIBLE);
                    String pk = credentials.getEcKeyPair().getPrivateKey().toString(16);
                    pkView.setFixedText(pk);
                    isKeyStore = true;
                    return true;
                }
            }
            catch (Exception e)
            {
                showError("Keystore decode error: " + e.getMessage());
            }
        }

        return false;
    }

    private int wordCount(String value)
    {
        if (value == null || value.isEmpty()) return 0;
        String[] split = value.split("\\s+");
        return split.length;
    }

    // DO NOT use this style of key authentication in any other code. It's here like this only for key diagnostics
    // Always use the ActionSheet + implement ActionSheetCallback as per SendActivity, NFTAssetDetailActivity etc
    private void doUnlock(UnlockCallback cb)
    {
        if (BuildConfig.DEBUG && Utils.isRunningTest()) //running tests in debug build mode, we don't use key unlock
        {
            cb.carryOn(true);
            return;
        }

        SignTransactionDialog unlockTx = new SignTransactionDialog(this);
        unlockTx.getAuthentication(new AuthenticationCallback()
        {
            @Override
            public void authenticatePass(Operation callbackId)
            {
                cb.carryOn(true);
            }

            @Override
            public void authenticateFail(String fail, AuthenticationFailType failType, Operation callbackId)
            {
                cb.carryOn(false);
            }

            @Override
            public void legacyAuthRequired(Operation callbackId, String dialogTitle, String desc)
            {
                // not interested
            }
        }, this, Operation.FETCH_MNEMONIC);
    }

    //This function could be useful for future, in case this is needed
    @SuppressWarnings("unused")
    private String dumpKeystoreFromSeedPhrase(String seedPhrase, String keystorePassword)
    {
        HDWallet newWallet = new HDWallet(seedPhrase, "");
        PrivateKey pk = newWallet.getKeyForCoin(CoinType.ETHEREUM);
        ECKeyPair keyPair = ECKeyPair.create(pk.data());

        try
        {
            WalletFile wf = org.web3j.crypto.Wallet.createLight(keystorePassword, keyPair);
            return objectMapper.writeValueAsString(wf);
        }
        catch (Exception e)
        {
            Timber.e(e);
        }

        return "";
    }

    private void showError(String error)
    {
        TextView statusTxt = findViewById(R.id.text_status);
        statusTxt.setText(error);
        statusTxt.setTextColor(getColor(R.color.danger));
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = new AWalletAlertDialog(this);
        dialog.setTitle(R.string.title_dialog_error);
        dialog.setMessage(error);
        dialog.setIcon(ERROR);
        dialog.setButtonText(R.string.button_ok);
        dialog.setButtonListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    private interface UnlockCallback
    {
        default void carryOn(boolean passed) { };
    }
}
