package tn.esprit.msinterview.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.config.LiveModeScripts;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.enumerated.LiveSubMode;
import tn.esprit.msinterview.entity.enumerated.RoleType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class LiveGreetingBuilder {

    private final LiveModeScripts scripts;

    public String buildGreeting(InterviewSession session,
                                String companyName,
                                String targetRole,
                                InterviewQuestion firstQuestion) {

        String company = (companyName == null || companyName.isBlank()) ? "Tech Company" : companyName;
        String role = (targetRole == null || targetRole.isBlank())
                ? formatRole(session.getRoleType()) : targetRole;

        String warmup = session.getLiveSubMode() == LiveSubMode.PRACTICE_LIVE
            ? scripts.getWarmup().getPractice()
            : scripts.getWarmup().getTest();

        int totalQuestions = session.getQuestionCountRequested() == null ? 0 : session.getQuestionCountRequested();
        String firstQuestionText = firstQuestion == null ? "" : firstQuestion.getQuestionText();

        String template = scripts.getGreeting().getTemplate();
        String greeting = template
            .replace("{company}", company)
            .replace("{role}", role)
            .replace("{warmup}", warmup)
            .replace("{count}", String.valueOf(totalQuestions))
            .replace("{firstQuestion}", firstQuestionText);

        log.info("[LiveGreeting] Built greeting ({} chars)", greeting.length());
        return greeting;
    }

    public String buildRetryPrompt() {
        List<String> prompts = scripts.getRetryPrompts();
        if (prompts.isEmpty()) {
            return "Go ahead, take another shot.";
        }
        return prompts.get(ThreadLocalRandom.current().nextInt(prompts.size()));
    }

    public String buildClosing(InterviewSession session) {
        List<String> closings = scripts.getClosing();
        if (closings.isEmpty()) {
            return "Thank you for your time. We will be in touch.";
        }
        return closings.get(ThreadLocalRandom.current().nextInt(closings.size()));
    }

    public List<String> getFillerPhrases() {
        return scripts.getFillers();
    }

    private String formatRole(RoleType role) {
        if (role == null) {
            return "Software Engineer";
        }

        return switch (role) {
            case SE -> "Software Engineer";
            case CLOUD -> "Cloud Engineer";
            case AI -> "AI Engineer";
            default -> role.name();
        };
    }
}
