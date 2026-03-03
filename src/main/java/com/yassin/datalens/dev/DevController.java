package com.yassin.datalens.dev;

import com.yassin.datalens.dto.BenchmarkStatsResponse;
import com.yassin.datalens.dto.ExplainResponse;
import com.yassin.datalens.dto.IndexOperationResponse;
import com.yassin.datalens.dto.SeedResponse;
import com.yassin.datalens.service.EventIngestionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@Validated
public class DevController {

    private final EventIngestionService eventIngestionService;
    private final IndexManagementService indexManagementService;
    private final BenchmarkService benchmarkService;

    public DevController(EventIngestionService eventIngestionService,
                         IndexManagementService indexManagementService,
                         BenchmarkService benchmarkService) {
        this.eventIngestionService = eventIngestionService;
        this.indexManagementService = indexManagementService;
        this.benchmarkService = benchmarkService;
    }

    @PostMapping("/seed")
    public SeedResponse seed(@RequestParam(defaultValue = "100000") @Min(1) @Max(5000000) long n,
                             @RequestParam(defaultValue = "jdbc") String mode,
                             @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return eventIngestionService.seed(n, mode, days);
    }

    @PostMapping("/indexes/apply")
    public IndexOperationResponse applyIndexes(@RequestParam(defaultValue = "optimized") String profile) {
        return indexManagementService.apply(IndexProfile.from(profile));
    }

    @PostMapping("/indexes/drop")
    public IndexOperationResponse dropIndexes(@RequestParam(defaultValue = "optimized") String profile) {
        return indexManagementService.drop(IndexProfile.from(profile));
    }

    @PostMapping("/benchmark/run")
    public BenchmarkStatsResponse benchmark(@RequestParam(defaultValue = "errorRate") String scenario,
                                            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int iterations) {
        return benchmarkService.run(BenchmarkScenario.from(scenario), iterations);
    }

    @GetMapping("/explain")
    public ExplainResponse explain(@RequestParam(defaultValue = "errorRate") String scenario,
                                   @RequestParam(defaultValue = "optimized") String profile,
                                   @RequestParam(required = false, defaultValue = "base") String variant) {
        IndexProfile indexProfile = IndexProfile.from(profile);
        indexManagementService.apply(indexProfile);
        BenchmarkScenario benchmarkScenario = BenchmarkScenario.from(scenario);
        String plan = benchmarkService.explain(benchmarkScenario, variant);
        return new ExplainResponse(benchmarkScenario.apiName(), indexProfile.name().toLowerCase(), variant, plan);
    }
}
