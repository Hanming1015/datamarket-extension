package com.datamarket.backend.controller.user.dataset;


import com.datamarket.backend.pojo.Dataset;
import com.datamarket.backend.service.user.dataset.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing Dataset related endpoints and operations.
 */

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    @GetMapping("/list")
    public ResponseEntity<?> getDatasetList() {
        return ResponseEntity.ok(datasetService.getDatasetList());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addDataset(@RequestBody Dataset dataset) {
        return ResponseEntity.ok(datasetService.addDataset(dataset));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateDataset(@RequestBody Dataset dataset) {
        return ResponseEntity.ok(datasetService.updateDataset(dataset));
    }

    @DeleteMapping("/remove/{id}")
    public ResponseEntity<?> removeDataset(@PathVariable String id) {
        datasetService.removeDataset(id);
        return ResponseEntity.ok("Dataset deleted successfully");
    }

    @GetMapping("/all")
    public ResponseEntity<?> getDatasetListAll() {
        return ResponseEntity.ok(datasetService.getDatasetListAll());
    }
}

