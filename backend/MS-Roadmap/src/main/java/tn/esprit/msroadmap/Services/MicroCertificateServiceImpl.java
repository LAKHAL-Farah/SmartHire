package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.MicroCertificate;
import tn.esprit.msroadmap.Entities.RoadmapMilestone;
import tn.esprit.msroadmap.Enums.CertificateStatus;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.MicroCertificateRepository;
import tn.esprit.msroadmap.Repositories.RoadmapMilestoneRepository;
import tn.esprit.msroadmap.ServicesImpl.IMicroCertificateService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class MicroCertificateServiceImpl implements IMicroCertificateService {

    private final MicroCertificateRepository repository;
    private final RoadmapMilestoneRepository milestoneRepository;

    @Override
    public MicroCertificate generateCertificate(Long userId, Long milestoneId) {
        RoadmapMilestone m = milestoneRepository.findById(milestoneId).orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));
        MicroCertificate c = new MicroCertificate();
        c.setUserId(userId);
        c.setMilestone(m);
        c.setCertificateCode(UUID.randomUUID().toString());
        c.setStatus(CertificateStatus.GENERATED);
        c.setIssuedAt(LocalDateTime.now());
        return repository.save(c);
    }

    @Override
    public List<MicroCertificate> getCertificatesByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public MicroCertificate getCertificateByCertificateCode(String code) {
        MicroCertificate c = repository.findByCertificateCode(code);
        if (c == null) throw new ResourceNotFoundException("Certificate not found");
        return c;
    }

    @Override
    public MicroCertificate markLinkedInShared(Long certificateId) {
        MicroCertificate c = repository.findById(certificateId).orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        c.setLinkedInShared(true);
        c.setStatus(CertificateStatus.SHARED);
        return repository.save(c);
    }

    @Override
    public void revokeCertificate(Long certificateId) {
        MicroCertificate c = repository.findById(certificateId).orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        c.setStatus(CertificateStatus.REVOKED);
        repository.save(c);
    }
}
