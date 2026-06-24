package com.datamarket.backend.controller.user.dataset.pricingconfig;

import com.datamarket.backend.pojo.PricingConfig;
import com.datamarket.backend.service.user.dataset.pricingconfig.PricingConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing PricingConfig related endpoints and operations.
 */

@RestController
@RequestMapping("/user/dataset/pricingconfig")
public class PricingConfigController {

    @Autowired
    private PricingConfigService pricingConfigService;

    @PostMapping("/add")
    public ResponseEntity<?> addPricingConfig(@RequestBody PricingConfig pricingConfig) {
        PricingConfig newPricingConfig = pricingConfigService.addPricingConfig(pricingConfig);
        return ResponseEntity.ok(newPricingConfig);
    }

    @GetMapping("/get/{datasetId}")
    public ResponseEntity<?> getPricingConfigList(@PathVariable String datasetId) {
        PricingConfig pricingConfig = pricingConfigService.getPricingConfig(datasetId);
        return ResponseEntity.ok(pricingConfig);
    }

    @PutMapping("/put")
    public ResponseEntity<?> updatePricingConfig(@RequestBody PricingConfig pricingConfig) {
        PricingConfig updatedPricingConfig = pricingConfigService.updatePricingConfig(pricingConfig);
        return ResponseEntity.ok(updatedPricingConfig);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> removePricingConfig(@PathVariable String id) {
        pricingConfigService.removePricingConfig(id);
        return ResponseEntity.ok("Pricing configuration deleted successfully");
    }
}
