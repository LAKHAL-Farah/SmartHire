package tn.esprit.msroadmap.DTO.request;

import java.util.List;
import java.util.Map;

public record NvidiaChatRequestDto(
        String model,
        List<ChatMessageDto> messages,
        double temperature,
        int max_tokens,
        double top_p,
        Map<String, Object> extra_body
) {}
