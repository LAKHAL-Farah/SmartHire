package tn.esprit.eventmanagement.util;

import org.springframework.stereotype.Component;
import tn.esprit.eventmanagement.DTO.AIDTO.RecommendationRequestDTO;
import tn.esprit.eventmanagement.DTO.event.EventDTO;

import java.util.List;

@Component
public class PromptBuilder {


    public String build(RecommendationRequestDTO dto) {

        StringBuilder eventsString = new StringBuilder();

        for (EventDTO e : dto.getEvents()) {

            List<String> tags = e.getTags().stream()
                    .map(Object::toString)
                    .toList();
            eventsString.append("Event ID: ").append(e.getId())
                    .append(", title: ").append(e.getTitle())
                    .append(", domain: ").append(e.getDomain())
                    .append(", tags: ").append(tags)
                    .append("\n");
        }

        return "You are a recommendation system\n" +
                "User skills: " + dto.getUserSkills() + "\n" +
                "History: " + dto.getHistoryDomains() + "\n\n" +
                "Events:\n" + eventsString +
                "\nReturn ONLY JSON: [{\"eventId\":1,\"score\":90}]";
    }
}