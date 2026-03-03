package com.yassin.datalens.controller;

import com.yassin.datalens.dto.ErrorRatePoint;
import com.yassin.datalens.dto.HealthResponse;
import com.yassin.datalens.dto.P95LatencyPoint;
import com.yassin.datalens.dto.SlidingWindowErrorPoint;
import com.yassin.datalens.dto.SuspiciousIpPoint;
import com.yassin.datalens.dto.TopEndpointPoint;
import com.yassin.datalens.dto.TopIpPoint;
import com.yassin.datalens.service.AnalyticsService;
import com.yassin.datalens.service.TimeRange;
import com.yassin.datalens.service.TimeRangeResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Validated
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TimeRangeResolver timeRangeResolver;

    public AnalyticsController(AnalyticsService analyticsService, TimeRangeResolver timeRangeResolver) {
        this.analyticsService = analyticsService;
        this.timeRangeResolver = timeRangeResolver;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return analyticsService.health();
    }

    @GetMapping("/error-rate")
    public List<ErrorRatePoint> errorRate(@RequestParam(required = false) String service,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to,
                                          @RequestParam(defaultValue = "1") @Min(1) @Max(1440) int bucketMinutes) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        return analyticsService.errorRate(service, range.from(), range.to(), bucketMinutes);
    }

    @GetMapping("/top-ips")
    public List<TopIpPoint> topIps(@RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        return analyticsService.topIps(range.from(), range.to(), limit);
    }

    @GetMapping("/top-endpoints")
    public List<TopEndpointPoint> topEndpoints(@RequestParam(required = false) String service,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        return analyticsService.topEndpoints(service, range.from(), range.to(), limit);
    }

    @GetMapping("/p95-latency")
    public List<P95LatencyPoint> p95Latency(@RequestParam(required = false) String service,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(defaultValue = "5") @Min(1) @Max(1440) int bucketMinutes) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        return analyticsService.p95Latency(service, range.from(), range.to(), bucketMinutes);
    }

    @GetMapping("/suspicious-ips")
    public List<SuspiciousIpPoint> suspiciousIps(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        return analyticsService.suspiciousIps(range.from(), range.to(), limit);
    }

    @GetMapping("/sliding-window-errors")
    public List<SlidingWindowErrorPoint> slidingWindowErrors(@RequestParam(defaultValue = "15") @Min(1) @Max(1440) int windowMinutes,
                                                              @RequestParam(defaultValue = "5") @Min(1) @Max(1440) int stepMinutes,
                                                              @RequestParam(required = false) String from,
                                                              @RequestParam(required = false) String to) {
        TimeRange range = timeRangeResolver.resolve(from, to, 24);
        if (stepMinutes > windowMinutes) {
            throw new IllegalArgumentException("stepMinutes must be less than or equal to windowMinutes");
        }
        return analyticsService.slidingWindowErrors(range.from(), range.to(), windowMinutes, stepMinutes);
    }
}
