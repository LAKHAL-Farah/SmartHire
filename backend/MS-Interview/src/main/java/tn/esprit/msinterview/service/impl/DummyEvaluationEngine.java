package tn.esprit.msinterview.service.impl;

import lombok.extern.slf4j.Slf4j;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.service.EvaluationEngine;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class DummyEvaluationEngine implements EvaluationEngine {

    @Override
    public AnswerEvaluation evaluateTextAnswer(SessionAnswer answer) {
        double content = score(5.0, 9.8);
        double clarity = score(5.0, 9.8);
        double technical = score(5.0, 9.8);
        double confidence = score(5.0, 9.8);
        double posture = score(5.0, 9.8);
        double tone = score(5.0, 9.8);
        double emotion = score(5.0, 9.8);
        double eyeContact = score(5.0, 9.8);
        double expression = score(5.0, 9.8);
        double speechRate = score(95.0, 165.0);
        int hesitationCount = ThreadLocalRandom.current().nextInt(0, 6);

        double overall = (content * 0.30) + (technical * 0.25) + (confidence * 0.25) + (posture * 0.20);

        log.debug("Dummy evaluation generated for answer {} with overall score {}", answer.getId(), overall);

        return AnswerEvaluation.builder()
                .answer(answer)
                .contentScore(content)
                .clarityScore(clarity)
                .technicalScore(technical)
                .confidenceScore(confidence)
                .toneScore(tone)
                .emotionScore(emotion)
                .speechRate(speechRate)
                .hesitationCount(hesitationCount)
                .postureScore(posture)
                .eyeContactScore(eyeContact)
                .expressionScore(expression)
                .overallScore(overall)
                .aiFeedback("Dummy evaluation for flow testing only.")
                .build();
    }

    @Override
    public AnswerEvaluation evaluateCodingAnswer(Long answerId) {
        double correctness = score(5.0, 9.8);
        double quality = score(5.0, 9.8);
        double algorithmic = score(5.0, 9.8);
        double clarity = score(5.0, 9.8);
        double depth = score(5.0, 9.8);

        return AnswerEvaluation.builder()
                .contentScore(depth)
                .clarityScore(clarity)
                .technicalScore(algorithmic)
                .codeCorrectnessScore(correctness)
                .codeComplexityNote("Time: O(n) Space: O(n)")
                .aiFeedback("Dummy coding evaluation for flow testing only.")
                .followUpGenerated("Can you improve space complexity?")
                .overallScore((correctness + quality + algorithmic + clarity + depth) / 5.0)
                .build();
    }

    private double score(double min, double max) {
        double value = ThreadLocalRandom.current().nextDouble(min, max);
        return Math.round(value * 100.0) / 100.0;
    }
}
