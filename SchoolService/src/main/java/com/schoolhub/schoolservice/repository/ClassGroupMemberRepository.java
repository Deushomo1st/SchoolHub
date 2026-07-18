package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.ClassGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassGroupMemberRepository extends JpaRepository<ClassGroupMember, Long> {
    List<ClassGroupMember> findByGroupId(Long groupId);
    List<ClassGroupMember> findByGroupIdIn(List<Long> groupIds);
    void deleteByGroupId(Long groupId);
}
