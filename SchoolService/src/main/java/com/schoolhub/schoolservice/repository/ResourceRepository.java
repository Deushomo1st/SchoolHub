package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findAllByOrderByNameAsc();
    List<Resource> findByParentResourceId(Long parentResourceId);
}
