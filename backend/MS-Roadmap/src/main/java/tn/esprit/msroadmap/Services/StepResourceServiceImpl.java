package tn.esprit.msroadmap.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.msroadmap.DTO.request.StepResourceDto;
import tn.esprit.msroadmap.Entities.RoadmapNode;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Entities.StepResource;
import tn.esprit.msroadmap.Enums.ResourceProvider;
import tn.esprit.msroadmap.Enums.ResourceType;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Repositories.RoadmapNodeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.Repositories.StepResourceRepository;
import tn.esprit.msroadmap.ServicesImpl.IStepResourceService;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StepResourceServiceImpl implements IStepResourceService {

    private final StepResourceRepository repository;
    private final RoadmapStepRepository stepRepository;
    private final RoadmapNodeRepository nodeRepository;
    private final WebClient webClient;

    @Value("${udemy.api.key:}")
    private String udemyApiKey;

    @Value("${coursera.api.key:}")
    private String courseraApiKey;

    @Value("${youtube.api.key:}")
    private String youtubeApiKey;

    public StepResourceServiceImpl(
            StepResourceRepository repository,
            RoadmapStepRepository stepRepository,
            RoadmapNodeRepository nodeRepository,
            @Qualifier("genericWebClient") WebClient webClient) {
        this.repository = repository;
        this.stepRepository = stepRepository;
        this.nodeRepository = nodeRepository;
        this.webClient = webClient;
    }

    @Override
    public List<StepResource> getResourcesByStepId(Long stepId) {
        RoadmapStep resolved = resolveStep(stepId);
        return repository.findByStepId(resolved.getId());
    }

    @Override
    public List<StepResource> getResourcesByStepIdAndType(Long stepId, String type) {
        RoadmapStep resolved = resolveStep(stepId);
        return repository.findByStepIdAndType(resolved.getId(), parseRequiredResourceType(type));
    }

    @Override
    public List<StepResource> searchResources(String topic, String provider, String type) {
        if (topic == null || topic.isBlank()) {
            throw new BusinessException("topic is required");
        }

        List<StepResource> results = new ArrayList<>();
        ResourceProvider parsedProvider = parseOptionalProvider(provider);
        ResourceType targetType = parseOptionalResourceType(type);

        if (parsedProvider == null) {
            results.addAll(searchUdemy(topic));
            results.addAll(searchCoursera(topic));
            results.addAll(searchYouTube(topic));
        } else {
            switch (parsedProvider) {
                case UDEMY -> results.addAll(searchUdemy(topic));
                case COURSERA -> results.addAll(searchCoursera(topic));
                case YOUTUBE -> results.addAll(searchYouTube(topic));
                default -> throw new BusinessException("Unsupported provider: " + provider);
            }
        }

        if (targetType != null) {
            results = results.stream()
                    .filter(r -> r.getType() == targetType)
                    .collect(Collectors.toList());
        }

        return results;
    }

    private List<StepResource> searchUdemy(String topic) {
        if (udemyApiKey == null || udemyApiKey.isBlank()) {
            return List.of();
        }

        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("www.udemy.com")
                            .path("/api-2.0/courses/")
                            .queryParam("search", topic)
                            .queryParam("page_size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + udemyApiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(12))
                    .onErrorReturn(Map.of())
                    .block();

            if (response == null || !(response.get("results") instanceof List<?> list)) {
                return List.of();
            }

            List<StepResource> resources = new ArrayList<>();
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> item)) {
                    continue;
                }
                String title = asString(item.get("title"));
                String url = asString(item.get("url"));
                if (url != null && url.startsWith("/")) {
                    url = "https://www.udemy.com" + url;
                }
                StepResource r = new StepResource();
                r.setType(ResourceType.COURSE);
                r.setProvider(ResourceProvider.UDEMY);
                r.setTitle(title);
                r.setUrl(url != null ? url : "https://www.udemy.com/courses/search/?q=" + encode(topic));
                r.setRating(toDouble(item.get("rating")));
                r.setDurationHours(null);
                r.setPrice(extractUdemyPrice(item));
                r.setFree(r.getPrice() != null && r.getPrice() == 0.0);
                r.setExternalId(asString(item.get("id")));
                resources.add(r);
            }
            return resources;
        } catch (Exception ex) {
            log.warn("Udemy search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<StepResource> searchCoursera(String topic) {
        if (courseraApiKey == null || courseraApiKey.isBlank()) {
            return List.of();
        }

        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.coursera.org")
                            .path("/api/courses.v1")
                            .queryParam("q", "search")
                            .queryParam("query", topic)
                            .queryParam("limit", 10)
                            .build())
                    .header("X-API-Key", courseraApiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(12))
                    .onErrorReturn(Map.of())
                    .block();

            if (response == null || !(response.get("elements") instanceof List<?> list)) {
                return List.of();
            }

            List<StepResource> resources = new ArrayList<>();
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> item)) {
                    continue;
                }
                String name = asString(item.get("name"));
                String slug = asString(item.get("slug"));

                StepResource r = new StepResource();
                r.setType(ResourceType.COURSE);
                r.setProvider(ResourceProvider.COURSERA);
                r.setTitle(name);
                r.setUrl(slug != null && !slug.isBlank()
                        ? "https://www.coursera.org/learn/" + slug
                        : "https://www.coursera.org/search?query=" + encode(topic));
                r.setRating(null);
                r.setDurationHours(null);
                r.setPrice(null);
                r.setFree(false);
                r.setExternalId(asString(item.get("id")));
                resources.add(r);
            }
            return resources;
        } catch (Exception ex) {
            log.warn("Coursera search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<StepResource> searchYouTube(String topic) {
        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            return List.of();
        }

        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("www.googleapis.com")
                            .path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("q", topic)
                            .queryParam("type", "video")
                            .queryParam("maxResults", 10)
                            .queryParam("key", youtubeApiKey)
                            .build())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(12))
                    .onErrorReturn(Map.of())
                    .block();

            if (response == null || !(response.get("items") instanceof List<?> list)) {
                return List.of();
            }

            List<StepResource> resources = new ArrayList<>();
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> item)) {
                    continue;
                }

                String videoId = null;
                Object idObj = item.get("id");
                if (idObj instanceof Map<?, ?> idMap) {
                    videoId = asString(idMap.get("videoId"));
                }

                String title = null;
                Object snObj = item.get("snippet");
                if (snObj instanceof Map<?, ?> snippet) {
                    title = asString(snippet.get("title"));
                }

                if (videoId == null || videoId.isBlank()) {
                    continue;
                }

                StepResource r = new StepResource();
                r.setType(ResourceType.VIDEO);
                r.setProvider(ResourceProvider.YOUTUBE);
                r.setTitle(title);
                r.setUrl("https://www.youtube.com/watch?v=" + videoId);
                r.setRating(null);
                r.setDurationHours(null);
                r.setPrice(0.0);
                r.setFree(true);
                r.setExternalId(videoId);
                resources.add(r);
            }
            return resources;
        } catch (Exception ex) {
            log.warn("YouTube search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public StepResource addResourceToStep(Long stepId, StepResourceDto dto) {
        var step = resolveStep(stepId);
        StepResource r = new StepResource();
        r.setStep(step);
        r.setType(parseRequiredResourceType(dto.type()));
        r.setProvider(parseRequiredProvider(dto.provider()));
        r.setTitle(dto.title());
        r.setUrl(dto.url());
        r.setRating(dto.rating());
        r.setDurationHours(dto.durationHours());
        r.setPrice(dto.price());
        r.setFree(dto.isFree() != null && dto.isFree());
        r.setExternalId(dto.externalId());
        return repository.save(r);
    }

    @Override
    public void deleteResource(Long resourceId) {
        if (!repository.existsById(resourceId)) throw new ResourceNotFoundException("Resource not found");
        repository.deleteById(resourceId);
    }

    @Override
    public void syncResourcesForStep(Long stepId) {
        RoadmapStep step = resolveStep(stepId);

        List<StepResource> resources = searchResources(step.getTitle(), null, null);

        int count = 0;
        for (StepResource resource : resources) {
            if (count >= 5) {
                break;
            }

            String externalId = resource.getExternalId();
            if (externalId != null && repository.existsByStepIdAndExternalId(step.getId(), externalId)) {
                continue;
            }

            resource.setStep(step);
            repository.save(resource);
            count++;
        }
    }

    private RoadmapStep resolveStep(Long stepIdOrNodeId) {
        // Accepts either a real step id or a node id that maps to an existing stepOrder; never auto-creates rows.
        if (stepIdOrNodeId == null || stepIdOrNodeId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }

        RoadmapStep direct = stepRepository.findById(stepIdOrNodeId).orElse(null);
        if (direct != null) {
            return direct;
        }

        RoadmapNode node = nodeRepository.findById(stepIdOrNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found: " + stepIdOrNodeId));

        RoadmapStep mapped = stepRepository.findByRoadmapIdAndStepOrder(
                node.getRoadmap().getId(),
                node.getStepOrder()
        );
        if (mapped == null) {
            throw new BusinessException("No roadmap step mapped to node " + stepIdOrNodeId + ". Sync roadmap steps before requesting resources.");
        }
        return mapped;
    }

    private ResourceProvider parseOptionalProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        try {
            return ResourceProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException("Unsupported provider: " + provider);
        }
    }

    private ResourceProvider parseRequiredProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException("provider is required");
        }
        return parseOptionalProvider(provider);
    }

    private ResourceType parseOptionalResourceType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException("Unsupported resource type: " + type);
        }
    }

    private ResourceType parseRequiredResourceType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("type is required");
        }
        return parseOptionalResourceType(type);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double extractUdemyPrice(Map<?, ?> item) {
        Object detailObj = item.get("price_detail");
        if (detailObj instanceof Map<?, ?> priceDetail) {
            Object amount = priceDetail.get("amount");
            Double parsed = toDouble(amount);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }
}
