package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Service;
import tn.esprit.msprofile.config.properties.FileStorageProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PdfExportService {

    private static final DeviceRgb SECTION_COLOR = new DeviceRgb(37, 99, 235);

    private final ObjectMapper objectMapper;
    private final FileStorageProperties fileStorageProperties;

    public PdfExportService(ObjectMapper objectMapper, FileStorageProperties fileStorageProperties) {
        this.objectMapper = objectMapper;
        this.fileStorageProperties = fileStorageProperties;
    }

    public byte[] exportCvToPdf(String tailoredContentJson, String fallbackCvJson, String originalFileName) {
        JsonNode root = mergeCvJson(readJson(tailoredContentJson), readJson(fallbackCvJson));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument, PageSize.A4);
            float margin = 56.7f; // 2cm
            document.setMargins(margin, margin, margin, margin);

            String name = cleanText(root.path("name").asText("Candidate"));
            String email = cleanText(root.path("email").asText(""));
            String phone = cleanText(root.path("phone").asText(""));

            document.add(new Paragraph(name).setBold().setFontSize(22).setMarginBottom(4));

            List<String> contactParts = new ArrayList<>();
            if (!email.isBlank()) {
                contactParts.add(email);
            }
            if (!phone.isBlank()) {
                contactParts.add(phone);
            }
            if (!contactParts.isEmpty()) {
                document.add(new Paragraph(String.join(" | ", contactParts))
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginTop(0)
                        .setMarginBottom(10));
            }

            document.add(new LineSeparator(new SolidLine()).setMarginBottom(12));

            addSectionLabel(document, "Summary");
            String summary = cleanText(root.path("summary").asText(""));
            if (summary.isBlank()) {
                summary = "No summary provided.";
            }
            document.add(new Paragraph(summary).setFontSize(10).setMarginBottom(12));

            addSectionLabel(document, "Skills");
            List<String> skills = readTextArray(root.path("skills"));
            if (skills.isEmpty()) {
                document.add(new Paragraph("No skills provided.").setFontSize(10).setFontColor(ColorConstants.GRAY).setMarginBottom(12));
            } else {
                for (String skill : skills) {
                    document.add(new Paragraph("- " + cleanText(skill))
                            .setFontSize(10)
                            .setMarginLeft(8)
                            .setMarginTop(0)
                            .setMarginBottom(2));
                }
                document.add(new Paragraph("").setMarginBottom(6));
            }

            addSectionLabel(document, "Experience");
            JsonNode experienceNode = root.path("experience");
            if (!experienceNode.isArray() || experienceNode.isEmpty()) {
                document.add(new Paragraph("No experience details provided.")
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginBottom(12));
            } else {
                for (JsonNode exp : experienceNode) {
                    String title = cleanText(exp.path("title").asText("Role"));
                    String company = cleanText(exp.path("company").asText("Company"));
                    String duration = cleanText(exp.path("duration").asText(""));
                    String desc = exp.path("description").asText("");

                    Paragraph roleLine = new Paragraph(title + " - " + company).setBold().setMarginBottom(1);
                    document.add(roleLine);
                    if (!duration.isBlank()) {
                        document.add(new Paragraph(duration)
                                .setFontSize(10)
                                .setFontColor(ColorConstants.GRAY)
                                .setMarginTop(0)
                                .setMarginBottom(4));
                    }

                    List<String> bullets = splitToBullets(desc);
                    if (bullets.isEmpty()) {
                        document.add(new Paragraph("- No role details provided.")
                                .setFontSize(10)
                                .setMarginLeft(8)
                                .setMarginBottom(12));
                    } else {
                        for (String bullet : bullets) {
                            document.add(new Paragraph(bullet)
                                    .setFontSize(10)
                                    .setMarginLeft(8)
                                    .setMarginTop(0)
                                    .setMarginBottom(2));
                        }
                        document.add(new Paragraph("").setMarginBottom(6));
                    }
                }
            }

            addSectionLabel(document, "Education");
            JsonNode educationNode = root.path("education");
            if (!educationNode.isArray() || educationNode.isEmpty()) {
                document.add(new Paragraph("No education details provided.")
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginBottom(12));
            } else {
                for (JsonNode edu : educationNode) {
                    String degree = cleanText(edu.path("degree").asText("Degree"));
                    String institution = cleanText(edu.path("institution").asText("Institution"));
                    String year = cleanText(edu.path("year").asText(""));

                    Paragraph row = new Paragraph()
                            .add(new Text(degree + " - " + institution).setBold())
                            .add(new Text(year.isBlank() ? "" : "\n" + year).setFontSize(10).setFontColor(ColorConstants.GRAY));
                    document.add(row.setMarginBottom(10));
                }
            }

            // Render any additional sections present in tailored content that are outside the standard schema.
            renderAdditionalSections(document, root);

            if (originalFileName != null && !originalFileName.isBlank()) {
                document.add(new Paragraph("Source: " + originalFileName)
                        .setFontSize(9)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginTop(12)
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            document.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate PDF", e);
        }
    }

    public String savePdfToStorage(byte[] pdfBytes, UUID userId, UUID versionId) {
        String relativePath = userId + "/exports/" + versionId + ".pdf";
        Path fullPath = Paths.get(fileStorageProperties.getBasePath()).toAbsolutePath().normalize().resolve(relativePath).normalize();
        try {
            Path parent = fullPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(fullPath, pdfBytes);
            return relativePath.replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist exported PDF", e);
        }
    }

    private void addSectionLabel(Document document, String label) {
        document.add(new Paragraph(label)
                .setFontSize(11)
                .setBold()
                .setFontColor(SECTION_COLOR)
                .setMarginTop(0)
                .setMarginBottom(4));
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> readTextArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String stripBulletPrefix(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = line.trim();
        while (cleaned.startsWith("-") || cleaned.startsWith("*") || cleaned.startsWith(".") || cleaned.startsWith("•")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }

    private List<String> splitToBullets(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return List.of();
        }

        String normalized = rawDescription.replace("\r", "\n");
        String[] pieces = normalized.split("\\n|(?<=\\.)\\s+|\\s*;\\s*");
        List<String> bullets = new ArrayList<>();
        for (String piece : pieces) {
            String cleaned = cleanText(stripBulletPrefix(piece));
            if (cleaned.isBlank()) {
                continue;
            }
            if (!cleaned.endsWith(".")) {
                cleaned = cleaned + ".";
            }
            bullets.add("- " + cleaned);
            if (bullets.size() == 6) {
                break;
            }
        }
        return bullets;
    }

    private JsonNode mergeCvJson(JsonNode preferredRoot, JsonNode fallbackRoot) {
        JsonNode preferred = preferredRoot != null && preferredRoot.isObject() ? preferredRoot : objectMapper.createObjectNode();
        JsonNode fallback = fallbackRoot != null && fallbackRoot.isObject() ? fallbackRoot : objectMapper.createObjectNode();

        com.fasterxml.jackson.databind.node.ObjectNode merged = objectMapper.createObjectNode();

        merged.put("name", firstNonBlank(preferred, fallback, "name", "Candidate"));
        merged.put("email", firstNonBlank(preferred, fallback, "email", ""));
        merged.put("phone", firstNonBlank(preferred, fallback, "phone", ""));
        merged.put("summary", firstNonBlank(preferred, fallback, "summary", ""));

        JsonNode skills = nonEmptyArray(preferred.path("skills"), fallback.path("skills"));
        merged.set("skills", skills == null ? objectMapper.createArrayNode() : skills.deepCopy());

        JsonNode experience = nonEmptyArray(preferred.path("experience"), fallback.path("experience"));
        merged.set("experience", experience == null ? objectMapper.createArrayNode() : experience.deepCopy());

        JsonNode education = nonEmptyArray(preferred.path("education"), fallback.path("education"));
        merged.set("education", education == null ? objectMapper.createArrayNode() : education.deepCopy());

        copyAdditionalFields(merged, fallback);
        copyAdditionalFields(merged, preferred);
        return merged;
    }

    private void copyAdditionalFields(com.fasterxml.jackson.databind.node.ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        Iterator<String> names = source.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (isStandardField(field)) {
                continue;
            }
            JsonNode value = source.get(field);
            if (value != null && !value.isNull() && !target.has(field)) {
                target.set(field, value.deepCopy());
            }
        }
    }

    private boolean isStandardField(String field) {
        return "name".equals(field)
                || "email".equals(field)
                || "phone".equals(field)
                || "summary".equals(field)
                || "skills".equals(field)
                || "experience".equals(field)
                || "education".equals(field);
    }

    private String firstNonBlank(JsonNode preferred, JsonNode fallback, String field, String defaultValue) {
        String first = cleanText(preferred.path(field).asText(""));
        if (!first.isBlank()) {
            return first;
        }
        String second = cleanText(fallback.path(field).asText(""));
        if (!second.isBlank()) {
            return second;
        }
        return defaultValue;
    }

    private JsonNode nonEmptyArray(JsonNode first, JsonNode second) {
        if (first != null && first.isArray() && !first.isEmpty()) {
            return first;
        }
        if (second != null && second.isArray() && !second.isEmpty()) {
            return second;
        }
        return null;
    }

    private void renderAdditionalSections(Document document, JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }

        Set<String> rendered = new LinkedHashSet<>();
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (isStandardField(key)) {
                continue;
            }

            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                continue;
            }

            String label = toSectionLabel(key);
            if (!rendered.add(label)) {
                continue;
            }

            if (value.isTextual()) {
                String text = cleanText(value.asText(""));
                if (text.isBlank()) {
                    continue;
                }
                addSectionLabel(document, label);
                document.add(new Paragraph(text).setFontSize(10).setMarginBottom(10));
                continue;
            }

            if (value.isArray()) {
                if (value.isEmpty()) {
                    continue;
                }
                addSectionLabel(document, label);
                if (value.get(0).isObject()) {
                    for (JsonNode item : value) {
                        List<String> lines = flattenObject(item);
                        for (String line : lines) {
                            document.add(new Paragraph("- " + line)
                                    .setFontSize(10)
                                    .setMarginLeft(8)
                                    .setMarginTop(0)
                                    .setMarginBottom(2));
                        }
                        if (lines.isEmpty()) {
                            String itemText = cleanText(item.asText(""));
                            if (!itemText.isBlank()) {
                                document.add(new Paragraph("- " + itemText)
                                        .setFontSize(10)
                                        .setMarginLeft(8)
                                        .setMarginTop(0)
                                        .setMarginBottom(2));
                            }
                        }
                    }
                } else {
                    List<String> values = readTextArray(value);
                    for (String v : values) {
                        String text = cleanText(v);
                        if (text.isBlank()) {
                            continue;
                        }
                        document.add(new Paragraph("- " + text)
                                .setFontSize(10)
                                .setMarginLeft(8)
                                .setMarginTop(0)
                                .setMarginBottom(2));
                    }
                }
                document.add(new Paragraph("").setMarginBottom(6));
                continue;
            }

            if (value.isObject()) {
                List<String> lines = flattenObject(value);
                if (lines.isEmpty()) {
                    continue;
                }
                addSectionLabel(document, label);
                for (String line : lines) {
                    document.add(new Paragraph("- " + line)
                            .setFontSize(10)
                            .setMarginLeft(8)
                            .setMarginTop(0)
                            .setMarginBottom(2));
                }
                document.add(new Paragraph("").setMarginBottom(6));
            }
        }
    }

    private List<String> flattenObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }

            String keyLabel = toSectionLabel(key);
            if (value.isTextual()) {
                String text = cleanText(value.asText(""));
                if (!text.isBlank()) {
                    lines.add(keyLabel + ": " + text);
                }
                continue;
            }

            if (value.isNumber() || value.isBoolean()) {
                lines.add(keyLabel + ": " + value.asText());
            }
        }
        return lines;
    }

    private String toSectionLabel(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "Section";
        }
        String spaced = rawKey
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (spaced.isBlank()) {
            return "Section";
        }

        String[] words = spaced.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase(Locale.ROOT);
            if (word.isBlank()) {
                continue;
            }
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }
}
