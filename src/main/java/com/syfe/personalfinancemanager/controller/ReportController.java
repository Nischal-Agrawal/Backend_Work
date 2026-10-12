package com.syfe.personalfinancemanager.controller;

import com.syfe.personalfinancemanager.dto.response.MonthlyReportResponse;
import com.syfe.personalfinancemanager.dto.response.YearlyReportResponse;
import com.syfe.personalfinancemanager.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller handling financial reports and analytics endpoints.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Generates a monthly financial report.
     *
     * @param year Calendar year.
     * @param month Calendar month (1-12).
     * @return 200 OK with monthly income, expenses, and net savings.
     */
    @GetMapping("/monthly/{year}/{month}")
    public ResponseEntity<MonthlyReportResponse> getMonthlyReport(
            @PathVariable("year") int year,
            @PathVariable("month") int month) {

        MonthlyReportResponse response = reportService.getMonthlyReport(year, month);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a yearly financial report.
     *
     * @param year Calendar year.
     * @return 200 OK with yearly income, expenses, and net savings.
     */
    @GetMapping("/yearly/{year}")
    public ResponseEntity<YearlyReportResponse> getYearlyReport(
            @PathVariable("year") int year) {

        YearlyReportResponse response = reportService.getYearlyReport(year);
        return ResponseEntity.ok(response);
    }
}
