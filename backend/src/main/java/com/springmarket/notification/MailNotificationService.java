package com.springmarket.notification;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailNotificationService {
    private final JavaMailSender mailSender;

    public MailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOrderConfirmation(String toEmail, UUID orderId, BigDecimal total) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Order confirmation " + orderId);
        message.setText("Your order " + orderId + " has been paid. Total: " + total);
        mailSender.send(message);
    }
}
