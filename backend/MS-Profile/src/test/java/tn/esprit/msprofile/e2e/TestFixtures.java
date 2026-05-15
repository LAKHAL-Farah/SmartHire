package tn.esprit.msprofile.e2e;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static MockMultipartFile minimalPdfCv() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/MediaBox[0 0 612 792]>>endobj\n"
                + "xref\n0 4\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n9\n%%EOF";
        return new MockMultipartFile(
                "file",
                "test-cv.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdf.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String mockParsedCvJson() {
        return """
                {
                  "name": "Alice Martin",
                  "email": "alice@example.com",
                  "phone": "+1-555-0100",
                  "summary": "Experienced full-stack engineer with 5 years in Java and Angular.",
                  "skills": ["Java", "Spring Boot", "Angular", "REST API", "MySQL", "Docker", "CI/CD", "Git"],
                  "experience": [{
                    "title": "Senior Software Engineer",
                    "company": "TechCorp",
                    "duration": "2020–2025",
                    "description": "Built REST APIs with Spring Boot and Angular frontends."
                  }],
                  "education": [{
                    "degree": "BSc Computer Science",
                    "institution": "State University",
                    "year": "2019"
                  }]
                }
                """;
    }

    public static String mockJobKeywordsJson() {
        return "[\"Java\",\"Spring Boot\",\"Microservices\",\"Docker\",\"Kubernetes\",\"REST API\",\"Angular\",\"MySQL\",\"CI/CD\",\"Agile\"]";
    }

    public static String mockTailoredCvJson() {
        return """
                {
                  "name": "Alice Martin",
                  "email": "alice@example.com",
                  "phone": "+1-555-0100",
                  "summary": "Experienced full-stack engineer with 5 years in Java, Microservices, and Kubernetes delivery.",
                  "skills": ["Java", "Spring Boot", "Microservices", "Docker", "Kubernetes", "REST API", "Angular", "MySQL", "CI/CD", "Agile"],
                  "experience": [{
                    "title": "Senior Software Engineer",
                    "company": "TechCorp",
                    "duration": "2020–2025",
                    "description": "Built REST APIs using Spring Boot and delivered Microservices architecture on Kubernetes."
                  }],
                  "education": [{
                    "degree": "BSc Computer Science",
                    "institution": "State University",
                    "year": "2019"
                  }]
                }
                """;
    }

    public static String createJobOfferRequestJson() {
        return """
                {
                  "title": "Senior Full-Stack Engineer",
                  "company": "Acme Corp",
                  "rawDescription": "We are looking for a Senior Full-Stack Engineer proficient in Java, Spring Boot, Microservices, Docker, Kubernetes, REST API design, Angular, MySQL, CI/CD pipelines, and Agile methodology.",
                  "sourceUrl": "https://acme.com/jobs/123"
                }
                """;
    }
}
