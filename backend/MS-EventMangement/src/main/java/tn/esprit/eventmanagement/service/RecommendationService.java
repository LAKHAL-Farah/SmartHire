package tn.esprit.eventmanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.AIDTO.RecommendationRequestDTO;
import tn.esprit.eventmanagement.DTO.AIDTO.RecommendationResponseDTO;
import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventRegistration;
import tn.esprit.eventmanagement.repository.EventRegistrationRepository;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.util.JsonParserUtil;
import tn.esprit.eventmanagement.util.PromptBuilder;

import java.util.List;

@Service
public class RecommendationService {

    @Autowired
    private EventRepository eventRepo;
    @Autowired private EventRegistrationRepository regRepo;
    @Autowired private GroqService groqService;
    @Autowired private PromptBuilder promptBuilder;
    @Autowired private JsonParserUtil parser;

    private RecommendationRequestDTO buildDTO(List<Event> history, List<Event> events) {

        RecommendationRequestDTO dto = new RecommendationRequestDTO();

        // skills
        List<String> skills = history.stream()
                .flatMap(e -> e.getTags().stream())
                .map(Object::toString)
                .distinct()
                .toList();

        // domains
        List<String> domains = history.stream()
                .map(Event::getDomain)
                .distinct()
                .toList();

        // events DTO
        List<EventDTO> eventDTOs = events.stream().map(e -> {
            EventDTO ev = new EventDTO();
            ev.setId(e.getId());
            ev.setTitle(e.getTitle());
            ev.setDomain(e.getDomain());

            // ✅ garder EventTag
            ev.setTags(e.getTags());

            return ev;
        }).toList();

        dto.setUserSkills(skills);
        dto.setHistoryDomains(domains);
        dto.setEvents(eventDTOs);

        return dto;
    }
    public List<?> recommend(Long userId) throws Exception {

        List<EventRegistration> registrations =
                regRepo.findByUserId(userId);

        List<Event> history = registrations.stream()
                .map(EventRegistration::getEvent)
                .toList();

        List<Event> upcoming = eventRepo.findUpcomingEvents();
        System.out.println("upcompig"+upcoming);

        RecommendationRequestDTO dto = buildDTO(history, upcoming);

        String prompt = promptBuilder.build(dto);

        String response = groqService.callAI(prompt);

        List<RecommendationResponseDTO> result = parser.extract(response);

        // 🔥 ADD TITLE HERE
        return result.stream().map(r -> {

            return eventRepo.findById(r.getEventId())
                    .map(event -> new RecommendationResponseDTO(
                            r.getEventId(),
                              // ✅ safe
                            r.getScore(),
                            eventRepo.findById(r.getEventId()).get().getTitle()
                    ))
                    .orElse(new RecommendationResponseDTO(
                            r.getEventId(),
                               // fallback
                            r.getScore(),
                            "Unknown Event"
                            ));

        }).toList();
    }
    }
