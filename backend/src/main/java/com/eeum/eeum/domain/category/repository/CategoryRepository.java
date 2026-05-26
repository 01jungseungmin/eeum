package com.eeum.eeum.domain.category.repository;

import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    Optional<Category> findByCategoryIdAndTypeAndIsActiveTrue(
            Long categoryId,
            CategoryType type
    );
}
