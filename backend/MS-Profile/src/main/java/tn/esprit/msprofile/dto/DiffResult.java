package tn.esprit.msprofile.dto;

import java.util.List;

public record DiffResult(
        List<SectionDiff> sections,
        int totalChanges,
        int keywordsAdded,
        int keywordsRemoved,
        int sentencesRewritten
) {
    public record SectionDiff(
            String sectionName,
            DiffType diffType,
            String originalContent,
            String revisedContent,
            List<String> addedKeywords,
            List<String> removedKeywords,
            String changeSummary
    ) {
    }

    public enum DiffType {
        ADDED,
        REMOVED,
        MODIFIED,
        UNCHANGED
    }
}
