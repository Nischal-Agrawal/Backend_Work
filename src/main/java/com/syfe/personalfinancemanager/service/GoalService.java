package com.syfe.personalfinancemanager.service;

import com.syfe.personalfinancemanager.dto.request.GoalRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateGoalRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.GoalListResponse;
import com.syfe.personalfinancemanager.dto.response.GoalResponse;
import com.syfe.personalfinancemanager.entity.SavingsGoal;
import com.syfe.personalfinancemanager.entity.Transaction;
import com.syfe.personalfinancemanager.entity.TransactionType;
import com.syfe.personalfinancemanager.entity.User;
import com.syfe.personalfinancemanager.exception.BadRequestException;
import com.syfe.personalfinancemanager.exception.ResourceNotFoundException;
import com.syfe.personalfinancemanager.repository.GoalRepository;
import com.syfe.personalfinancemanager.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing savings goals and calculating live financial progress metrics.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final TransactionRepository transactionRepository;
    private final AuthService authService;

    public GoalService(GoalRepository goalRepository,
                       TransactionRepository transactionRepository,
                       AuthService authService) {
        this.goalRepository = goalRepository;
        this.transactionRepository = transactionRepository;
        this.authService = authService;
    }

    /**
     * Creates a savings goal for the authenticated user.
     *
     * @param request Savings goal payload.
     * @return Created goal response with calculated progress.
     */
    @Transactional
    public GoalResponse createGoal(GoalRequest request) {
        User currentUser = authService.getCurrentUser();

        if (request.getGoalName() == null || request.getGoalName().trim().isBlank()) {
            throw new BadRequestException("Goal name cannot be blank");
        }

        if (request.getTargetAmount() == null || request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Target amount must be greater than zero");
        }

        if (request.getTargetDate() == null || !request.getTargetDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Target date must be a future date");
        }

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        if (request.getTargetDate().isBefore(startDate)) {
            throw new BadRequestException("Target date cannot be before start date");
        }

        SavingsGoal goal = new SavingsGoal(
                request.getGoalName().trim(),
                request.getTargetAmount().setScale(2, RoundingMode.HALF_UP),
                request.getTargetDate(),
                startDate,
                currentUser
        );

        SavingsGoal saved = goalRepository.save(goal);
        return calculateProgress(saved, currentUser);
    }

    /**
     * Retrieves all savings goals for the authenticated user.
     *
     * @return List of savings goals with live progress.
     */
    @Transactional(readOnly = true)
    public GoalListResponse getGoals() {
        User currentUser = authService.getCurrentUser();
        List<SavingsGoal> goals = goalRepository.findByUserOrderByIdAsc(currentUser);

        List<GoalResponse> responses = goals.stream()
                .map(g -> calculateProgress(g, currentUser))
                .collect(Collectors.toList());

        return new GoalListResponse(responses);
    }

    /**
     * Retrieves a single savings goal by ID for the authenticated user.
     *
     * @param id Goal ID.
     * @return Goal response with live progress.
     */
    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long id) {
        User currentUser = authService.getCurrentUser();
        SavingsGoal goal = goalRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found with id: " + id));

        return calculateProgress(goal, currentUser);
    }

    /**
     * Updates an existing savings goal.
     *
     * @param id Goal ID.
     * @param request Update payload.
     * @return Updated goal response with recalculated progress.
     */
    @Transactional
    public GoalResponse updateGoal(Long id, UpdateGoalRequest request) {
        User currentUser = authService.getCurrentUser();
        SavingsGoal goal = goalRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found with id: " + id));

        if (request.getGoalName() != null && !request.getGoalName().trim().isBlank()) {
            goal.setGoalName(request.getGoalName().trim());
        }

        if (request.getTargetAmount() != null) {
            if (request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Target amount must be greater than zero");
            }
            goal.setTargetAmount(request.getTargetAmount().setScale(2, RoundingMode.HALF_UP));
        }

        if (request.getTargetDate() != null) {
            if (!request.getTargetDate().isAfter(LocalDate.now())) {
                throw new BadRequestException("Target date must be a future date");
            }
            if (request.getTargetDate().isBefore(goal.getStartDate())) {
                throw new BadRequestException("Target date cannot be before start date");
            }
            goal.setTargetDate(request.getTargetDate());
        }

        SavingsGoal updated = goalRepository.save(goal);
        return calculateProgress(updated, currentUser);
    }

    /**
     * Deletes a savings goal owned by the authenticated user.
     *
     * @param id Goal ID.
     * @return Success API response.
     */
    @Transactional
    public ApiResponse deleteGoal(Long id) {
        User currentUser = authService.getCurrentUser();
        SavingsGoal goal = goalRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found with id: " + id));

        goalRepository.delete(goal);
        return new ApiResponse("Goal deleted successfully");
    }

    /**
     * Calculates the real-time progress for a savings goal based on transactions since the goal's start date.
     *
     * @param goal The savings goal.
     * @param user The owner user.
     * @return Goal response with progress metrics.
     */
    private GoalResponse calculateProgress(SavingsGoal goal, User user) {
        List<Transaction> transactions = transactionRepository.findByUserAndDateGreaterThanEqual(user, goal.getStartDate());

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.getAmount());
            } else if (t.getType() == TransactionType.EXPENSE) {
                totalExpenses = totalExpenses.add(t.getAmount());
            }
        }

        BigDecimal currentProgress = totalIncome.subtract(totalExpenses).setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectiveProgress = currentProgress.compareTo(BigDecimal.ZERO) > 0 ? currentProgress : BigDecimal.ZERO;

        BigDecimal target = goal.getTargetAmount();
        BigDecimal percentage = BigDecimal.ZERO;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            percentage = effectiveProgress.multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
        }

        BigDecimal remainingAmount = target.subtract(effectiveProgress);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            remainingAmount = remainingAmount.setScale(2, RoundingMode.HALF_UP);
        }

        return new GoalResponse(
                goal.getId(),
                goal.getGoalName(),
                target,
                goal.getTargetDate(),
                goal.getStartDate(),
                currentProgress,
                percentage.doubleValue(),
                remainingAmount
        );
    }
}
