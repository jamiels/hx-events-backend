package com.rahilhusain.hxevent.service;

import com.rahilhusain.hxevent.domain.Mail;

public interface MailService {
    void sendEmail(String from, String recipient, String subject, String body, String replyTo);

    void queueMail(Mail mail);

    void sendQueuedMails();
}
