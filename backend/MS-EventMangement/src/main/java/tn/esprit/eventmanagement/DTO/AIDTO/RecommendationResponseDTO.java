package tn.esprit.eventmanagement.DTO.AIDTO;


import tn.esprit.eventmanagement.DTO.event.EventDTO;

import java.util.List;

public class RecommendationResponseDTO {
    private Long eventId;
    private double score;
    private String title;




    public RecommendationResponseDTO(Long eventId, double score,String title) {
        this.eventId = eventId;
        this.score = score;
        this.title=title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public RecommendationResponseDTO() {
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}