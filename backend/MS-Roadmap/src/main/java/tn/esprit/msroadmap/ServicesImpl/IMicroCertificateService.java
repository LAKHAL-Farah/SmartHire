package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.MicroCertificate;
import tn.esprit.msroadmap.Enums.CertificateStatus;

import java.util.List;

public interface IMicroCertificateService {
    MicroCertificate generateCertificate(Long userId, Long milestoneId);
    List<MicroCertificate> getCertificatesByUserId(Long userId);
    MicroCertificate getCertificateByCertificateCode(String code);
    MicroCertificate markLinkedInShared(Long certificateId);
    void revokeCertificate(Long certificateId);
}
