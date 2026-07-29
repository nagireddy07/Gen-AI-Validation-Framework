# AI Response Validation Framework

A small Java/Maven/TestNG framework that data-drives AI response validation off an Excel file
and produces a single Excel report with per-query confidence scores and an overall dashboard.
No HTML/PDF reporting — just the one Excel file.

## How it works

1. `AIResponseValidationTest` has one TestNG `@DataProvider` (`excelData`) that reads
   `src/test/resources/input/AI_Test_Data.xlsx` and returns one `TestCase` per data row.
   **The row count is never hardcoded** — 1 row in the sheet runs 1 test, 100 rows run 100 tests.
2. Each row is scored by `AzureOpenAIJudge`, which calls Azure OpenAI (as an LLM judge) and gets
   back four scores (0-100):
   - `Semantic_Correctness` — does Actual mean the same as Expected?
   - `Relevance` — does Actual actually answer the Query?
   - `Completeness` — does Actual cover everything Expected covers?
   - `Groundedness` — is Actual free of unsupported/hallucinated claims vs. Expected?
   - `Overall_Confidence` is computed in code as the average of the four (not left to the LLM),
     so it's deterministic and reproducible.
3. A query is `PASS` if `Overall_Confidence >= pass.threshold` (default 75, set in `config.properties`).
4. After all rows run, `ExcelWriter` writes **one output file** with two sheets:
   - **Results** — one row per query: all 4 scores, Overall_Confidence, Status, Reason.
   - **Dashboard** — total/passed/failed, pass %, average of each score, and a list of failed rows.

## Project layout

```
src/main/java/com/qaframework/config/AppConfig.java     - loads config.properties
src/main/java/com/qaframework/model/TestCase.java        - one input row
src/main/java/com/qaframework/model/ScoreResult.java      - one scored row
src/main/java/com/qaframework/excel/ExcelReader.java       - reads input Excel (dynamic row count)
src/main/java/com/qaframework/excel/ExcelWriter.java        - writes Results + Dashboard sheets
src/main/java/com/qaframework/llm/AzureOpenAIJudge.java       - Azure OpenAI judge + retry/backoff
src/test/java/com/qaframework/tests/AIResponseValidationTest.java - TestNG DataProvider + test
src/main/resources/config.properties                       - all settings live here
src/test/resources/input/AI_Test_Data.xlsx                   - sample input (3 rows, 1 deliberately wrong)
```

## Setup

1. **Edit `src/main/resources/config.properties`:**
   ```properties
   azure.endpoint=https://YOUR-RESOURCE.cognitiveservices.azure.com
   azure.api.key=YOUR_AZURE_OPENAI_KEY
   azure.deployment=gpt-4.1-mini
   pass.threshold=75
   ```
   For real use, don't commit real keys — pull `azure.api.key` from an environment variable
   or your CI's secret store instead of the properties file if you push this to source control.

2. **Prepare your input Excel** at `src/test/resources/input/AI_Test_Data.xlsx` (or change
   `input.excel.path` in config) with header row exactly:
   | Query | Expected_Response | Actual_Response |
   |---|---|---|

   Add as many rows as you want — 1 or 1000, the DataProvider adapts automatically.

3. **Run:**
   ```bash
   mvn test
   ```
   or run `testng.xml` directly from your IDE.

4. **Check the report** at `test-output/AI_Validation_Results.xlsx` (path set by `output.excel.path`).

## Notes

- `pom.xml` targets `maven.compiler.release=21` for broad compatibility out of the box.
  Bump it to `25` once you've confirmed your local JDK/toolchain is on 25.
- A failed row (`Overall_Confidence < threshold`) fails that TestNG invocation but does **not**
  stop other rows from running — TestNG runs every data-provider row independently, and the
  `@AfterClass` report writer runs regardless (`alwaysRun = true`), so you always get a full
  Excel report even if some rows failed.
- The retry/backoff logic (exponential, 5 attempts, jitter) mirrors the pattern already used in
  your Fireflink `ValidationOfResponseUsingLLM` element, so 429s and transient Azure errors are
  handled the same way you're used to.
