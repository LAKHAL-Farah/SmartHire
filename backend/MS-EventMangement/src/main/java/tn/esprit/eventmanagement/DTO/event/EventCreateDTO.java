package tn.esprit.eventmanagement.DTO.event;



import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EventCreateDTO {

    private String title;
    private String description;
    private String type;
    private String location;
    private Boolean isOnline;
    private String onlineUrl;
    private String domain;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Integer maxCapacity;
    private Long organizerId;

    private List<Long> tagIds;

    // getters & setters
}