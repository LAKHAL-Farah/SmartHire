package tn.esprit.msinterview.dto;

public record StressPayload(
        String userId,
        Long sessionId,
        Long questionId,
        double stressScore,
        String level,
        double ear,
        double browFurrow,
        long timestamp
) {
}
