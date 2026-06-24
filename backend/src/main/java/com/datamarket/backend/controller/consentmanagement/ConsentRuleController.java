package com.datamarket.backend.controller.consentmanagement;

import com.datamarket.backend.pojo.ConsentRule;
import com.datamarket.backend.service.consentmanagement.ConsentRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for managing ConsentRule related endpoints and operations.
 */

@RestController
@RequestMapping("/api/consents")
public class ConsentRuleController {

    @Autowired
    private ConsentRuleService consentRuleService;

    @PostMapping
    public ResponseEntity<?> createConsentRule (@RequestBody Map<String, Object> body) {
        ConsentRule consentRule = consentRuleService.createConsentRule(body);
        return ResponseEntity.ok(consentRule);
    }

    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revokeConsentRule (@PathVariable String id) {
        consentRuleService.revokeConsentRule(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<?> getConsentRules(
            @RequestParam(required = false) String datasetId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(consentRuleService.getConsentRules(datasetId, status));
    }

}
