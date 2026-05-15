package tn.esprit.eventmanagement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.review.EventReviewDTO;

import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.service.EventReviewService;
import tn.esprit.eventmanagement.service.EventReviewServiceImpl;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin
public class EventReviewController {

    @Autowired
    private EventReviewServiceImpl reviewService;

    public EventReviewController(EventReviewServiceImpl reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public EventReviewDTO addReview(@RequestBody EventReviewDTO dto) {
        return reviewService.addReview(dto);
    }

    @GetMapping
    public List<EventReviewDTO> getAllReviews() {
        return reviewService.getAllReviews();
    }

    @GetMapping("/{id}")
    public EventReviewDTO getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id);
    }

    @GetMapping("/event/{eventId}")
    public List<EventReviewDTO> getReviewByEvent(@PathVariable Long eventId) {
        return reviewService.getReviewByEvent(eventId);
    }

    @PutMapping("/update/{id}")
    public EventReviewDTO updateReview(@PathVariable Long id,
                                       @RequestBody EventReviewDTO dto) {
        return reviewService.updateReview(id, dto);
    }

    @DeleteMapping("/delete/{id}")
    public void deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
    }
    @GetMapping("/events/{reviewId}")
    public Event getEventByReview(@PathVariable Long reviewId) {
        return reviewService.getEventByReview(reviewId);
    }
}