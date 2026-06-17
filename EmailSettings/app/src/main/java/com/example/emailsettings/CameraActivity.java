package com.example.emailsettings;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    
    private static final String PREFS_NAME = "email_settings";
    private static final String KEY_RECIPIENT = "recipient_email";
    private static final String KEY_SENDER = "sender_email";
    private static final String KEY_SENDER_PASSWORD = "sender_password";
    private static final String KEY_USE_DEVICE = "use_device_account";

    private PreviewView previewView;
    private TextView tvCounter;
    private MaterialButton btnMinus;
    private MaterialButton btnPlus;
    private MaterialButton btnSettings;
    private FloatingActionButton fabCapture;
    private FloatingActionButton fabGallery;

    private ImageCapture imageCapture;
    private int maxPhotos = 1;
    private int photosTaken = 0;
    private List<File> capturedPhotos = new ArrayList<>();

    // Gallery picker launcher
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            List<Uri> imageUris = new ArrayList<>();

                            if (data.getClipData() != null) {
                                // Multiple images selected
                                int count = data.getClipData().getItemCount();
                                for (int i = 0; i < count; i++) {
                                    imageUris.add(data.getClipData().getItemAt(i).getUri());
                                }
                            } else if (data.getData() != null) {
                                // Single image selected
                                imageUris.add(data.getData());
                            }

                            if (!imageUris.isEmpty()) {
                                // Convert URIs to files and send
                                List<File> galleryFiles = new ArrayList<>();
                                for (Uri uri : imageUris) {
                                    File file = createTempFileFromUri(uri);
                                    if (file != null) {
                                        galleryFiles.add(file);
                                    }
                                }
                                if (!galleryFiles.isEmpty()) {
                                    capturedPhotos.addAll(galleryFiles);
                                    photosTaken += galleryFiles.size();
                                    updateCounterDisplay();
                                    sendPhotosViaEmail();
                                } else {
                                    Toast.makeText(this,
                                            "Failed to read selected images",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Make status bar transparent
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        previewView = findViewById(R.id.previewView);
        tvCounter = findViewById(R.id.tvCounter);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnSettings = findViewById(R.id.btnSettings);
        fabCapture = findViewById(R.id.fabCapture);
        fabGallery = findViewById(R.id.fabGallery);

        updateCounterDisplay();

        btnMinus.setOnClickListener(v -> {
            if (maxPhotos > 0) {
                maxPhotos--;
                updateCounterDisplay();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (maxPhotos < 10) {
                maxPhotos++;
                updateCounterDisplay();
            }
        });

        btnSettings.setOnClickListener(v -> {
            // Clear settings saved flag to show settings screen
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean("settings_saved", false).apply();
            
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        fabCapture.setOnClickListener(v -> takePhoto());

        fabGallery.setOnClickListener(v -> openGallery());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void updateCounterDisplay() {
        tvCounter.setText(String.valueOf(maxPhotos));
        btnMinus.setEnabled(maxPhotos > 0);
        btnPlus.setEnabled(maxPhotos < 10);
        
        // Update capture button state
        fabCapture.setEnabled(photosTaken < maxPhotos);
        fabCapture.setAlpha(photosTaken < maxPhotos ? 1.0f : 0.4f);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void takePhoto() {
        if (imageCapture == null || photosTaken >= maxPhotos) {
            return;
        }

        // Create output file
        File photoDir = new File(getExternalFilesDir(null), "photos");
        if (!photoDir.exists()) {
            photoDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File photoFile = new File(photoDir, "PHOTO_" + timestamp + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = 
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        capturedPhotos.add(photoFile);
                        photosTaken++;
                        updateCounterDisplay();
                        
                        Toast.makeText(CameraActivity.this, 
                                "Photo " + photosTaken + "/" + maxPhotos + " captured", 
                                Toast.LENGTH_SHORT).show();

                        // If all photos taken, send email
                        if (photosTaken >= maxPhotos) {
                            sendPhotosViaEmail();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed", exception);
                        Toast.makeText(CameraActivity.this, 
                                "Failed to capture photo", 
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendPhotosViaEmail() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String recipient = prefs.getString(KEY_RECIPIENT, "");
        String sender = prefs.getString(KEY_SENDER, "");
        String senderPassword = prefs.getString(KEY_SENDER_PASSWORD, "");
        boolean useDevice = prefs.getBoolean(KEY_USE_DEVICE, false);

        if (recipient.isEmpty() || sender.isEmpty()) {
            Toast.makeText(this, "Email settings not configured properly", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // If using device account, we can't send via SMTP without password
        // Fall back to intent-based sharing
        if (useDevice || senderPassword.isEmpty()) {
            sendPhotosViaIntent(recipient);
            return;
        }

        // Show progress
        Toast.makeText(this, "Sending email...", Toast.LENGTH_SHORT).show();

        // Send via SMTP
        new EmailSender(sender, senderPassword, recipient, capturedPhotos, 
            new EmailSender.EmailSendCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(CameraActivity.this, 
                            "Photos sent successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(CameraActivity.this, 
                            error, Toast.LENGTH_LONG).show();
                        // Fall back to intent
                        sendPhotosViaIntent(recipient);
                    });
                }
            }).execute();
    }

    private void sendPhotosViaIntent(String recipient) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Photos from QuickPhotoSender");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Please find attached photos.");

        ArrayList<Uri> uris = new ArrayList<>();
        for (File photo : capturedPhotos) {
            uris.add(FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photo));
        }
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send photos via..."));
            finish();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email client installed", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryLauncher.launch(intent);
    }

    private File createTempFileFromUri(Uri uri) {
        try {
            File photoDir = new File(getExternalFilesDir(null), "photos");
            if (!photoDir.exists()) {
                photoDir.mkdirs();
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File outFile = new File(photoDir, "GALLERY_" + timestamp + ".jpg");

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            FileOutputStream outputStream = new FileOutputStream(outFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
            return outFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying gallery image", e);
            return null;
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
