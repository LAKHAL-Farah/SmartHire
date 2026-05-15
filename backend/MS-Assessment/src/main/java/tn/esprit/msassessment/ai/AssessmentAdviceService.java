package tn.esprit.msassessment.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.msassessment.entity.AssessmentSession;
import tn.esprit.msassessment.entity.SessionAnswer;
import tn.esprit.msassessment.repository.SessionAnswerRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based fallback advice service.
 * Used when the Python AI service is unavailable or disabled.
 */
@Service
@Slf4j
public class AssessmentAdviceService {

    private final SessionAnswerRepository sessionAnswerRepository;

    public AssessmentAdviceService(SessionAnswerRepository sessionAnswerRepository) {
        this.sessionAnswerRepository = sessionAnswerRepository;
    }

    /**
     * Generate advice based on simple rules (fallback when Python AI is unavailable).
     */
    public List<String> generateAdvice(AssessmentSession session, String situation, String careerPath) {
        List<String> advice = new ArrayList<>();
        
        Integer score = session.getScorePercent();
        if (score == null) score = 0;

        // Integrity violation
        if (session.isIntegrityViolation()) {
            advice.add("⚠️ Your attempt was flagged because you left the quiz screen. Your score was recorded as 0%. Treat every assessment like a real interview — stay focused and avoid switching tabs or minimizing the window.");
            advice.add("💡 Next time, prepare a distraction-free environment before you start.");
            return advice;
        }

        // Score-based advice
        if (score >= 90) {
            advice.add("🏆 Excellent result! A score above 90% shows strong mastery. Keep this momentum going.");
            advice.add("🚀 Apply your skills in a real project or contribute to open-source to deepen your expertise.");
        } else if (score >= 75) {
            advice.add("✅ Good performance. You have a solid foundation — focus on the gaps to reach expert level.");
            advice.add("🛠️ Practice with hands-on exercises and small projects to reinforce the concepts.");
        } else if (score >= 50) {
            advice.add("📈 There is clear room for improvement. Review the questions you missed and study those concepts before your next assessment.");
            advice.add("🛠️ Practice with hands-on exercises and small projects to reinforce the concepts.");
        } else if (score >= 30) {
            advice.add("📚 The fundamentals need more work. Dedicate focused study time to the core topics of this category.");
            advice.add("📖 Revisit the official documentation or a structured course before attempting other assessments.");
        } else {
            advice.add("🔴 Significant gaps detected. Don't be discouraged — use this as a starting point and build from the basics step by step.");
            advice.add("📖 Start with a structured course or bootcamp focused on the fundamentals of this category.");
        }

        // Behavioral analysis
        try {
            List<SessionAnswer> answers = sessionAnswerRepository.findDetailBySession(session.getId());
            
            long easyWrong = answers.stream()
                    .filter(a -> !a.isCorrect() && "EASY".equalsIgnoreCase(
                            a.getQuestion().getDifficulty() != null ? a.getQuestion().getDifficulty().name() : ""))
                    .count();
            
            if (easyWrong > 0) {
                advice.add("⚡ You missed some easy questions. These cover foundational concepts — make sure you understand the basics thoroughly.");
            }

            long hardCorrect = answers.stream()
                    .filter(a -> a.isCorrect() && "HARD".equalsIgnoreCase(
                            a.getQuestion().getDifficulty() != null ? a.getQuestion().getDifficulty().name() : ""))
                    .count();
            
            if (hardCorrect > 0 && score < 75) {
                advice.add("💪 You answered hard questions correctly despite a lower overall score. Your advanced knowledge is there — strengthen the fundamentals to unlock your full potential.");
            }

            // Duration analysis
            if (session.getStartedAt() != null && session.getCompletedAt() != null) {
                long durationMin = Duration.between(session.getStartedAt(), session.getCompletedAt()).toMinutes();
                if (durationMin < 3 && score < 70) {
                    advice.add("⏱️ You completed the assessment very quickly. Rushing through questions often leads to avoidable mistakes — read each question carefully.");
                } else if (durationMin > 40 && score < 60) {
                    advice.add("⏳ The assessment took a long time. If you were unsure on many questions, that's a signal to revisit the core material before your next attempt.");
                }
            }
        } catch (Exception e) {
            log.warn("Could not analyze session answers for advice: {}", e.getMessage());
        }

        // Contextual advice based on profile
        if (situation != null && !situation.trim().isEmpty()) {
            String sit = situation.toLowerCase().trim();
            if (sit.contains("student") && score < 50) {
                advice.add("🎓 As a student, every assessment is a learning opportunity. Dedicate 1–2 hours daily to practice and you will see rapid improvement.");
            } else if (sit.contains("employ") && score >= 50 && score < 75) {
                advice.add("💼 Balancing work and learning is tough. Try 30-minute focused sessions on the weak topics during the week.");
            }
        }

        if (careerPath != null && !careerPath.trim().isEmpty()) {
            String path = careerPath.toLowerCase().trim();
            if (path.contains("senior") || path.contains("lead")) {
                advice.add("🚀 You're aiming for a senior/lead role. Consider system design, architecture patterns, and mentoring skills as your next focus areas.");
            } else if (path.contains("data") || path.contains("ai") || path.contains("ml")) {
                advice.add("📊 Strong foundation for a data/AI path. Complement your skills with statistics, ML frameworks, and real dataset projects.");
            }
        }

        // Add contextual advice if we have profile info
        if ((situation != null && !situation.trim().isEmpty()) || (careerPath != null && !careerPath.trim().isEmpty())) {
            String categoryTitle = session.getCategory() != null ? session.getCategory().getTitle() : "this assessment";
            String contextLine = "🎯 Based on your profile";
            if (situation != null && !situation.trim().isEmpty()) {
                contextLine += " (" + situation.trim() + ")";
            }
            if (careerPath != null && !careerPath.trim().isEmpty()) {
                contextLine += " aiming for " + careerPath.trim();
            }
            contextLine += ": focus on " + categoryTitle + " to align with your goals.";
            advice.add(contextLine);
        }

        return advice;
    }

