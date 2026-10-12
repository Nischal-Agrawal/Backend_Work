package com.syfe.personalfinancemanager.controller;

import com.syfe.personalfinancemanager.dto.request.GoalRequest;
import com.syfe.personalfinancemanager.dto.request.UpdateGoalRequest;
import com.syfe.personalfinancemanager.dto.response.ApiResponse;
import com.syfe.personalfinancemanager.dto.response.GoalListResponse;
import com.syfe.personalfinancemanager.dto.response.GoalResponse;
import com.syfe.personalfinancemanager.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling savings goals management and progress metrics.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Creates a new savings goal.
     *
     * @param request Goal creation request.
     * @return 201 Created with created goal and initial progress.
     */
    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody GoalRequest request) {
        GoalResponse response = goalService.createGoal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all savings goals for the current user.
     *
     * @return 200 OK with list of goals.
     */
    @GetMapping
    public ResponseEntity<GoalListResponse> getGoals() {
        GoalListResponse response = goalService.getGoals();
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single savings goal by ID.
     *
     * @param id Goal ID.
     * @return 200 OK with goal details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getGoal(@PathVariable("id") Long id) {
        GoalResponse response = goalService.getGoal(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing savings goal.
     *
     * @param id Goal ID.
     * @param request Update request.
     * @return 200 OK with updated goal and progress.
     */
    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable("id") Long id,
            @RequestBody UpdateGoalRequest request) {

        GoalResponse response = goalService.updateGoal(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a savings goal by ID.
     *
     * @param id Goal ID.
     * @return 200 OK with success message.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteGoal(@PathVariable("id") Long id) {
        ApiResponse response = goalService.deleteGoal(id);
        return ResponseEntity.ok(response);
    }
}
