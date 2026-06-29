package com.divakarchowdary.pom.service;

import com.divakarchowdary.pom.model.PomUpdaterProperties;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Properties;

@Slf4j
@Service
public class EmailService {

    private final PomUpdaterProperties props;
    private Session session;

    public EmailService(PomUpdaterProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        Properties mailProps = new Properties();
        mailProps.put("mail.smtp.host", props.getEmailSmtpHost() != null ? props.getEmailSmtpHost() : "localhost");
        mailProps.put("mail.smtp.port", props.getEmailSmtpPort());
        mailProps.put("mail.smtp.auth", props.isEmailSmtpAuth());

        this.session = Session.getInstance(mailProps);
        log.info("Email session initialized: {}:{}", props.getEmailSmtpHost(), props.getEmailSmtpPort());
    }

    public void send(String subject, String htmlBody) {
        if (props.getEmailRecipients() == null || props.getEmailRecipients().isEmpty()) {
            log.warn("No email recipients configured — skipping notification.");
            return;
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(props.getEmailFrom()));

            String toList = String.join(",", props.getEmailRecipients());
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toList));

            if (props.getEmailCcRecipients() != null && !props.getEmailCcRecipients().isEmpty()) {
                String ccList = String.join(",", props.getEmailCcRecipients());
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccList));
            }

            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);

            log.info("Email sent to: [{}]", toList);

        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage(), e);
        }
    }
}
