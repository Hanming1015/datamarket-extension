package com.datamarket.backend.service.impl.user.dataset;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.DatasetMapper;
import com.datamarket.backend.mapper.UserMapper;
import com.datamarket.backend.pojo.Dataset;
import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.user.dataset.DatasetService;
import com.datamarket.backend.utils.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of the DatasetService interface.
 */

@Service
public class DatasetServiceImpl implements DatasetService {

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private UserMapper userMapper;

//    private User getCurrentUser() {
//        UsernamePasswordAuthenticationToken authenticationToken =
//                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
//        UserDetailsImpl loginUser = (UserDetailsImpl) authenticationToken.getPrincipal();
//        return loginUser.getUser();
//    }

    @Override
    public Dataset addDataset(Dataset dataset) {
        User user = SecurityUtil.getCurrentUser();

        if (dataset.getFieldsSchema() != null) {
            List<String> fieldNames = dataset.getFieldsSchema().stream()
                    .map(schema -> (String) schema.get("name"))
                    .collect(Collectors.toList());
            dataset.setFields(fieldNames);
        }

        dataset.setOwnerId(user.getId());
        dataset.setCreatedAt(LocalDateTime.now());

        datasetMapper.insert(dataset);
        return dataset;
    }

    @Override
    public List<Dataset> getDatasetList() {
        User user = SecurityUtil.getCurrentUser();
        QueryWrapper<Dataset> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("owner_id", user.getId()); // only query own datasets
        return datasetMapper.selectList(queryWrapper);
    }

    @Override
    public Dataset updateDataset(Dataset dataset) {
        datasetMapper.updateById(dataset);
        return datasetMapper.selectById(dataset.getId());
    }

    @Override
    public void removeDataset(String id) {
        datasetMapper.deleteById(id);
    }

    @Override
    public List<Dataset> getDatasetListAll() {
        List<Dataset> datasets = datasetMapper.selectList(null);
        //System.out.println("datasets: " + datasets);
        for (Dataset dataset : datasets) {
            if (dataset.getOwnerId() != null) {
                User owner = userMapper.selectById(dataset.getOwnerId());
                //System.out.println("owner: " + owner);
                if (owner != null) {
                    dataset.setOwnerName(owner.getUsername());
                }
            }
        }

        return datasets;
    }
}
