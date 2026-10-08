package com.sentinelai.notification.channel;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.notification.domain.Notification;
import com.sentinelai.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** In-app bell: one notification row per enabled analyst/admin of the org. */
@Component
@RequiredArgsConstructor
public class InAppSender implements ChannelSender {

    private final UserRepository users;
    private final NotificationRepository notifications;

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    @Transactional
    public Result send(NotificationChannel channel, Message m) {
        int n = 0;
        for (var u : users.findByOrg_Id(m.orgId())) {
            if (u.isEnabled() && (u.getRole() == Role.ANALYST || u.getRole() == Role.ADMIN)) {
                notifications.save(Notification.builder().user(u).incidentId(m.incidentId())
                        .message(m.subject()).read(false).build());
                n++;
            }
        }
        return new Result(false, "in-app to " + n + " user(s)");
    }
}
