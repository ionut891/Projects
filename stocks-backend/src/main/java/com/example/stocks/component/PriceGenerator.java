package com.example.stocks.component;

public interface PriceGenerator {

  /**
   * Generates the next price for a given stock. This method simulates a computationally expensive
   * operation by introducing a random delay and calculating a random price delta.
   *
   * @param ticker The stock ticker name.
   * @param currentPrice The last known price of the stock.
   * @return The next price for the stock, guaranteed to be a positive number.
   */
  long generateNextPrice(String ticker, long currentPrice);
}
