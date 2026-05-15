package tn.esprit.msprofile.testsupport;

import java.util.UUID;

public final class TestConstants {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID ALT_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final String CV_PDF_FIXTURE = "fixtures/sample-cv.pdf";
    public static final String CV_DOCX_FIXTURE = "fixtures/sample-cv.docx";
    public static final String JOB_OFFER_DESCRIPTION = "We need a Java Spring engineer with Docker, Kubernetes, CI/CD and testing experience.";
    public static final String LINKEDIN_PROFILE_PATH = "/linkedin/profile/jane-doe";
    public static final String GITHUB_USERNAME = "jane-doe";

    private TestConstants() {
    }
}
