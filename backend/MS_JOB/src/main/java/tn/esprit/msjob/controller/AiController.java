package tn.esprit.msjob.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msjob.dto.ChatRequest;
import tn.esprit.msjob.dto.ChatResponse;
import tn.esprit.msjob.service.AiChatService;
import tn.esprit.msjob.service.ResumeMatchService;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class AiController {

    private final AiChatService chatService;
    private final ResumeMatchService resumeMatchService;

    public AiController(AiChatService chatService, ResumeMatchService resumeMatchService) {
        this.chatService = chatService;
        this.resumeMatchService = resumeMatchService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.getMessage());
        String response = chatService.chat(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(response));
    }

    @PostMapping(value = "/resume-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> resumeMatch(
            @RequestPart("resume") MultipartFile resume) throws IOException {
        log.info("Resume match request: {}", resume.getOriginalFilename());
        String response = resumeMatchService.matchResumeToJobs(resume);
        return ResponseEntity.ok(new ChatResponse(response));
    }
}