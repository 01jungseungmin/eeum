package com.eeum.eeum.domain.category.repository;

import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCategoryIdAndTypeAndIsActiveTrue(
            Long categoryId,
            CategoryType type
    );

    List<Category> findAllByOrderByTypeAscDepthAscParentIdAscDisplayOrderAscCategoryIdAsc();

    List<Category> findAllByTypeOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
            CategoryType type
    );

    List<Category> findAllByTypeAndIsActiveTrueOrderByDepthAscParentIdAscDisplayOrderAscCategoryIdAsc(
            CategoryType type
    );

    boolean existsByTypeAndParentIdAndName(
            CategoryType type,
            Long parentId,
            String name
    );

    boolean existsByTypeAndParentIdAndNameAndCategoryIdNot(
            CategoryType type,
            Long parentId,
            String name,
            Long categoryId
    );

    boolean existsByParentId(Long parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.categoryId = :categoryId")
    Optional<Category> findByIdForUpdate(@Param("categoryId") Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Category c
            where c.type = :type
              and ((:parentId is null and c.parentId is null) or c.parentId = :parentId)
            order by c.displayOrder asc, c.categoryId asc
            """)
    List<Category> findSiblingsForUpdate(
            @Param("type") CategoryType type,
            @Param("parentId") Long parentId
    );
}
