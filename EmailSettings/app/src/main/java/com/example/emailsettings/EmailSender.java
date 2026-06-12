package com.example.emailsettings;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailSender extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "EmailSender";
    
    private String senderEmail;
    private String senderPassword;
    private String recipientEmail;
    private List<File> attachments;
    private EmailSendCallback callback;

    public interface EmailSendCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public EmailSender(String senderEmail, String senderPassword, String recipientEmail, 
                      List<File> attachments, EmailSendCallback callback) {
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.recipientEmail = recipientEmail;
        this.attachments = attachments;
        this.callback = callback;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            // Determine SMTP settings based on sender email domain
            String smtpHost;
            String smtpPort;
            
            if (senderEmail.contains("@gmail.com")) {
                smtpHost = "smtp.gmail.com";
                smtpPort = "587";
            } else if (senderEmail.contains("@yahoo.com")) {
                smtpHost = "smtp.mail.yahoo.com";
                smtpPort = "587";
            } else if (senderEmail.contains("@outlook.com") || senderEmail.contains("@hotmail.com")) {
                smtpHost = "smtp-mail.outlook.com";
                smtpPort = "587";
            } else {
                // Default to Gmail settings
                smtpHost = "smtp.gmail.com";
                smtpPort = "587";
            }

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.ssl.trust", smtpHost);

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, senderPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Photos from QuickPhotoSender");

            // Create message body
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Please find attached photos.");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // Attach photos
            for (File file : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource source = new FileDataSource(file);
                attachmentPart.setDataHandler(new DataHandler(source));
                attachmentPart.setFileName(file.getName());
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            // Send message
            Transport.send(message);
            
            Log.i(TAG, "Email sent successfully");
            return true;

        } catch (MessagingException e) {
            Log.e(TAG, "Failed to send email", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (callback != null) {
            if (success) {
                callback.onSuccess();
            } else {
                callback.onFailure("Failed to send email. Please check your credentials.");
            }
        }
    }
}
