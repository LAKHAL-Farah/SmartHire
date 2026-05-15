package tn.esprit.eventmanagement.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventRegistration;
import tn.esprit.eventmanagement.repository.EventRegistrationRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CertificateService {

    private final EventRegistrationRepository registrationRepository;

    public CertificateService(EventRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    // ── Génère + sauvegarde le certificat, retourne les bytes PDF ──
    public byte[] generateAndSave(Long userId, Long eventId) throws Exception {

        EventRegistration reg = registrationRepository
                .findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        if (Boolean.TRUE.equals(reg.getAttended()) && reg.getCertificateCode() != null) {
            return buildPdf(reg);
        }

        reg.setAttended(true);
        reg.setConfirmedAt(LocalDateTime.now());

        String code = "CERT-" + eventId + "-" + userId + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        reg.setCertificateCode(code);
        reg.setCertificateIssuedAt(LocalDateTime.now());
        reg.setCertificateUrl("http://localhost:4200/verify/" + code);

        registrationRepository.save(reg);
        return buildPdf(reg);
    }

    // ── Construction du PDF avec OpenPDF ──────────────────────────
    private byte[] buildPdf(EventRegistration reg) throws Exception {

        Event event = reg.getEvent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // OpenPDF : PageSize, Document, PdfWriter viennent tous de com.lowagie
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        // ── Couleurs (java.awt.Color) ──────────────────────────────
        Color gold  = new Color(180, 130,  20);
        Color navy  = new Color( 20,  40,  80);
        Color light = new Color(245, 245, 250);
        Color gray  = new Color(120, 120, 120);

        float pageW = doc.getPageSize().getWidth();
        float pageH = doc.getPageSize().getHeight();

        // ── Fond ──────────────────────────────────────────────────
        PdfContentByte canvas = writer.getDirectContentUnder();
        canvas.setColorFill(light);
        canvas.rectangle(0, 0, pageW, pageH);
        canvas.fill();

        // ── Bordure décorative (double cadre doré) ─────────────────
        PdfContentByte cb = writer.getDirectContent();
        cb.setColorStroke(gold);
        cb.setLineWidth(4f);
        cb.rectangle(24, 24, pageW - 48, pageH - 48);
        cb.stroke();
        cb.setLineWidth(1.5f);
        cb.rectangle(34, 34, pageW - 68, pageH - 68);
        cb.stroke();

        // ── Fonts (com.lowagie.text.Font) ─────────────────────────
        Font titleFont = new Font(Font.TIMES_ROMAN, 36, Font.BOLD,  gold);
        Font subFont   = new Font(Font.TIMES_ROMAN, 16, Font.ITALIC, navy);
        Font nameFont  = new Font(Font.TIMES_ROMAN, 28, Font.BOLD,  navy);
        Font bodyFont  = new Font(Font.HELVETICA,   13, Font.NORMAL, navy);
        Font smallFont = new Font(Font.HELVETICA,   10, Font.NORMAL, gray);

        // ── Espacements haut ──────────────────────────────────────
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));

        // ── Titre principal ───────────────────────────────────────
        Paragraph title = new Paragraph("CERTIFICATE OF ATTENDANCE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        // ── Ligne décorative sous le titre ────────────────────────
        doc.add(new Paragraph(" "));
        cb.setColorStroke(gold);
        cb.setLineWidth(1f);
        cb.moveTo(pageW * 0.2f, pageH - 140);
        cb.lineTo(pageW * 0.8f, pageH - 140);
        cb.stroke();

        // ── Sous-titre ────────────────────────────────────────────
        Paragraph sub = new Paragraph("This is to certify that", subFont);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingBefore(20);
        doc.add(sub);

        // ── Nom du participant ────────────────────────────────────
        Paragraph recipient = new Paragraph("Participant #" + reg.getUserId(), nameFont);
        recipient.setAlignment(Element.ALIGN_CENTER);
        recipient.setSpacingBefore(10);
        doc.add(recipient);

        // ── Corps ─────────────────────────────────────────────────
        Paragraph body = new Paragraph("has successfully attended the event", bodyFont);
        body.setAlignment(Element.ALIGN_CENTER);
        body.setSpacingBefore(14);
        doc.add(body);

        // ── Nom de l'événement ────────────────────────────────────
        String evName = (event != null && event.getTitle() != null)
                ? event.getTitle()
                : "Event #" + event.getId();
        Paragraph eventTitle = new Paragraph(evName, nameFont);
        eventTitle.setAlignment(Element.ALIGN_CENTER);
        eventTitle.setSpacingBefore(6);
        doc.add(eventTitle);

        // ── Date + lieu ───────────────────────────────────────────
        if (event != null) {
            StringBuilder meta = new StringBuilder();
            if (event.getStartDate() != null)
                meta.append("Date : ").append(event.getStartDate().toLocalDate());
            if (event.getLocation() != null && !event.getLocation().isBlank())
                meta.append("   ·   Location : ").append(event.getLocation());
            if (!meta.isEmpty()) {
                Paragraph evMeta = new Paragraph(meta.toString(), bodyFont);
                evMeta.setAlignment(Element.ALIGN_CENTER);
                evMeta.setSpacingBefore(10);
                doc.add(evMeta);
            }
        }

        // ── Date d'émission ───────────────────────────────────────
        Paragraph issued = new Paragraph(
                "Issued on : " + reg.getCertificateIssuedAt().toLocalDate(), bodyFont);
        issued.setAlignment(Element.ALIGN_CENTER);
        issued.setSpacingBefore(28);
        doc.add(issued);

        // ── Séparateur ────────────────────────────────────────────
        doc.add(new Paragraph(" "));
        cb.setColorStroke(gold);
        cb.setLineWidth(0.8f);
        cb.moveTo(pageW * 0.3f, 110);
        cb.lineTo(pageW * 0.7f, 110);
        cb.stroke();

        // ── Code de vérification ──────────────────────────────────
        Paragraph codeP = new Paragraph(
                "Certificate code : " + reg.getCertificateCode(), smallFont);
        codeP.setAlignment(Element.ALIGN_CENTER);
        codeP.setSpacingBefore(8);
        doc.add(codeP);

        // ── URL de vérification ───────────────────────────────────
        Paragraph verif = new Paragraph(
                "Verify at : " + reg.getCertificateUrl(), smallFont);
        verif.setAlignment(Element.ALIGN_CENTER);
        doc.add(verif);

        doc.close();
        return out.toByteArray();
    }
}