package com.example.emailsettings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {

    // SharedPreferences keys
    private static final String PREFS_NAME       = "email_settings";
    private static final String KEY_RECIPIENT    = "recipient_email";
    private static final String KEY_SENDER       = "sender_email";
    private static final String KEY_SENDER_PASSWORD = "sender_password";
    private static final String KEY_USE_DEVICE   = "use_device_account";
    private static final String KEY_SETTINGS_SAVED = "settings_saved";

    // Views
    private TextInputLayout     tilRecipient;
    private TextInputEditText   etRecipient;
    private TextInputLayout     tilSender;
    private TextInputEditText   etSender;
    private TextInputLayout     tilSenderPassword;
    private TextInputEditText   etSenderPassword;
    private MaterialCheckBox    cbUseDeviceAccount;
    private TextView            tvDeviceAccount;
    private MaterialButton      btnSave;

    // The first Google/email account found on the device (may be null)
    private String deviceAccountEmail = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if settings already saved - skip to camera if so
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean settingsSaved = prefs.getBoolean(KEY_SETTINGS_SAVED, false);
        if (settingsSaved) {
            Intent intent = new Intent(this, CameraActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        setContentView(R.layout.activity_main);

        // Bind views
        tilRecipient       = findViewById(R.id.tilRecipient);
        etRecipient        = findViewById(R.id.etRecipient);
        tilSender          = findViewById(R.id.tilSender);
        etSender           = findViewById(R.id.etSender);
        tilSenderPassword  = findViewById(R.id.tilSenderPassword);
        etSenderPassword   = findViewById(R.id.etSenderPassword);
        cbUseDeviceAccount = findViewById(R.id.cbUseDeviceAccount);
        tvDeviceAccount    = findViewById(R.id.tvDeviceAccount);
        btnSave            = findViewById(R.id.btnSave);

        // Detect the device's primary email account
        deviceAccountEmail = getDeviceAccountEmail();
        if (deviceAccountEmail != null) {
            tvDeviceAccount.setText(deviceAccountEmail);
        } else {
            tvDeviceAccount.setText("No account found on device");
            cbUseDeviceAccount.setEnabled(false);
        }

        // Load previously saved settings
        loadSettings();

        // When checkbox is toggled, enable/disable the custom sender field
        cbUseDeviceAccount.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSenderFieldState(isChecked);
        });

        // Save button
        btnSave.setOnClickListener(v -> saveSettings());
    }

    // Try to find the first email-looking account registered on the device.
    // Uses AccountManager; requires no extra permissions for GET_ACCOUNTS on modern Android
    // when the accounts belong to the app's authenticator or are Google accounts
    // (the system shows a chooser if permission is absent, but usually returns nothing silently).
    private String getDeviceAccountEmail() {
        try {
            AccountManager am = AccountManager.get(this);

            // Prefer Google accounts
            Account[] googleAccounts = am.getAccountsByType("com.google");
            if (googleAccounts.length > 0) {
                return googleAccounts[0].name;
            }

            // Fall back to any account whose name looks like an email
            Account[] allAccounts = am.getAccounts();
            for (Account account : allAccounts) {
                if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches()) {
                    return account.name;
                }
            }
        } catch (Exception e) {
            // Permission denied or other issue - return null gracefully
        }
        return null;
    }

    // Show or hide the custom sender field depending on the checkbox state.
    private void updateSenderFieldState(boolean useDevice) {
        tilSender.setEnabled(!useDevice);
        tilSender.setAlpha(useDevice ? 0.4f : 1.0f);
        tilSenderPassword.setEnabled(!useDevice);
        tilSenderPassword.setAlpha(useDevice ? 0.4f : 1.0f);
        if (useDevice) {
            tilSender.setHelperText("Using device account: " + deviceAccountEmail);
        } else {
            tilSender.setHelperText(null);
        }
    }

    // Persist settings to SharedPreferences.
    private void saveSettings() {
        String recipient = etRecipient.getText() != null
                ? etRecipient.getText().toString().trim() : "";
        String sender = etSender.getText() != null
                ? etSender.getText().toString().trim() : "";
        String senderPassword = etSenderPassword.getText() != null
                ? etSenderPassword.getText().toString().trim() : "";
        boolean useDevice = cbUseDeviceAccount.isChecked();

        // Validate recipient
        if (TextUtils.isEmpty(recipient)) {
            tilRecipient.setError("Recipient email is required");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
            tilRecipient.setError("Enter a valid email address");
            return;
        }
        tilRecipient.setError(null);

        // Validate custom sender only when the checkbox is off
        if (!useDevice) {
            if (TextUtils.isEmpty(sender)) {
                tilSender.setError("Sender email is required");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(sender).matches()) {
                tilSender.setError("Enter a valid email address");
                return;
            }
            if (TextUtils.isEmpty(senderPassword)) {
                tilSenderPassword.setError("Sender password is required");
                return;
            }
        }
        tilSender.setError(null);
        tilSenderPassword.setError(null);

        // Determine actual sender email to use
        String actualSender = useDevice ? deviceAccountEmail : sender;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_RECIPIENT, recipient)
                .putString(KEY_SENDER, actualSender)
                .putString(KEY_SENDER_PASSWORD, useDevice ? "" : senderPassword)
                .putBoolean(KEY_USE_DEVICE, useDevice)
                .putBoolean(KEY_SETTINGS_SAVED, true)
                .apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        
        // Launch camera activity after saving settings
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
        finish();
    }

    // Restore settings from SharedPreferences on launch.
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String recipient  = prefs.getString(KEY_RECIPIENT, "");
        String sender     = prefs.getString(KEY_SENDER, "");
        String senderPassword = prefs.getString(KEY_SENDER_PASSWORD, "");
        boolean useDevice = prefs.getBoolean(KEY_USE_DEVICE, false);

        etRecipient.setText(recipient);
        etSender.setText(sender);
        etSenderPassword.setText(senderPassword);

        // Only honour "use device account" if an account actually exists
        boolean canUseDevice = deviceAccountEmail != null;
        cbUseDeviceAccount.setChecked(useDevice && canUseDevice);

        updateSenderFieldState(useDevice && canUseDevice);
    }
}
