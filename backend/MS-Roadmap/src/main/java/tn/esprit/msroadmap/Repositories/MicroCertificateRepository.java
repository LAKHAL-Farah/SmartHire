package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.MicroCertificate;
import tn.esprit.msroadmap.Enums.CertificateStatus;

import java.util.List;

@Repository
public interface MicroCertificateRepository extends JpaRepository<MicroCertificate, Long> {
    List<MicroCertificate> findByUserId(Long userId);
    MicroCertificate findByCertificateCode(String code);
    List<MicroCertificate> findByUserIdAndStatus(Long userId, CertificateStatus status);
}
