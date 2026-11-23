package com.smartdeploy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Courriels destinés aux personnes qui créent un compte (confirmation de l'adresse, compte activé, refus) :
 * envoyés par un vrai serveur SMTP (Gmail, Outlook…) vers leur vraie adresse. Les notifications de l'équipe
 * restent sur le serveur interne (Mailpit en développement). Sans configuration, repli sur le serveur interne.
 */
@Service
public class AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);

    private final MailService fallback;
    private final JavaMailSenderImpl sender;
    private final String from;

    public AccountMailService(MailService fallback,
                              @Value("${smartdeploy.account-mail.host:}") String host,
                              @Value("${smartdeploy.account-mail.port:587}") int port,
                              @Value("${smartdeploy.account-mail.username:}") String username,
                              @Value("${smartdeploy.account-mail.password:}") String password,
                              @Value("${smartdeploy.account-mail.from:}") String from) {
        this.fallback = fallback;
        this.from = from == null || from.isBlank() ? username : from;
        if (host == null || host.isBlank()) {
            this.sender = null;
            log.info("Courriels des nouveaux comptes : serveur SMTP externe non configuré, envoi vers le serveur interne");
            return;
        }
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(host);
        s.setPort(port);
        s.setUsername(username);
        s.setPassword(password);
        s.setDefaultEncoding("UTF-8");
        Properties props = s.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        this.sender = s;
    }

    public boolean external() {
        return sender != null;
    }

    public void send(String to, String subject, String text) {
        if (sender == null) {
            fallback.send(to, subject, text);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("Courriel envoyé à l'adresse réelle de l'utilisateur : « {} »", subject);
        } catch (RuntimeException exception) {
            log.warn("Courriel non envoyé (« {} ») : {}", subject, exception.getMessage());
        }
    }
}
