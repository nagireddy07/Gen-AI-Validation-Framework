package com.qaframework.excel;

import com.qaframework.model.TestCase;
import org.apache.poi.ss.usermodel.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the input Excel and returns exactly as many TestCase rows as exist in the sheet —
 * 1 row in, 1 test case out; 100 rows in, 100 test cases out. No fixed row count anywhere.
 *
 * Expected header row (case-insensitive, any column order):
 *   Query | Expected_Response | Actual_Response
 */
public final class ExcelReader {

    private static final String COL_QUERY    = "Query";
    private static final String COL_EXPECTED = "Expected_Response";
    private static final String COL_ACTUAL   = "Actual_Response";

    private ExcelReader() {
    }

    public static List<TestCase> readTestCases(String filePath) throws IOException {
        List<TestCase> testCases = new ArrayList<>();

        try (InputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new IOException("Input Excel '" + filePath + "' has no data rows below the header.");
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columnIndex = mapHeaderColumns(headerRow);

            requireColumn(columnIndex, COL_QUERY, filePath);
            requireColumn(columnIndex, COL_EXPECTED, filePath);
            requireColumn(columnIndex, COL_ACTUAL, filePath);

            int firstDataRow = headerRow.getRowNum() + 1;
            int lastDataRow = sheet.getLastRowNum();

            for (int r = firstDataRow; r <= lastDataRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) {
                    continue; // skip stray blank rows, doesn't affect count of real cases
                }

                String query    = getCellString(row, columnIndex.get(COL_QUERY));
                String expected = getCellString(row, columnIndex.get(COL_EXPECTED));
                String actual   = getCellString(row, columnIndex.get(COL_ACTUAL));

                if (query.isEmpty() && expected.isEmpty() && actual.isEmpty()) {
                    continue;
                }

                // Excel row numbers are 1-indexed for humans
                testCases.add(new TestCase(r + 1, query, expected, actual));
            }
        }

        if (testCases.isEmpty()) {
            throw new IOException("No usable data rows found in '" + filePath + "'.");
        }

        return testCases;
    }

    private static Map<String, Integer> mapHeaderColumns(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = cell.getStringCellValue().trim();
            map.put(header, cell.getColumnIndex());
        }
        return map;
    }

    private static void requireColumn(Map<String, Integer> columnIndex, String name, String filePath) throws IOException {
        if (!columnIndex.containsKey(name)) {
            throw new IOException("Column '" + name + "' not found in header row of '" + filePath + "'. "
                    + "Expected headers: Query, Expected_Response, Actual_Response");
        }
    }

    private static boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK && !getCellString(row, cell.getColumnIndex()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final DataFormatter FORMATTER = new DataFormatter();

    private static String getCellString(Row row, Integer colIndex) {
        if (colIndex == null) {
            return "";
        }
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return "";
        }
        // DataFormatter reads the cell's displayed value regardless of type
        // (string, number, formula, etc.) without mutating the cell.
        return FORMATTER.formatCellValue(cell).trim();
    }
}
