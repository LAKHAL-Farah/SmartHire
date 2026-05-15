package tn.esprit.msprofile.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.AuditLogResponse;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.service.AuditLogService;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogWorkflowController {

    private final AuditLogService auditLogService;
    private final StaticUserContext staticUserContext;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@RequestParam(required = false) OperationType operationType) {
        if (operationType != null) {
            return ResponseEntity.ok(auditLogService.getLogsByOperation(staticUserContext.getCurrentUserId(), operationType));
        }
        return ResponseEntity.ok(auditLogService.getLogsForUser(staticUserContext.getCurrentUserId()));
    }

}
