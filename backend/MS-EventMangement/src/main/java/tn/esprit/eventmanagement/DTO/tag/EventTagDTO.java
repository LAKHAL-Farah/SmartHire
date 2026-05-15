package tn.esprit.eventmanagement.DTO.tag;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
public class EventTagDTO {

    private Long id;
    private String tagName;
    private String color;

    public EventTagDTO(Long id, String tagName, String color) {
        this.id = id;
        this.tagName = tagName;
        this.color = color;
    }

    public EventTagDTO() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}