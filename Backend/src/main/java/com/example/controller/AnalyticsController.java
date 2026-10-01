package com.example.controller;

import com.example.dto.AnalyticsResponse;
import com.example.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin's analytics. SecurityConfig locks /api/admin/** to admins.
 *
 * `from` and `to` are read as plain strings and parsed in the service, so a
 * bad date answers 400 RANGE_INVALID instead of a 500.
 */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/admin/analytics")
    public AnalyticsResponse analytics(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analyticsService.analytics(from, to);
    }
}
