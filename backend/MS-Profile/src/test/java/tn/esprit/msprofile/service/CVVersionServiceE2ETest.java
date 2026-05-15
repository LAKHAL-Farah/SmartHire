package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.CVVersionResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.AuditLogRepository;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CVVersionServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private CVVersionService cvVersionService;

    @Autowired
    private CandidateCVRepository candidateCVRepository;

    @Autowired
    private JobOfferRepository jobOfferRepository;

    @Autowired
    private CVVersionRepository cvVersionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void tailorCvForJobOffer_stateValidInputs_expectedVersionPersistedAndAuditLogged() {
        CandidateCV cv = persistCompletedCv();
        JobOffer offer = persistJobOffer();

        CVVersionResponse response = cvVersionService.tailorCvForJobOffer(cv.getId(), offer.getId());

        assertThat(response.processingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(response.versionType()).isEqualTo(CVVersionType.TAILORED);
        assertThat(response.keywordMatchRate()).isNotNull();
        assertThat(response.tailoredContent()).contains("ATS Tailored CV");

        CVVersion persisted = cvVersionRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getJobOffer().getId()).isEqualTo(offer.getId());

        List<AuditLog> logs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.CV_TAILOR);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(logs.get(0).getTokensUsed()).isGreaterThan(0);
    }

    @Test
    void generateGenericOptimizedVersion_stateValidCv_expectedOptimizedVersionCreated() {
        CandidateCV cv = persistCompletedCv();

        CVVersionResponse response = cvVersionService.generateGenericOptimizedVersion(cv.getId());

        assertThat(response.versionType()).isEqualTo(CVVersionType.GENERIC_OPTIMIZED);
        assertThat(response.diffContent()).isNotBlank();
        assertThat(response.atsScore()).isNotNull();

        List<CVVersion> versions = cvVersionRepository.findByCvId(cv.getId());
        assertThat(versions).hasSize(1);
    }

    @Test
    void exportVersionAsPdf_stateExistingVersion_expectedPdfBytesAndStoredPath() {
        CandidateCV cv = persistCompletedCv();
        CVVersionResponse version = cvVersionService.generateGenericOptimizedVersion(cv.getId());

        byte[] pdf = cvVersionService.exportVersionAsPdf(version.id());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);

        CVVersion persisted = cvVersionRepository.findById(version.id()).orElseThrow();
        assertThat(persisted.getExportedFileUrl()).isNotBlank();
        assertThat(persisted.getExportedFileUrl()).contains(".pdf");
    }

    @Test
    void tailorCvForJobOffer_stateMissingJobOffer_expectedResourceNotFoundException() {
        CandidateCV cv = persistCompletedCv();

        assertThatThrownBy(() -> cvVersionService.tailorCvForJobOffer(cv.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("JobOffer not found");
    }

    private CandidateCV persistCompletedCv() {
        CandidateCV cv = new CandidateCV();
        cv.setUserId(TestConstants.USER_ID);
        cv.setOriginalFileUrl("temp/cv-uploads/test-cv.pdf");
        cv.setOriginalFileName("test-cv.pdf");
        cv.setFileFormat(FileFormat.PDF);
        cv.setParsedContent("{\"rawText\":\"Java Spring Docker Kubernetes Testing\",\"keywords\":[\"java\",\"spring\"]}");
        cv.setParseStatus(ProcessingStatus.COMPLETED);
        cv.setAtsScore(82);
        cv.setIsActive(true);
        cv.setUploadedAt(Instant.now());
        cv.setUpdatedAt(Instant.now());
        return candidateCVRepository.save(cv);
    }

    private JobOffer persistJobOffer() {
        JobOffer offer = new JobOffer();
        offer.setUserId(TestConstants.USER_ID);
        offer.setTitle("Backend Java Engineer");
        offer.setCompany("SmartHire");
        offer.setRawDescription(TestConstants.JOB_OFFER_DESCRIPTION);
        offer.setExtractedKeywords("[\"java\",\"spring\",\"docker\",\"kubernetes\"]");
        offer.setSourceUrl("https://example.com/jobs/backend-java");
        offer.setCreatedAt(Instant.now());
        return jobOfferRepository.save(offer);
    }
}
