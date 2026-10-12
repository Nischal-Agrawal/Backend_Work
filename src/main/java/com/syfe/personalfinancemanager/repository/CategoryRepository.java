package com.syfe.personalfinancemanager.repository;

import com.syfe.personalfinancemanager.entity.Category;
import com.syfe.personalfinancemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Category entity.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIsNull();

    List<Category> findByUser(User user);

    Optional<Category> findByUserIsNullAndNameIgnoreCase(String name);

    Optional<Category> findByUserAndNameIgnoreCase(User user, String name);

    boolean existsByUserIsNullAndNameIgnoreCase(String name);

    boolean existsByUserAndNameIgnoreCase(User user, String name);
}
