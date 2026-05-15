package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.AuditLogRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;
import tn.esprit.msprofile.testsupport.TestFixtureLoader;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest
class CandidateCVServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private CandidateCVService candidateCVService;

    @Autowired
    private CandidateCVRepository candidateCVRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void uploadAndParseCv_stateValidPdf_expectedCvParsedScoredAndLogged() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "candidate.pdf",
                "application/pdf",
                TestFixtureLoader.readBytes(TestConstants.CV_PDF_FIXTURE)
        );

        var created = candidateCVService.uploadAndParseCv(TestConstants.USER_ID, multipartFile);
        assertThat(created.parseStatus()).isEqualTo(ProcessingStatus.PENDING);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            CandidateCV persisted = candidateCVRepository.findById(created.id()).orElseThrow();
            assertThat(persisted.getParseStatus()).isIn(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);
            assertThat(persisted.getAtsScore()).isNotNull();
        });

        CandidateCV persisted = candidateCVRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getIsActive()).isTrue();
        assertThat(persisted.getParsedContent()).isNotBlank();

        List<AuditLog> logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(TestConstants.USER_ID);
        assertThat(logs)
                .extracting(AuditLog::getOperationType)
                .contains(OperationType.CV_PARSE);
        assertThat(logs)
                .extracting(AuditLog::getStatus)
                .containsAnyOf(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);
        assertThat(logs)
                .extracting(AuditLog::getTokensUsed)
                .allMatch(tokens -> tokens != null && tokens > 0);
    }

    @Test
    void uploadAndParseCv_stateSecondUpload_expectedOldCvInactiveAndLatestActive() {
        MockMultipartFile first = new MockMultipartFile(
                "file",
                "first.pdf",
                "application/pdf",
                TestFixtureLoader.readBytes(TestConstants.CV_PDF_FIXTURE)
        );
        MockMultipartFile second = new MockMultipartFile(
                "file",
                "second.pdf",
                "application/pdf",
                TestFixtureLoader.readBytes(TestConstants.CV_DOCX_FIXTURE)
        );

        var firstResponse = candidateCVService.uploadAndParseCv(TestConstants.USER_ID, first);
        var secondResponse = candidateCVService.uploadAndParseCv(TestConstants.USER_ID, second);

        await().atMost(15, SECONDS).untilAsserted(() -> {
                        List<CandidateCV> userCvs = candidateCVRepository.findByUserId(TestConstants.USER_ID);
                        assertThat(userCvs).hasSize(2);
                        assertThat(userCvs)
                                        .extracting(CandidateCV::getId)
                                        .contains(firstResponse.id(), secondResponse.id());
        });

        CandidateCV secondCv = candidateCVRepository.findById(secondResponse.id()).orElseThrow();

        assertThat(secondCv.getIsActive()).isTrue();
    }

    @Test
    void deactivateCv_stateOwnedCv_expectedInactiveAndNoActiveCvLeft() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "to-deactivate.pdf",
                "application/pdf",
                TestFixtureLoader.readBytes(TestConstants.CV_PDF_FIXTURE)
        );
        var created = candidateCVService.uploadAndParseCv(TestConstants.USER_ID, file);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            CandidateCV cv = candidateCVRepository.findById(created.id()).orElseThrow();
            assertThat(cv.getParseStatus()).isIn(ProcessingStatus.COMPLETED, ProcessingStatus.FAILED);
        });

        candidateCVService.deactivateCv(created.id(), TestConstants.USER_ID);

        CandidateCV afterDeactivate = candidateCVRepository.findById(created.id()).orElseThrow();
        assertThat(afterDeactivate.getIsActive()).isFalse();

        assertThatThrownBy(() -> candidateCVService.getActiveCvForUser(TestConstants.USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadAndParseCv_stateEmptyFile_expectedIllegalArgumentException() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> candidateCVService.uploadAndParseCv(TestConstants.USER_ID, empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Uploaded CV file is empty");
    }
}
