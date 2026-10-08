package com.sentinelai.ai.assistant;

import com.sentinelai.ai.AiProperties;
import com.sentinelai.ai.context.IncidentContextBuilder;
import com.sentinelai.auth.security.AppUserPrincipal;
import com.sentinelai.common.exception.NotFoundException;
import com.sentinelai.incident.domain.Incident;
import com.sentinelai.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an org-scoped incident, builds its sanitized context and routes the question to the assistant. */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final IncidentRepository incidentRepository;
    private final IncidentContextBuilder contextBuilder;
    private final IncidentAssistant assistant;
    private final AiProperties aiProperties;

    @Transactional(readOnly = true)
    public AssistantAnswer ask(Long incidentId, AskIntent intent, String question, AppUserPrincipal actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .filter(i -> i.getOrg().getId().equals(actor.getOrgId()))
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        String cleaned = clean(question);
        AskIntent resolved = intent != null ? intent : AskIntent.route(cleaned).orElse(null);
        return assistant.answer(contextBuilder.build(incident).context(), resolved, cleaned);
    }

    public AiStatus status() {
        return new AiStatus(aiProperties.isEnabled(), aiProperties.getProvider(),
                aiProperties.isOffline(), aiProperties.isOffline() ? "Offline mode" : "Online model",
                aiProperties.getModel());
    }

    /** Strip control characters and collapse whitespace; length is capped by the request DTO. */
    private static String clean(String question) {
        if (question == null) {
            return null;
        }
        String s = question.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? null : s;
    }

    public record AiStatus(boolean enabled, String provider, boolean offline, String label, String model) {
    }
}
