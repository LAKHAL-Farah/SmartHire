package tn.esprit.eventmanagement.DTO.AI;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class AIResponseDTO {
    private double originality;
    private double technical;

    public double getOriginality() {
        return originality;
    }

    public void setOriginality(double originality) {
        this.originality = originality;
    }

    public double getTechnical() {
        return technical;
    }

    public void setTechnical(double technical) {
        this.technical = technical;
    }

    public double getFeasibility() {
        return feasibility;
    }

    public void setFeasibility(double feasibility) {
        this.feasibility = feasibility;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    private double feasibility;
    private String feedback;
}