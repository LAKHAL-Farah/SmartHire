package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class GitHubProfileMapper {

    @Autowired
    protected GitHubRepoMapper gitHubRepoMapper;

    public GitHubProfileResponse toResponse(GitHubProfile profile, List<GitHubRepository> repositories) {
        return new GitHubProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getGithubUsername(),
                profile.getProfileUrl(),
                profile.getOverallScore(),
                profile.getRepoCount(),
                profile.getTopLanguages(),
                profile.getProfileReadmeScore(),
                profile.getFeedback(),
                profile.getAuditStatus(),
                profile.getAuditErrorMessage(),
                profile.getCreatedAt(),
                profile.getAnalyzedAt(),
                gitHubRepoMapper.toResponseList(repositories)
        );

    }
}
