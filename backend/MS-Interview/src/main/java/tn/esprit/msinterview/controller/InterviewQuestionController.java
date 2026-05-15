package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.dto.InterviewQuestionDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.service.InterviewQuestionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionService questionService;

    @GetMapping
    public ResponseEntity<List<InterviewQuestionDTO>> getAllActiveQuestions() {
        List<InterviewQuestion> questions = questionService.getAllActive();
        return ResponseEntity.ok(DTOMapper.toQuestionDTOList(questions));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewQuestionDTO> getQuestionById(@PathVariable Long id) {
        InterviewQuestion question = questionService.getById(id);
        return ResponseEntity.ok(DTOMapper.toQuestionDTO(question));
    }

    @GetMapping("/career-path/{careerPathId}")
    public ResponseEntity<List<InterviewQuestionDTO>> getQuestionsByCareerPath(@PathVariable Long careerPathId) {
        List<InterviewQuestion> questions = questionService.getByCareerPath(careerPathId);
        return ResponseEntity.ok(DTOMapper.toQuestionDTOList(questions));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<InterviewQuestionDTO>> filterQuestions(
            @RequestParam(required = false) RoleType role,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) DifficultyLevel difficulty) {

        List<Long> emptyExcludeList = List.of();
        List<InterviewQuestion> questions = questionService.filterForAdaptiveEngine(role, type, difficulty, emptyExcludeList);
        return ResponseEntity.ok(DTOMapper.toQuestionDTOList(questions));
    }

    @GetMapping("/coverage")
    public ResponseEntity<Map<String, Long>> checkBankCoverage() {
        Map<String, Long> coverage = questionService.checkBankCoverage();
        return ResponseEntity.ok(coverage);
    }

    @PostMapping
    public ResponseEntity<InterviewQuestionDTO> createQuestion(@RequestBody InterviewQuestion question) {
        InterviewQuestion created = questionService.createQuestion(question);
        return new ResponseEntity<>(DTOMapper.toQuestionDTO(created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestionDTO> updateQuestion(
            @PathVariable Long id,
            @RequestBody InterviewQuestion updated) {
        InterviewQuestion question = questionService.updateQuestion(id, updated);
        return ResponseEntity.ok(DTOMapper.toQuestionDTO(question));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteQuestion(@PathVariable Long id) {
        questionService.softDeleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<InterviewQuestionDTO> addTag(
            @PathVariable Long id,
            @RequestParam String tag) {
        InterviewQuestion question = questionService.addTag(id, tag);
        return ResponseEntity.ok(DTOMapper.toQuestionDTO(question));
    }
}
