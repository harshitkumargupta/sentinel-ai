package com.sentinelai.notification;

import com.sentinelai.auth.domain.Role;
import com.sentinelai.auth.repository.UserRepository;
import com.sentinelai.common.domain.Severity;
import com.sentinelai.notification.domain.Notification;
import com.sentinelai.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises in-app notifications. Used both inline by the synchronous correlation path and by the
 * Kafka notification consumer, so escalation notifications are produced identically either way.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** Notify every admin that an incident escalated to a HIGH/CRITICAL severity. */
    @Transactional
    public int notifyAdminsOfEscalation(Long incidentId, Severity severity, int riskScore) {
        String message = "Incident #" + incidentId + " escalated to " + severity
                + " (risk " + riskScore + ")";
        int count = 0;
        for (var admin : userRepository.findByRole(Role.ADMIN)) {
            notificationRepository.save(Notification.builder()
                    .user(admin)
                    .incidentId(incidentId)
                    .message(message)
                    .read(false)
                    .build());
            count++;
        }
        return count;
    }
}
