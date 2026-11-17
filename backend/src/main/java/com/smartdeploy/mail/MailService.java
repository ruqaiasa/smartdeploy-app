package com.smartdeploy.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envoi des e-mails de SmartDeploy. En développement, ils sont capturés par Mailpit
 * (smtp localhost:1025, page web http://localhost:8025) : aucun e-mail réel n'est envoyé.
 * Un échec d'envoi est journalisé sans jamais interrompre le parcours appelant.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender, @Value("${smartdeploy.mail.from:SmartDeploy <no-reply@smartdeploy.local>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("E-mail envoyé : « {} »", subject);
        } catch (RuntimeException exception) {
            log.warn("E-mail non envoyé (« {} ») : {}", subject, exception.getMessage());
        }
    }
}
