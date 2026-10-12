package com.syfe.personalfinancemanager.repository;

import com.syfe.personalfinancemanager.entity.SavingsGoal;
import com.syfe.personalfinancemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for SavingsGoal entity.
 */
@Repository
public interface GoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByUserOrderByIdAsc(User user);

    Optional<SavingsGoal> findByIdAndUser(Long id, User user);
}
