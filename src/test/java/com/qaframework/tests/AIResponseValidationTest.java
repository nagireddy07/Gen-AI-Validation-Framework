package com.qaframework.tests;

import com.qaframework.config.AppConfig;
import com.qaframework.excel.ExcelReader;
import com.qaframework.excel.ExcelWriter;
import com.qaframework.llm.AzureOpenAIJudge;
import com.qaframework.model.ScoreResult;
import com.qaframework.model.TestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Data-driven AI response validation.
 *
 * The DataProvider reads the input Excel and hands back exactly as many rows as it finds —
 * 1 row → 1 test invocation, 100 rows → 100 test invocations. Nothing here assumes a fixed count.
 *
 * Each invocation scores one query via AzureOpenAIJudge and stores the result. Once every
 * invocation is done, results are written to a single output Excel with a "Results" sheet
 * and a "Dashboard" summary sheet.
 */
public class AIResponseValidationTest {

    private final AzureOpenAIJudge judge = new AzureOpenAIJudge();

    // Thread-safe since TestNG may run data-provider invocations in parallel depending on config.
    private static final List<ScoreResult> RESULTS = new CopyOnWriteArrayList<>();

    @DataProvider(name = "excelData")
    public Object[][] provideTestCases() throws Exception {
        List<TestCase> testCases = ExcelReader.readTestCases(AppConfig.getInputExcelPath());

        Object[][] data = new Object[testCases.size()][1];
        for (int i = 0; i < testCases.size(); i++) {
            data[i][0] = testCases.get(i);
        }
        return data;
    }

    @Test(dataProvider = "excelData")
    public void validateAIResponse(TestCase testCase) throws Exception {
        ScoreResult result = judge.evaluate(testCase);
        RESULTS.add(result);

        // Intentionally no assertion here: PASS/FAIL per query is a data outcome, not a
        // test-framework failure. Every row always "passes" as a TestNG invocation — the
        // actual verdict (and reason) lives only in the Results/Dashboard sheets of the
        // output Excel, which is the single source of truth for review.
        System.out.println(testCase + " -> Overall_Confidence=" + result.getOverallConfidence()
                + " [" + result.getStatus() + "]");
    }

    @AfterClass(alwaysRun = true)
    public void writeReport() throws Exception {
        if (RESULTS.isEmpty()) {
            System.out.println("No results collected — skipping Excel report.");
            return;
        }

        List<ScoreResult> sorted = new java.util.ArrayList<>(RESULTS);
        sorted.sort(Comparator.comparingInt(r -> r.getTestCase().getRowNumber()));

        ExcelWriter.write(sorted, AppConfig.getOutputExcelPath());
        System.out.println("Report written to: " + AppConfig.getOutputExcelPath());
    }
}