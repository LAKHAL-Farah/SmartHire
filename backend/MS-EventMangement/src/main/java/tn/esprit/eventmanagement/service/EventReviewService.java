package tn.esprit.eventmanagement.service;


import tn.esprit.eventmanagement.DTO.review.EventReviewDTO;
import tn.esprit.eventmanagement.entities.Event;

import java.util.List;

public interface EventReviewService {


    EventReviewDTO addReview(EventReviewDTO dto);

    List<EventReviewDTO> getAllReviews();

    EventReviewDTO getReviewById(Long id);

    List<EventReviewDTO> getReviewByEvent(Long eventId);

    EventReviewDTO updateReview(Long id, EventReviewDTO dto);

    void deleteReview(Long id);

    Event  getEventByReview(Long reviewId);

}