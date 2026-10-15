package com.smartservice;

import com.smartservice.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EvaluationServiceTest {
    @Autowired EvaluationService evaluation;

    @Test
    void producesReproducibleOfflineMetrics() {
        EvaluationService.EvaluationReport report = evaluation.run();
        System.out.printf("EVAL_METRICS intent=%.1f%% keywordR@1=%.1f%% hybridR@1=%.1f%% improvement=%.1f%% keywordR@3=%.1f%% hybridR@3=%.1f%%%n",
                report.intentAccuracyPercent(), report.keywordRecallAt1Percent(), report.hybridRecallAt1Percent(),
                report.recallAt1RelativeImprovementPercent(), report.keywordRecallAt3Percent(), report.hybridRecallAt3Percent());
        report.caseResults().forEach(it -> System.out.printf("EVAL_CASE query=%s expected=%d keyword=%d hybrid=%d%n",
                it.query(), it.expectedDocumentId(), it.keywordTop1DocumentId(), it.hybridTop1DocumentId()));
        assertThat(report.intentCases()).isEqualTo(30);
        assertThat(report.retrievalCases()).isEqualTo(50);
        assertThat(report.intentAccuracyPercent()).isGreaterThanOrEqualTo(90);
        assertThat(report.hybridRecallAt1Percent()).isGreaterThanOrEqualTo(report.keywordRecallAt1Percent());
        assertThat(report.hybridRecallAt3Percent()).isGreaterThanOrEqualTo(80);
        assertThat(report.unconfirmedRiskBlockPercent()).isEqualTo(100);
        assertThat(report.duplicateWritePreventionPercent()).isEqualTo(100);
    }
}
