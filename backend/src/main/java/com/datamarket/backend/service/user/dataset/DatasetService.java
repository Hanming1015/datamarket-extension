package com.datamarket.backend.service.user.dataset;

import com.datamarket.backend.pojo.Dataset;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing Dataset operations.
 */

public interface DatasetService {
    Dataset addDataset(Dataset dataset);

    List<Dataset> getDatasetList();

    Dataset updateDataset(Dataset dataset);

    void removeDataset(String id);

    List<Dataset> getDatasetListAll();
}
