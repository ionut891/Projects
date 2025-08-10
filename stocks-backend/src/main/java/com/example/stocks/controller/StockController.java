package com.example.stocks.controller;

import com.example.stocks.model.StockSnapshot;
import com.example.stocks.service.StockService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for handling stock-related API requests. */
@RestController
public class StockController {

  private static final Logger log = LoggerFactory.getLogger(StockController.class);

  private final StockService stockService;

  /**
   * Constructs the controller with a dependency on the StockService.
   *
   * @param stockService The service to handle stock business logic.
   */
  @Autowired
  public StockController(StockService stockService) {
    this.stockService = stockService;
  }

  /**
   * Endpoint to get the current price of a single stock.
   *
   * @param ticker The ticker symbol of the stock.
   * @return A ResponseEntity containing the StockSnapshot or a 404 Not Found status.
   */
  @GetMapping("/stocks/{ticker}")
  public ResponseEntity<StockSnapshot> getStock(@PathVariable String ticker) {
    log.info("Received request for stock: {}", ticker);
    try {
      return ResponseEntity.ok(stockService.getPrice(ticker));
    } catch (NoSuchElementException e) {
      log.warn("Request for non-existent ticker: {}", ticker);
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Endpoint to get the top 3 most popular stocks.
   *
   * @return A ResponseEntity containing a list of the most popular ticker symbols.
   */
  @GetMapping("/popular-stocks")
  public ResponseEntity<List<String>> popularStocks() {
    log.info("Received request for popular stocks.");
    return ResponseEntity.ok(stockService.getTopPopular(3));
  }

  /**
   * Endpoint to get the sum of all current stock prices.
   *
   * @return A ResponseEntity containing a map with the total sum.
   */
  @GetMapping("/sum-stocks")
  public ResponseEntity<Map<String, Long>> sumStocks() {
    log.info("Received request for sum of all stocks.");
    return ResponseEntity.ok(Map.of("sum", stockService.getSumOfAllStocksSameSecond()));
  }
}
