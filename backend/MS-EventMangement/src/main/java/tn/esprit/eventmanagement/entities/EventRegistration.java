package tn.esprit.eventmanagement.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity

public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status;

    private Double relevanceScore;

    private LocalDateTime registeredAt;
    private LocalDateTime confirmedAt;

    private Boolean attended;

    private Boolean reminderSevenDaysSent;
    private Boolean reminderOneDaySent;
    private Boolean reminderOneHourSent;

    private String certificateUrl;
    private String certificateCode;
    private LocalDateTime certificateIssuedAt;

    @ManyToOne
    private Event event;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public LocalDateTime getCertificateIssuedAt() {
        return certificateIssuedAt;
    }

    public void setCertificateIssuedAt(LocalDateTime certificateIssuedAt) {
        this.certificateIssuedAt = certificateIssuedAt;
    }

    public String getCertificateCode() {
        return certificateCode;
    }

    public void setCertificateCode(String certificateCode) {
        this.certificateCode = certificateCode;
    }

    public String getCertificateUrl() {
        return certificateUrl;
    }

    public void setCertificateUrl(String certificateUrl) {
        this.certificateUrl = certificateUrl;
    }

    public Boolean getReminderOneHourSent() {
        return reminderOneHourSent;
    }

    public void setReminderOneHourSent(Boolean reminderOneHourSent) {
        this.reminderOneHourSent = reminderOneHourSent;
    }

    public Boolean getReminderOneDaySent() {
        return reminderOneDaySent;
    }

    public void setReminderOneDaySent(Boolean reminderOneDaySent) {
        this.reminderOneDaySent = reminderOneDaySent;
    }

    public Boolean getReminderSevenDaysSent() {
        return reminderSevenDaysSent;
    }

    public void setReminderSevenDaysSent(Boolean reminderSevenDaysSent) {
        this.reminderSevenDaysSent = reminderSevenDaysSent;
    }

    public Boolean getAttended() {
        return attended;
    }

    public void setAttended(Boolean attended) {
        this.attended = attended;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}