    /**
     * Suggest categories based on onboarding profile (fallback when Python AI is unavailable).
     */
    public List<String> suggestCategories(String situation, String careerPath, String headline,
                                        String customSituation, String customCareerPath, List<String> availableCodes) {
        List<String> suggested = new ArrayList<>();
        
        // Combine all profile information
        StringBuilder profileBuilder = new StringBuilder();
        if (situation != null && !situation.trim().isEmpty()) {
            profileBuilder.append(situation.trim()).append(" ");
        }
        if (careerPath != null && !careerPath.trim().isEmpty()) {
            profileBuilder.append(careerPath.trim()).append(" ");
        }
        if (headline != null && !headline.trim().isEmpty()) {
            profileBuilder.append(headline.trim()).append(" ");
        }
        if (customSituation != null && !customSituation.trim().isEmpty()) {
            profileBuilder.append(customSituation.trim()).append(" ");
        }
        if (customCareerPath != null && !customCareerPath.trim().isEmpty()) {
            profileBuilder.append(customCareerPath.trim()).append(" ");
        }
        
        String profile = profileBuilder.toString().toLowerCase();

        // Rule-based category suggestions
        if (profile.contains("java") || profile.contains("backend") || profile.contains("spring")) {
            addIfAvailable(suggested, "JAVA_OOP", availableCodes);
            addIfAvailable(suggested, "SQL_BASICS", availableCodes);
            addIfAvailable(suggested, "SPRING_BOOT", availableCodes);
        }
        
        if (profile.contains("python") || profile.contains("data") || profile.contains("ai") || profile.contains("ml")) {
            addIfAvailable(suggested, "PYTHON_CORE", availableCodes);
            addIfAvailable(suggested, "MACHINE_LEARNING", availableCodes);
            addIfAvailable(suggested, "DATA_SCIENCE", availableCodes);
            addIfAvailable(suggested, "DSA_BASICS", availableCodes);
        }
        
        if (profile.contains("javascript") || profile.contains("frontend") || profile.contains("react") || profile.contains("angular")) {
            addIfAvailable(suggested, "JS_TS_WEB", availableCodes);
            addIfAvailable(suggested, "REACT_WEB", availableCodes);
        }
        
        if (profile.contains("react")) {
            addIfAvailable(suggested, "REACT_WEB", availableCodes);
            addIfAvailable(suggested, "JS_TS_WEB", availableCodes);
        }
        
        if (profile.contains("angular")) {
            addIfAvailable(suggested, "ANGULAR_WEB", availableCodes);
            addIfAvailable(suggested, "JS_TS_WEB", availableCodes);
        }
        
        if (profile.contains("vue")) {
            addIfAvailable(suggested, "VUE_JS", availableCodes);
            addIfAvailable(suggested, "JS_TS_WEB", availableCodes);
        }
        
        if (profile.contains("node") || profile.contains("express")) {
            addIfAvailable(suggested, "NODE_BACKEND", availableCodes);
            addIfAvailable(suggested, "JS_TS_WEB", availableCodes);
        }
        
        if (profile.contains("devops") || profile.contains("cloud") || profile.contains("docker")) {
            addIfAvailable(suggested, "DOCKER_K8S", availableCodes);
            addIfAvailable(suggested, "DEVOPS_ADVANCED", availableCodes);
            addIfAvailable(suggested, "AWS_CLOUD", availableCodes);
        }
        
        if (profile.contains("security") || profile.contains("cyber")) {
            addIfAvailable(suggested, "CYBERSECURITY", availableCodes);
            addIfAvailable(suggested, "WEB_SECURITY", availableCodes);
        }
        
        if (profile.contains("algorithm") || profile.contains("competitive") || profile.contains("leetcode")) {
            addIfAvailable(suggested, "DSA_BASICS", availableCodes);
        }
        
        if (profile.contains("mobile") || profile.contains("android") || profile.contains("ios")) {
            addIfAvailable(suggested, "KOTLIN_ANDROID", availableCodes);
            addIfAvailable(suggested, "SWIFT_IOS", availableCodes);
            addIfAvailable(suggested, "FLUTTER_DART", availableCodes);
        }
        
        if (profile.contains("csharp") || profile.contains("c#") || profile.contains("dotnet") || profile.contains(".net")) {
            addIfAvailable(suggested, "CSHARP_DOTNET", availableCodes);
        }
        
        if (profile.contains("php") || profile.contains("laravel")) {
            addIfAvailable(suggested, "PHP_WEB", availableCodes);
        }
        
        if (profile.contains("ruby") || profile.contains("rails")) {
            addIfAvailable(suggested, "RUBY_RAILS", availableCodes);
        }
        
        if (profile.contains("go") || profile.contains("golang")) {
            addIfAvailable(suggested, "GO_LANG", availableCodes);
        }
        
        if (profile.contains("rust")) {
            addIfAvailable(suggested, "RUST_SYSTEMS", availableCodes);
        }
        
        if (profile.contains("blockchain") || profile.contains("web3") || profile.contains("crypto")) {
            addIfAvailable(suggested, "BLOCKCHAIN_WEB3", availableCodes);
        }
        
        if (profile.contains("game") || profile.contains("unity")) {
            addIfAvailable(suggested, "GAME_DEV_UNITY", availableCodes);
        }
        
        if (profile.contains("ux") || profile.contains("ui") || profile.contains("design")) {
            addIfAvailable(suggested, "UX_UI_DESIGN", availableCodes);
        }

        // Add common foundational categories if we have fewer than 5 suggestions
        if (suggested.size() < 5) {
            addIfAvailable(suggested, "DSA_BASICS", availableCodes);
            addIfAvailable(suggested, "SQL_BASICS", availableCodes);
            addIfAvailable(suggested, "GIT_WORKFLOW", availableCodes);
            addIfAvailable(suggested, "REST_API", availableCodes);
            addIfAvailable(suggested, "WEB_SECURITY", availableCodes);
        }
        
        // Add more general categories to reach 5-10 suggestions
        if (suggested.size() < 8) {
            for (String code : availableCodes) {
                if (suggested.size() >= 10) break;
                if (!suggested.contains(code)) {
                    suggested.add(code);
                }
            }
        }

        return suggested;
    }

    private void addIfAvailable(List<String> suggested, String code, List<String> availableCodes) {
        if (availableCodes.contains(code) && !suggested.contains(code)) {
            suggested.add(code);
        }
    }
}