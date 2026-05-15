package tn.esprit.eventmanagement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.registration.EventRegistrationDTO;

import tn.esprit.eventmanagement.entities.EventRegistration;
import tn.esprit.eventmanagement.repository.EventRegistrationRepository;
import tn.esprit.eventmanagement.service.CertificateService;
import tn.esprit.eventmanagement.service.EventRegistrationServiceImpl;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registrations")
@CrossOrigin
public class EventRegistrationController {

    private final EventRegistrationServiceImpl registrationService;
    private final CertificateService certificateService;
    private final EventRegistrationRepository registrationRepository;

    public EventRegistrationController(EventRegistrationServiceImpl registrationService, CertificateService certificateService, EventRegistrationRepository registrationRepository) {
        this.registrationService = registrationService;
        this.certificateService = certificateService;
        this.registrationRepository = registrationRepository;
    }

    // ✅ Register to event
    @PostMapping
    public EventRegistrationDTO register(@RequestBody EventRegistrationDTO dto) {
        return registrationService.register(dto);
    }

    // ✅ Get all registrations
    @GetMapping
    public List<EventRegistrationDTO> getAllRegistrations() {
        return registrationService.getAllRegistrations();
    }

    // ✅ Get by ID
    @GetMapping("/{id}")
    public EventRegistrationDTO getRegistrationById(@PathVariable Long id) {
        return registrationService.getRegistrationById(id);
    }

    // ✅ Get registrations by Event
    @GetMapping("/event/{eventId}")
    public List<EventRegistrationDTO> getByEvent(@PathVariable Long eventId) {
        return registrationService.getRegistrationsByEvent(eventId);
    }

    // ✅ Get registrations by User
    @GetMapping("/user/{userId}")
    public List<EventRegistrationDTO> getByUser(@PathVariable Long userId) {
        return registrationService.getRegistrationsByUser(userId);
    }

    // ✅ Update registration
    @PutMapping("/update/{id}")
    public EventRegistrationDTO updateRegistration(@PathVariable Long id,
                                                   @RequestBody EventRegistrationDTO dto) {
        return registrationService.updateRegistration(id, dto);
    }

    // ✅ Delete registration
    @DeleteMapping("/delete/{id}")
    public void deleteRegistration(@PathVariable Long id) {
        registrationService.deleteRegistration(id);
    }
    @PostMapping("/{id}/register")
    public String register(@PathVariable Long id, @RequestBody EventRegistration registration) {

        registrationService.registerToEvent(id, registration);
        return "Registration successfual";
    }
    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestParam Long userId,
                                  @RequestParam Long eventId) {
        try {
            // markAsAttended() est déjà dans ton RegistrationService
            registrationService.markAsAttended(userId, eventId);
            // Génère et sauvegarde le certificat
            certificateService.generateAndSave(userId, eventId);

            // On récupère le code pour le retourner au front
            EventRegistration reg = registrationRepository
                    .findByUserIdAndEventId(userId, eventId).orElseThrow();

            return ResponseEntity.ok(Map.of(
                    "message",         "✅ Présence confirmée",
                    "certificateCode", reg.getCertificateCode(),
                    "certificateUrl",  reg.getCertificateUrl()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/certificate/{code}")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable String code) {
        EventRegistration reg = registrationRepository
                .findByCertificateCode(code)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        try {
            byte[] pdf = certificateService.generateAndSave(
                    reg.getUserId(), reg.getEvent().getId());

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition",
                            "attachment; filename=\"certificate-" + code + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}