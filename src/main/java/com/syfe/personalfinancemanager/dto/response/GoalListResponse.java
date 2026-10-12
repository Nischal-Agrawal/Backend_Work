package com.syfe.personalfinancemanager.dto.response;

import java.util.List;

/**
 * Response DTO wrapper for list of savings goals.
 */
public class GoalListResponse {

    private List<GoalResponse> goals;

    public GoalListResponse() {
    }

    public GoalListResponse(List<GoalResponse> goals) {
        this.goals = goals;
    }

    public List<GoalResponse> getGoals() {
        return goals;
    }

    public void setGoals(List<GoalResponse> goals) {
        this.goals = goals;
    }
}
