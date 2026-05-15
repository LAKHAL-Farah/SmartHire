package tn.esprit.eventmanagement.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import tn.esprit.eventmanagement.DTO.AIDTO.RecommendationResponseDTO;


import java.util.List;

@Component
public class JsonParserUtil {

    public List<RecommendationResponseDTO> extract(String response) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        JsonNode root = mapper.readTree(response);

        String content = root
                .get("choices")
                .get(0)
                .get("message")
                .get("content")
                .asText();

        // 🔥 content = JSON string from AI
        return mapper.readValue(
                content,
                new TypeReference<List<RecommendationResponseDTO>>() {}
        );
    }
}