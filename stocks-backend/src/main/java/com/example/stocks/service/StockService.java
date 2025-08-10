package com.example.stocks.service;

import com.example.stocks.model.StockSnapshot;
import java.util.List;
import java.util.NoSuchElementException;

public interface StockService {
  /**
   * Retrieves the current price for a given stock ticker. Increments the stock's popularity.
   *
   * @param ticker The stock ticker to query.
   * @return A snapshot of the stock's current price.
   * @throws NoSuchElementException if the ticker is not found.
   */
  StockSnapshot getPrice(String ticker);

  /**
   * Gets the most popular stocks based on query count.
   *
   * @param n The number of top popular stocks to return.
   * @return A list of the top n ticker names.
   */
  List<String> getTopPopular(int n);

  /**
   * Calculates the sum of all stock prices for a consistent moment in time. This method ensures all
   * prices are for the same second, triggering new calculations in parallel if necessary.
   *
   * @return The sum of all current stock prices.
   */
  long getSumOfAllStocksSameSecond();
}
