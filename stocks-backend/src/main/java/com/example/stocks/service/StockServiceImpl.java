package com.example.stocks.service;

import com.example.stocks.component.PriceGenerator;
import com.example.stocks.model.StockSnapshot;
import com.example.stocks.model.StockStateDTO;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service to manage stock prices and popularity. This service uses a non-blocking, concurrent
 * approach to handle expensive price calculations efficiently.
 */
@Service
public class StockServiceImpl implements StockService {

  private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

  private static class StockState {
    volatile long lastSecond;
    volatile long lastPrice;
    final AtomicLong popularity = new AtomicLong(0);
  }

  @Autowired private PriceGenerator priceGenerator;
  @Autowired private Clock clock;

  private final ExecutorService executor;
  private final Map<String, StockState> stocks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Long>> inflight =
      new ConcurrentHashMap<>();

  public StockServiceImpl() {
    executor = Executors.newVirtualThreadPerTaskExecutor();
    for (int i = 1; i <= 10; i++) {
      String ticker = "stock-" + i;
      StockState stockState = new StockState();
      stockState.lastSecond = 0;
      stockState.lastPrice = 1000L;
      stocks.put(ticker, stockState);
    }
    log.info("StockService initialized with {} stocks.", stocks.size());
  }

  @Override
  public StockSnapshot getPrice(String ticker) {
    StockState stockState = stocks.get(ticker);
    if (stockState == null) {
      log.warn("Attempted to get price for non-existent ticker: {}", ticker);
      throw new NoSuchElementException("Ticker not found: " + ticker);
    }

    long nowSecond = clock.instant().getEpochSecond();
    stockState.popularity.incrementAndGet();

    // Check cache first
    if (stockState.lastSecond == nowSecond) {
      log.info("Cache hit for ticker {}. Returning cached price: {}", ticker, stockState.lastPrice);
      return new StockSnapshot(ticker, stockState.lastPrice);
    }

    log.info(
        "Cache miss for ticker {}. Attempting to calculate new price for second {}",
        ticker,
        nowSecond);
    String key = ticker + ":" + nowSecond;

    // Atomically check for an in-flight calculation or start a new one.
    CompletableFuture<Long> future =
        inflight.computeIfAbsent(
            key,
            k -> {
              log.info("No in-flight calculation for key [{}]. Starting new calculation.", k);
              return CompletableFuture.supplyAsync(
                  () -> {
                    long computed = priceGenerator.generateNextPrice(ticker, stockState.lastPrice);
                    stockState.lastPrice = computed;
                    stockState.lastSecond = nowSecond;
                    inflight.remove(k); // Cleanup to prevent memory leak
                    log.info("Calculation finished for key [{}]. New price: {}", k, computed);
                    return computed;
                  },
                  executor);
            });

    try {
      log.info("Waiting for price calculation of key [{}] to complete.", key);
      return new StockSnapshot(ticker, future.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  @Override
  public List<String> getTopPopular(int n) {
    return stocks.entrySet().stream()
        .sorted(
            (a, b) -> Long.compare(b.getValue().popularity.get(), a.getValue().popularity.get()))
        .limit(n)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  @Override
  public long getSumOfAllStocksSameSecond() {
    log.info("Calculating sum of all stocks for the current second.");
    long now = clock.instant().getEpochSecond();
    List<CompletableFuture<Long>> futures = new ArrayList<>();

    for (Map.Entry<String, StockState> e : stocks.entrySet()) {
      String ticker = e.getKey();
      StockState s = e.getValue();
      if (s.lastSecond == now) {
        log.debug("Cache hit for {} during sum calculation.", ticker);
        futures.add(CompletableFuture.completedFuture(s.lastPrice));
      } else {
        log.debug("Cache miss for {} during sum calculation.", ticker);
        String key = ticker + ":" + now;
        CompletableFuture<Long> future =
            inflight.computeIfAbsent(
                key,
                k ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          long computed = priceGenerator.generateNextPrice(ticker, s.lastPrice);
                          s.lastPrice = computed;
                          s.lastSecond = now;
                          inflight.remove(k);
                          return computed;
                        },
                        executor));
        futures.add(future);
      }
    }

    try {
      log.info("Waiting for all stock price calculations to complete for sum.");
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }

    long sum =
        futures.stream()
            .mapToLong(
                f -> {
                  try {
                    return f.get();
                  } catch (Exception ex) {
                    throw new RuntimeException(ex);
                  }
                })
            .sum();

    log.info("Sum of all stocks calculated: {}", sum);
    return sum;
  }

  public Set<String> getAllTickerValues() {
    return Collections.unmodifiableSet(stocks.keySet());
  }

  public Map<String, StockStateDTO> getStockStates() {
    return stocks.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                  StockServiceImpl.StockState state = entry.getValue();
                  return new StockStateDTO(
                      state.lastPrice, state.lastSecond, state.popularity.get());
                }));
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down executor service.");
    executor.shutdownNow();
  }
}
