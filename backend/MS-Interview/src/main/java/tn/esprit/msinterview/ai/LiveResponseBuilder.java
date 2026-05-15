package tn.esprit.msinterview.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.config.LiveModeScripts;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class LiveResponseBuilder {

    private final LiveModeScripts scripts;

    private static final List<String> FOLLOW_UP_INTROS = List.of(
            "Interesting - %s",
            "I'd like to dig into that a bit more. %s",
            "Quick follow-up on that: %s",
            "Let me ask you this. %s",
            "Building on what you just said - %s",
            "Tell me more - %s"
    );

    private static final List<String> FEEDBACK_INTROS = List.of(
            "Let me stop you there for a second. ",
            "Okay, hold on - let me give you some quick feedback. ",
            "Alright, let me pause here. ",
            "Before we continue - I want to flag something. ",
            "Hmm, let me offer a thought on that. ",
            "Quick note before we move on. "
    );

    public String buildTransitionSpeech(String nextQuestion) {
        List<String> templates = scripts.getTransitions();
        String normalizedQuestion = nextQuestion == null ? "" : nextQuestion;
        if (templates == null || templates.isEmpty()) {
            return "Next question: " + normalizedQuestion;
        }

        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return template.replace("{nextQuestion}", normalizedQuestion);
    }

    public String buildFollowUpSpeech(String followUp) {
        return String.format(
                FOLLOW_UP_INTROS.get(ThreadLocalRandom.current().nextInt(FOLLOW_UP_INTROS.size())),
                followUp == null ? "" : followUp
        );
    }

    public String buildFeedbackSpeech(String aiFeedback, double score) {
        String intro = FEEDBACK_INTROS.get(ThreadLocalRandom.current().nextInt(FEEDBACK_INTROS.size()));
        String trimmed = trimToSentences(aiFeedback, 2);
        return intro + trimmed + " Would you like to retry this question or move on?";
    }

    private String trimToSentences(String text, int max) {
        if (text == null || text.isBlank()) {
            return "Your answer could use a bit more depth.";
        }
        String[] parts = text.split("(?<=[.!?])\\s+");
        return String.join(" ", Arrays.copyOfRange(parts, 0, Math.min(parts.length, max)));
    }
}
