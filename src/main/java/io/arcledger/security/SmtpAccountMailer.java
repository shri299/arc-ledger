package io.arcledger.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "arcledger.mail.enabled", havingValue = "true")
public class SmtpAccountMailer implements AccountMailer {
    private final JavaMailSender sender;
    private final String from;
    private final String publicUrl;

    public SmtpAccountMailer(JavaMailSender sender,
                             @Value("${arcledger.mail.from}") String from,
                             @Value("${arcledger.mail.public-url}") String publicUrl) {
        this.sender = sender;
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Override public boolean isConfigured() { return true; }
    @Override public void sendVerification(String recipient, String token) {
        send(recipient, "Verify your ArcLedger email",
            "Verify your email address: " + publicUrl + "/#/verify-email?token=" + token +
                "\n\nThis link expires in 24 hours.");
    }
    @Override public void sendPasswordReset(String recipient, String token) {
        send(recipient, "Reset your ArcLedger password",
            "Reset your password: " + publicUrl + "/#/reset-password?token=" + token +
                "\n\nThis link expires in 30 minutes. If you did not request it, ignore this email.");
    }
    @Override public void sendStoryInvitation(String recipient, String inviterName, String storyTitle, String token) {
        send(recipient, "You were invited to an ArcLedger story",
            inviterName + " invited you to collaborate on “" + storyTitle + "”.\n\nAccept: " +
                publicUrl + "/#/invitations/accept?token=" + token + "\n\nThis link expires in 7 days.");
    }

    private void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
