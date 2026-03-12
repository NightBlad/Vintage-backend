package com.example.vintage.repository;

import com.example.vintage.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Category> findByActiveTrueAndParentIsNullOrderByDisplayOrderAscNameAsc();

    List<Category> findByActiveTrueAndParentIdOrderByDisplayOrderAscNameAsc(Long parentId);

    List<Category> findByParentId(Long parentId);

    java.util.Optional<Category> findByName(String name);

    Boolean existsByName(String name);

    Boolean existsByNameAndIdNot(String name, Long id);

    Boolean existsByNameAndParentId(String name, Long parentId);

    Boolean existsByNameAndParentIdAndIdNot(String name, Long parentId, Long id);
}
