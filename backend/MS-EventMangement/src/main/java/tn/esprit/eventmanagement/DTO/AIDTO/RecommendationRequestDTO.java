package tn.esprit.eventmanagement.DTO.AIDTO;


import tn.esprit.eventmanagement.DTO.event.EventDTO;

import java.util.List;

public class RecommendationRequestDTO {
    private List<String> userSkills;
    private String careerPath;
    private List<String> historyDomains;
    private List<EventDTO> events;

    public RecommendationRequestDTO(List<String> userSkills, List<EventDTO> events, List<String> historyDomains, String careerPath) {
        this.userSkills = userSkills;
        this.events = events;
        this.historyDomains = historyDomains;
        this.careerPath = careerPath;
    }

    public RecommendationRequestDTO() {
    }

    public List<String> getUserSkills() {
        return userSkills;
    }

    public void setUserSkills(List<String> userSkills) {
        this.userSkills = userSkills;
    }

    public String getCareerPath() {
        return careerPath;
    }

    public void setCareerPath(String careerPath) {
        this.careerPath = careerPath;
    }

    public List<String> getHistoryDomains() {
        return historyDomains;
    }

    public void setHistoryDomains(List<String> historyDomains) {
        this.historyDomains = historyDomains;
    }

    public List<EventDTO> getEvents() {
        return events;
    }

    public void setEvents(List<EventDTO> events) {
        this.events = events;
    }
}