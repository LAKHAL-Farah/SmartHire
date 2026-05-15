package tn.esprit.eventmanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.review.EventReviewDTO;

import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventReview;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.repository.EventReviewRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventReviewServiceImpl implements EventReviewService {

    private final EventReviewRepository reviewRepository;
    private final EventRepository eventRepository;

    @Autowired
    public EventReviewServiceImpl(EventReviewRepository reviewRepository,
                                  EventRepository eventRepository) {
        this.reviewRepository = reviewRepository;
        this.eventRepository = eventRepository;
    }


    private EventReviewDTO mapToDTO(EventReview review) {
        EventReviewDTO dto = new EventReviewDTO();
        dto.setId(review.getId());
        dto.setUserId(review.getUserId());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setReviewedAt(review.getReviewedAt());
        dto.setEventId(review.getEvent().getId());
        return dto;
    }

    // 🔄 Convert DTO → Entity
    private EventReview mapToEntity(EventReviewDTO dto) {
        EventReview review = new EventReview();
        review.setId(dto.getId());
        review.setUserId(dto.getUserId());
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        review.setEvent(event);
        return review;
    }

    @Override
    public EventReviewDTO addReview(EventReviewDTO dto) {

        if (reviewRepository.existsByUserIdAndEventId(
                dto.getUserId(),
                dto.getEventId())) {
            throw new RuntimeException("User already reviewed this event");
        }

        EventReview review = mapToEntity(dto);
        review.setReviewedAt(LocalDateTime.now());

        return mapToDTO(reviewRepository.save(review));
    }

    @Override
    public List<EventReviewDTO> getAllReviews() {
        return reviewRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventReviewDTO getReviewById(Long id) {
        return reviewRepository.findById(id)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Override
    public List<EventReviewDTO> getReviewByEvent(Long eventId) {
        return reviewRepository.findByEventId(eventId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventReviewDTO updateReview(Long id, EventReviewDTO dto) {

        EventReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        Event eventId = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));



        review.setRating(dto.getRating());
        review.setComment(dto.getComment());
        review.setEvent(eventId);
        review.setUserId(dto.getUserId());


        return mapToDTO(reviewRepository.save(review));
    }

    @Override
    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }
    @Override
    public Event getEventByReview(Long reviewId) {

        EventReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        return review.getEvent();
    }
}