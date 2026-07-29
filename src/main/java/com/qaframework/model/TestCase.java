package com.qaframework.model;

/** One row from the input Excel: the query plus what was expected and what the AI actually said. */
public class TestCase {

    private final int rowNumber;
    private final String query;
    private final String expectedResponse;
    private final String actualResponse;

    public TestCase(int rowNumber, String query, String expectedResponse, String actualResponse) {
        this.rowNumber = rowNumber;
        this.query = query;
        this.expectedResponse = expectedResponse;
        this.actualResponse = actualResponse;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getQuery() {
        return query;
    }

    public String getExpectedResponse() {
        return expectedResponse;
    }

    public String getActualResponse() {
        return actualResponse;
    }

    @Override
    public String toString() {
        return "Row " + rowNumber + " | Query: " + query;
    }
}
