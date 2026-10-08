package com.sentinelai.notification.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Email over SMTP when {@code sentinel.notifications.smtp.host} is set; otherwise a mock delivery
 * that is only logged (the UI labels the channel "Mock channel"). The SMTP password comes only
 * from the environment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

    private final NotificationProperties props;

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    public boolean configured() {
        return props.getSmtp().getHost() != null && !props.getSmtp().getHost().isBlank();
    }

    @Override
    public Result send(NotificationChannel channel, Message m) {
        if (!configured()) {
            log.info("[mock email] to={} subject=\"{}\"", channel.getTarget(), m.subject());
            return new Result(true, "no SMTP configured — logged only");
        }
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        var s = props.getSmtp();
        mail.setHost(s.getHost());
        mail.setPort(s.getPort());
        if (!s.getUsername().isBlank()) {
            mail.setUsername(s.getUsername());
            mail.setPassword(s.getPassword());
        }
        Properties p = mail.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(!s.getUsername().isBlank()));
        p.put("mail.smtp.starttls.enable", String.valueOf(s.isStarttls()));
        p.put("mail.smtp.connectiontimeout", String.valueOf(props.getWebhookTimeoutMs()));
        p.put("mail.smtp.timeout", String.valueOf(props.getWebhookTimeoutMs()));
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(s.getFrom());
        msg.setTo(channel.getTarget());
        msg.setSubject(m.subject());
        msg.setText(m.body());
        mail.send(msg);
        return new Result(false, "emailed " + channel.getTarget());
    }
}
