package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.QuestionBookmark;

import java.util.List;

public interface QuestionBookmarkService {
    QuestionBookmark addBookmark(Long userId, Long questionId, String note, String tagLabel);
    void removeBookmark(Long userId, Long questionId);
    List<QuestionBookmark> getBookmarksByUser(Long userId);
    List<QuestionBookmark> getBookmarksByTag(Long userId, String tagLabel);
    boolean isBookmarked(Long userId, Long questionId);
    List<String> getUserTags(Long userId);
    QuestionBookmark updateNote(Long bookmarkId, String note);
}
