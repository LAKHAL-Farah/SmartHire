package tn.esprit.msprofile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.CVVersionResponse;
import tn.esprit.msprofile.dto.response.CandidateCVResponse;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.CVVersionService;
import tn.esprit.msprofile.service.CandidateCVService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = tn.esprit.msprofile.controller.m4.CandidateCvController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidateCVControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandidateCVService candidateCVService;

    @MockBean
    private CVVersionService cvVersionService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void uploadCv_stateValidFile_expectedCreated() throws Exception {
        CandidateCVResponse response = sampleCvResponse();
        when(candidateCVService.uploadAndParseCv(eq(USER_ID), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cv.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "sample cv content".getBytes()
        );

        mockMvc.perform(multipart("/api/cv/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.parseStatus").value("PENDING"));
    }

    @Test
    void uploadCv_stateMissingFile_expectedBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/cv/upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCvsForStaticUser_stateServiceReturnsList_expectedOk() throws Exception {
        when(candidateCVService.getAllCvsForUser(eq(USER_ID))).thenReturn(List.of(sampleCvResponse()));

        mockMvc.perform(get("/api/cv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()));
    }

    @Test
    void getCvsForStaticUser_stateServiceThrowsException_expectedInternalServerError() throws Exception {
        when(candidateCVService.getAllCvsForUser(eq(USER_ID))).thenThrow(new IllegalStateException("list failure"));

        mockMvc.perform(get("/api/cv"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("list failure"));
    }

    @Test
    void getCvById_stateCvExists_expectedOk() throws Exception {
        CandidateCVResponse response = sampleCvResponse();
        when(candidateCVService.getCvById(eq(response.id()), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/cv/{cvId}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    void getCvById_stateCvMissing_expectedNotFound() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(candidateCVService.getCvById(eq(cvId), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("CandidateCV not found"));

        mockMvc.perform(get("/api/cv/{cvId}", cvId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("CandidateCV not found"));
    }

    @Test
    void getCvScore_stateCvExists_expectedOk() throws Exception {
        CandidateCVResponse response = sampleCvResponse();
        when(candidateCVService.getCvById(eq(response.id()), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/cv/{cvId}/score", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value(response.id().toString()))
                .andExpect(jsonPath("$.atsScore").value(82));
    }

    @Test
    void getCvScore_stateCvMissing_expectedNotFound() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(candidateCVService.getCvById(eq(cvId), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("missing cv"));

        mockMvc.perform(get("/api/cv/{cvId}/score", cvId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("missing cv"));
    }

    @Test
    void tailorCv_stateValidRequest_expectedOk() throws Exception {
        UUID cvId = UUID.randomUUID();
        UUID jobOfferId = UUID.randomUUID();
        CVVersionResponse version = sampleVersionResponse(cvId, jobOfferId);
        when(cvVersionService.tailorCvForJobOffer(eq(cvId), eq(jobOfferId))).thenReturn(version);

        mockMvc.perform(post("/api/cv/{cvId}/tailor", cvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobOfferId\":\"" + jobOfferId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(version.id().toString()))
                .andExpect(jsonPath("$.versionType").value("TAILORED"));
    }

    @Test
    void tailorCv_stateMissingJobOfferId_expectedBadRequest() throws Exception {
        UUID cvId = UUID.randomUUID();

        mockMvc.perform(post("/api/cv/{cvId}/tailor", cvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request payload"));
    }

    @Test
    void getCvVersions_stateVersionsExist_expectedOk() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(cvVersionService.getVersionsForCv(eq(cvId))).thenReturn(List.of(sampleVersionResponse(cvId, UUID.randomUUID())));

        mockMvc.perform(get("/api/cv/{cvId}/versions", cvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cvId").value(cvId.toString()));
    }

    @Test
    void getCvVersions_stateServiceThrowsException_expectedInternalServerError() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(cvVersionService.getVersionsForCv(eq(cvId))).thenThrow(new IllegalStateException("versions failure"));

        mockMvc.perform(get("/api/cv/{cvId}/versions", cvId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("versions failure"));
    }

    @Test
    void getVersionById_stateVersionExists_expectedOk() throws Exception {
        CVVersionResponse response = sampleVersionResponse(UUID.randomUUID(), UUID.randomUUID());
        when(cvVersionService.getVersionById(eq(response.id()))).thenReturn(response);

        mockMvc.perform(get("/api/cv/versions/{versionId}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    void getVersionById_stateVersionMissing_expectedNotFound() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(cvVersionService.getVersionById(eq(versionId)))
                .thenThrow(new ResourceNotFoundException("version missing"));

        mockMvc.perform(get("/api/cv/versions/{versionId}", versionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("version missing"));
    }

    @Test
    void exportVersion_stateVersionExists_expectedPdfResponse() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(cvVersionService.exportVersionAsPdf(eq(versionId))).thenReturn("%PDF-test".getBytes());

        mockMvc.perform(get("/api/cv/versions/{versionId}/export", versionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=cv-version-" + versionId + ".pdf"));
    }

    @Test
    void exportVersion_stateServiceThrowsException_expectedInternalServerError() throws Exception {
        UUID versionId = UUID.randomUUID();
        when(cvVersionService.exportVersionAsPdf(eq(versionId))).thenThrow(new IllegalStateException("pdf failure"));

        mockMvc.perform(get("/api/cv/versions/{versionId}/export", versionId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("pdf failure"));
    }

    @Test
    void deactivateCv_stateCvExists_expectedNoContent() throws Exception {
        UUID cvId = UUID.randomUUID();

        mockMvc.perform(delete("/api/cv/{cvId}", cvId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateCv_stateCvMissing_expectedNotFound() throws Exception {
        UUID cvId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("cv not found for delete"))
                .when(candidateCVService)
                .deactivateCv(eq(cvId), eq(USER_ID));

        mockMvc.perform(delete("/api/cv/{cvId}", cvId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("cv not found for delete"));
    }

    private CandidateCVResponse sampleCvResponse() {
        UUID cvId = UUID.randomUUID();
        Instant now = Instant.now();
        return new CandidateCVResponse(
                cvId,
                USER_ID,
                "temp/cv-uploads/sample.pdf",
                "sample.pdf",
                FileFormat.PDF,
                "{\"rawText\":\"java spring\"}",
                ProcessingStatus.PENDING,
                null,
                82,
                true,
                now,
                now
        );
    }

    private CVVersionResponse sampleVersionResponse(UUID cvId, UUID jobOfferId) {
        return new CVVersionResponse(
                UUID.randomUUID(),
                cvId,
                jobOfferId,
                CVVersionType.TAILORED,
                "tailored text",
                88,
                new BigDecimal("91.50"),
                "{\"addedKeywords\":[\"spring\"]}",
                true,
                ProcessingStatus.COMPLETED,
                "temp/cv-exports/version.pdf",
                Instant.now()
        );
    }
}
