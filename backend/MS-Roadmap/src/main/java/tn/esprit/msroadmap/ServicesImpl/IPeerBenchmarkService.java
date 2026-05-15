package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.PeerBenchmark;

import java.util.List;

public interface IPeerBenchmarkService {
    PeerBenchmark getBenchmarkForStep(Long careerPathId, int stepOrder);
    List<PeerBenchmark> getBenchmarksForCareerPath(Long careerPathId);
    String getBenchmarkForUser(Long userId, Long stepId);
    void recordUserStepCompletion(Long userId, Long stepId, int actualDays);
    void recomputeBenchmarks(Long careerPathId);
}
