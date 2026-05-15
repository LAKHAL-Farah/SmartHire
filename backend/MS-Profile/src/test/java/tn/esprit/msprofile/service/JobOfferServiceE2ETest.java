package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.request.JobOfferRequest;
import tn.esprit.msprofile.dto.response.JobOfferResponse;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class JobOfferServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private JobOfferService jobOfferService;

    @Autowired
    private JobOfferRepository jobOfferRepository;

    @Autowired
    private CandidateCVRepository candidateCVRepository;

    @Autowired
    private CVVersionRepository cvVersionRepository;

    @Test
    void createJobOffer_stateValidRequest_expectedPersistedAndKeywordsExtracted() {
        JobOfferRequest request = new JobOfferRequest(
                TestConstants.USER_ID,
                "Senior Java Engineer",
                "SmartHire",
                TestConstants.JOB_OFFER_DESCRIPTION,
                null,
                "https://example.com/jobs/123",
                Instant.now()
        );

        JobOfferResponse created = jobOfferService.createJobOffer(TestConstants.USER_ID, request);

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Senior Java Engineer");

        await().atMost(10, SECONDS).untilAsserted(() -> {
            JobOffer persisted = jobOfferRepository.findById(created.id()).orElseThrow();
            assertThat(persisted.getExtractedKeywords()).isNotBlank();
            assertThat(persisted.getExtractedKeywords()).contains("java");
        });
    }

    @Test
    void getJobOffersForUser_stateMultipleOffers_expectedOrderedByNewestFirst() {
        JobOfferRequest first = new JobOfferRequest(
                TestConstants.USER_ID,
                "Offer A",
                "Company A",
                "Java Spring experience required",
                null,
                null,
                Instant.now().minusSeconds(120)
        );
        JobOfferRequest second = new JobOfferRequest(
                TestConstants.USER_ID,
                "Offer B",
                "Company B",
                "Docker Kubernetes and CI/CD",
                null,
                null,
                Instant.now()
        );

        jobOfferService.createJobOffer(TestConstants.USER_ID, first);
        jobOfferService.createJobOffer(TestConstants.USER_ID, second);

        List<JobOfferResponse> offers = jobOfferService.getJobOffersForUser(TestConstants.USER_ID);

        assertThat(offers).hasSize(2);
        assertThat(offers.get(0).title()).isEqualTo("Offer B");
        assertThat(offers.get(1).title()).isEqualTo("Offer A");
    }

    @Test
    void deleteJobOffer_stateOfferHasVersions_expectedOfferAndDependentVersionsRemoved() {
        CandidateCV cv = persistCompletedCv();
        JobOffer offer = persistJobOffer("Offer to Delete");
        persistCvVersion(cv, offer);

        assertThat(cvVersionRepository.findByJobOfferId(offer.getId())).hasSize(1);

        jobOfferService.deleteJobOffer(offer.getId(), TestConstants.USER_ID);

        assertThat(jobOfferRepository.findById(offer.getId())).isEmpty();
        assertThat(cvVersionRepository.findByJobOfferId(offer.getId())).isEmpty();
    }

    @Test
    void getJobOfferById_stateDifferentOwner_expectedResourceNotFoundException() {
        JobOffer offer = persistJobOffer("Offer Owned By User");

        assertThatThrownBy(() -> jobOfferService.getJobOfferById(offer.getId(), TestConstants.ALT_USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("JobOffer not found");
    }

    private CandidateCV persistCompletedCv() {
        CandidateCV cv = new CandidateCV();
        cv.setUserId(TestConstants.USER_ID);
        cv.setOriginalFileUrl("temp/cv-uploads/john.pdf");
        cv.setOriginalFileName("john.pdf");
        cv.setFileFormat(FileFormat.PDF);
        cv.setParsedContent("{\"rawText\":\"java spring docker\"}");
        cv.setParseStatus(ProcessingStatus.COMPLETED);
        cv.setAtsScore(78);
        cv.setIsActive(true);
        cv.setUploadedAt(Instant.now());
        cv.setUpdatedAt(Instant.now());
        return candidateCVRepository.save(cv);
    }

    private JobOffer persistJobOffer(String title) {
        JobOffer offer = new JobOffer();
        offer.setUserId(TestConstants.USER_ID);
        offer.setTitle(title);
        offer.setCompany("SmartHire");
        offer.setRawDescription(TestConstants.JOB_OFFER_DESCRIPTION);
        offer.setExtractedKeywords("[\"java\",\"spring\"]");
        offer.setSourceUrl("https://example.com/jobs/" + UUID.randomUUID());
        offer.setCreatedAt(Instant.now());
        return jobOfferRepository.save(offer);
    }

    private void persistCvVersion(CandidateCV cv, JobOffer offer) {
        CVVersion version = new CVVersion();
        version.setCv(cv);
        version.setJobOffer(offer);
        version.setVersionType(CVVersionType.TAILORED);
        version.setTailoredContent("tailored content");
        version.setProcessingStatus(ProcessingStatus.COMPLETED);
        version.setGeneratedByAI(true);
        version.setAtsScore(85);
        version.setGeneratedAt(Instant.now());
        cvVersionRepository.save(version);
    }
}
