package tn.esprit.msinterview.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.QuestionBookmark;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.QuestionBookmarkRepository;
import tn.esprit.msinterview.service.QuestionBookmarkService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionBookmarkServiceImpl implements QuestionBookmarkService {

    private final QuestionBookmarkRepository questionBookmarkRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;

    @Override
    @Transactional
    public QuestionBookmark addBookmark(Long userId, Long questionId, String note, String tagLabel) {
        // Guard against duplicate bookmarks
        if (questionBookmarkRepository.existsByUserIdAndQuestionId(userId, questionId)) {
            throw new IllegalArgumentException("Question already bookmarked by this user: " + questionId);
        }

        InterviewQuestion question = interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));

        QuestionBookmark bookmark = QuestionBookmark.builder()
                .userId(userId)
                .question(question)
                .note(note)
                .tagLabel(tagLabel)
                .savedAt(LocalDateTime.now())
                .build();

        return questionBookmarkRepository.save(bookmark);
    }

    @Override
    @Transactional
    public void removeBookmark(Long userId, Long questionId) {
        questionBookmarkRepository.deleteByUserIdAndQuestionId(userId, questionId);
        log.info("Bookmark removed for userId={}, questionId={}", userId, questionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionBookmark> getBookmarksByUser(Long userId) {
        return questionBookmarkRepository.findByUserIdOrderBySavedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionBookmark> getBookmarksByTag(Long userId, String tagLabel) {
        return questionBookmarkRepository.findByUserIdAndTagLabel(userId, tagLabel);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBookmarked(Long userId, Long questionId) {
        return questionBookmarkRepository.existsByUserIdAndQuestionId(userId, questionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getUserTags(Long userId) {
        return questionBookmarkRepository.findTagsByUserId(userId);
    }

    @Override
    @Transactional
    public QuestionBookmark updateNote(Long bookmarkId, String note) {
        QuestionBookmark bookmark = questionBookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new IllegalArgumentException("Bookmark not found: " + bookmarkId));

        bookmark.setNote(note);
        bookmark.setSavedAt(LocalDateTime.now());
        return questionBookmarkRepository.save(bookmark);
    }
}
