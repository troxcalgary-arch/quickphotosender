# EmailSettings

A minimal Android app with three settings:
- Recipient email  (who to send to)
- Sender email     (who it comes from)
- Checkbox         (use the device's own Google account as the sender)

## How to open in Android Studio

1. Unzip this project somewhere on your machine.
2. Open Android Studio -> File -> Open -> select the `EmailSettings` folder.
3. Wait for Gradle to sync (it downloads dependencies automatically).
4. Run on an emulator or physical device (minSdk 24, i.e. Android 7+).

## What it does

- Settings are saved with SharedPreferences, so they survive app restarts.
- On launch, the app scans `AccountManager` for the device's primary Google account
  and shows it next to the checkbox.
- When the checkbox is ticked, the custom sender field is disabled/greyed out.
- Both email fields are validated before saving.
- No internet permission is required -- this app only stores settings.
  Your actual sending logic (SMTP, Gmail API, etc.) goes on top of these saved values.

## Adding your sending logic

Read saved settings anywhere in your app:

    SharedPreferences prefs = getSharedPreferences("email_settings", MODE_PRIVATE);
    String recipient  = prefs.getString("recipient_email", "");
    String sender     = prefs.getString("sender_email", "");   // empty when using device account
    boolean useDevice = prefs.getBoolean("use_device_account", false);

    String effectiveSender = useDevice ? "<device google account>" : sender;
