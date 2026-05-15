package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.DTO.response.UserRoadmapResponse;
import tn.esprit.msroadmap.Entities.UserRoadmap;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Mapper.RoadmapMapper;
import tn.esprit.msroadmap.Repositories.UserRoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IUserRoadmapService;

import java.time.LocalDateTime;




@Service
@AllArgsConstructor
public class UserRoadmapServiceImpl implements IUserRoadmapService {

    private final UserRoadmapRepository userRoadmapRepository;
    private final RoadmapMapper roadmapMapper;

    @Override
    public UserRoadmapResponse startRoadmap(Long userId, Long roadmapId) {
        var existing = userRoadmapRepository.findByUserIdAndRoadmapId(userId, roadmapId);
        if (existing.isPresent()) {
            throw new BusinessException("User already started this roadmap");
        }

        UserRoadmap userRoadmap = new UserRoadmap();
        userRoadmap.setUserId(userId);
        userRoadmap.setRoadmapId(roadmapId);
        userRoadmap.setProgressPercent(0);
        userRoadmap.setStartedAt(LocalDateTime.now());

        return roadmapMapper.toUserRoadmapResponse(userRoadmapRepository.save(userRoadmap));
    }

    @Override
    public UserRoadmapResponse getUserProgress(Long userId, Long roadmapId) {
        UserRoadmap progress = userRoadmapRepository.findByUserIdAndRoadmapId(userId, roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("No progress record found for this user/roadmap"));
        return roadmapMapper.toUserRoadmapResponse(progress);
    }

    @Override
    public void deleteUserRoadmap(Long id) {
        if (!userRoadmapRepository.existsById(id)) {
            throw new ResourceNotFoundException("User Roadmap not found with ID: " + id);
        }
        userRoadmapRepository.deleteById(id);
    }
}