package tn.esprit.msroadmap.DTO.response;

import tn.esprit.msroadmap.DTO.request.ChatMessageDto;

import java.util.List;

public record NvidiaChatResponseDto(
        String id,
        List<Choice> choices,
        Usage usage
) {
    public record Choice(int index, ChatMessageDto message, String finish_reason) {}

    public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
}
