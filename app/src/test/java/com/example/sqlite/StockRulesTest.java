package com.example.sqlite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StockRulesTest {
    @Test public void zeroStockIsOutOfStockEvenWhenMinimumIsZero() {
        assertEquals(StockRules.OUT_OF_STOCK, StockRules.status(0, 0));
    }

    @Test public void positiveStockAtOrBelowMinimumIsCritical() {
        assertEquals(StockRules.CRITICAL, StockRules.status(3, 5));
        assertEquals(StockRules.CRITICAL, StockRules.status(5, 5));
    }

    @Test public void stockAboveMinimumIsNormal() {
        assertEquals(StockRules.NORMAL, StockRules.status(6, 5));
    }
}
