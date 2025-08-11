package com.example.stocks.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.stocks.component.PriceGenerator;
import com.example.stocks.model.StockSnapshot;
import com.example.stocks.model.StockStateDTO;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class StockServiceTest {

  @Mock private PriceGenerator mockPriceGenerator;
  @Mock private Clock mockClock;
  @InjectMocks private StockServiceImpl stockService;

  @BeforeEach
  void methodSetUp() {
    MockitoAnnotations.openMocks(this);
    when(mockClock.instant()).thenReturn(Instant.ofEpochSecond(1000));
  }

  @Test
  void getPrice_returnsUpdatedPrice() {
    when(mockPriceGenerator.generateNextPrice(anyString(), anyLong())).thenReturn(1001L);

    Map<String, StockStateDTO> stockStates = stockService.getStockStates();
    assertEquals(stockStates.get("stock-1").lastPrice(), 1000L, "Initial price should be 1000");
    assertEquals(stockStates.get("stock-1").lastSecond(), 0, "Initial second should be 0");
    assertEquals(stockStates.get("stock-1").popularity(), 0, "Initial popularity should be 0");

    stockService.getPrice("stock-1");

    stockStates = stockService.getStockStates();
    assertEquals(stockStates.get("stock-1").lastPrice(), 1001L, "Post update price should be 1001");
    assertEquals(
        stockStates.get("stock-1").lastSecond(), 1000, "Post update second should be 1000");
    assertEquals(stockStates.get("stock-1").popularity(), 1, "Post update popularity should be 1");

    verify(mockPriceGenerator, times(1)).generateNextPrice("stock-1", 1000L);
    verify(mockClock, atLeastOnce()).instant();
  }

  @Test
  void getPrice_throwsExceptionForUnknownTicker() {
    assertThrows(NoSuchElementException.class, () -> stockService.getPrice("unknown-stock"));
    verifyNoInteractions(mockPriceGenerator);
    verifyNoInteractions(mockClock);
  }

  @Test
  void getPopularStocks_returnsTopThree() {
    when(mockPriceGenerator.generateNextPrice(anyString(), anyLong())).thenReturn(1000L);
    when(mockClock.instant())
        .thenReturn(
            Instant.ofEpochSecond(1000),
            Instant.ofEpochSecond(2000),
            Instant.ofEpochSecond(3000),
            Instant.ofEpochSecond(4000),
            Instant.ofEpochSecond(5000),
            Instant.ofEpochSecond(6000));
    stockService.getPrice("stock-5");
    stockService.getPrice("stock-5");
    stockService.getPrice("stock-5");
    stockService.getPrice("stock-2");
    stockService.getPrice("stock-2");
    stockService.getPrice("stock-8");

    List<String> popular = stockService.getTopPopular(3);

    assertEquals(3, popular.size());
    assertEquals("stock-5", popular.get(0));
    assertEquals("stock-2", popular.get(1));
    assertEquals("stock-8", popular.get(2));

    verify(mockPriceGenerator, times(6)).generateNextPrice(anyString(), anyLong());
  }

  @Test
  void getSumOfStocks_returnsConsistentSum() {
    when(mockPriceGenerator.generateNextPrice(anyString(), anyLong())).thenReturn(1050L);
    long expectedSum = 1050L * stockService.getAllTickerValues().size();

    Map<String, StockStateDTO> stockStates = stockService.getStockStates();
    assertAll(
        "Verify initial state of all stocks",
        stockStates.values().stream()
            .map(
                state ->
                    () -> {
                      assertEquals(1000L, state.lastPrice(), "Initial price should be 1000");
                      assertEquals(0, state.lastSecond(), "Initial second should be 0");
                      assertEquals(0, state.popularity(), "Initial popularity should be 0");
                    }));

    long actualSum = stockService.getSumOfAllStocksSameSecond();
    assertEquals(expectedSum, actualSum);

    stockStates = stockService.getStockStates();
    assertAll(
        "Verify final state of all stocks",
        stockStates.values().stream()
            .map(
                state ->
                    () -> {
                      assertEquals(1050L, state.lastPrice(), "Post update price should be 1050");
                      assertEquals(1000, state.lastSecond(), "Post update second should be 1000");
                      assertEquals(0, state.popularity(), "Post update popularity should stay 0");
                    }));

    verify(mockPriceGenerator, times(10)).generateNextPrice(anyString(), anyLong());
    verify(mockClock, atLeastOnce()).instant();
  }

  @Test
  void getPrice_handlesConcurrentRequestsForTheSameTickerAcrossSecondBoundary() throws Exception {
    long initialTime = 1000L;
    long initialPrice = 1000L;
    long firstPrice = 1010L;
    long secondPrice = 1020L;
    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Chained mock responses to handle two identical calls
    when(mockPriceGenerator.generateNextPrice("stock-1", initialPrice))
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  Thread.sleep(200); // Simulate long calculation for the first call
                  return firstPrice;
                })
        .thenReturn(secondPrice); // Return this for the second call

    // T1: Submits request at second 1000
    when(mockClock.instant()).thenReturn(Instant.ofEpochSecond(initialTime));
    Future<StockSnapshot> future1 = executor.submit(() -> stockService.getPrice("stock-1"));

    // Give T1 a moment to start its long calculation
    // Without this T2 will get scheduled first and get firstPrice rather than secondPrice
    Thread.sleep(50);

    // T2: Submits request at second 1001, while T1's calculation is still running
    when(mockClock.instant()).thenReturn(Instant.ofEpochSecond(initialTime + 1));
    Future<StockSnapshot> future2 = executor.submit(() -> stockService.getPrice("stock-1"));

    StockSnapshot result1 = future1.get(1, TimeUnit.SECONDS);
    StockSnapshot result2 = future2.get(1, TimeUnit.SECONDS);

    assertEquals(firstPrice, result1.price(), "T1 should get the price for the first second");
    assertEquals(secondPrice, result2.price(), "T2 should get the price for the second second");

    verify(mockPriceGenerator, times(2)).generateNextPrice(eq("stock-1"), eq(initialPrice));
  }

  @Test
  void getPrice_concurrentRequestsSameTickerSameSecond_calculatesPriceOnlyOnce() throws Exception {
    long price = 1010L;

    when(mockPriceGenerator.generateNextPrice("stock-1", 1000L))
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  Thread.sleep(200); // Simulate slow calculation
                  return price;
                });

    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<StockSnapshot> future1 = executor.submit(() -> stockService.getPrice("stock-1"));
    Future<StockSnapshot> future2 = executor.submit(() -> stockService.getPrice("stock-1"));

    StockSnapshot result1 = future1.get(1, TimeUnit.SECONDS);
    StockSnapshot result2 = future2.get(1, TimeUnit.SECONDS);

    // Both threads should get the same result
    assertEquals(price, result1.price());
    assertEquals(price, result2.price());

    // CRITICAL: Verify the expensive method was only called ONCE
    verify(
            mockPriceGenerator,
            times(1)
                .description(
                    "The expensive price generator should only be called once to prove request coalescing is working"))
        .generateNextPrice("stock-1", 1000L);
  }

  @Test
  void getSumOfAllStocksSameSecond_withMixedCache_calculatesOnlyStaleStocks() {
    when(mockPriceGenerator.generateNextPrice("stock-1", 1000L)).thenReturn(1010L);
    when(mockPriceGenerator.generateNextPrice("stock-2", 1000L)).thenReturn(1020L);
    stockService.getPrice("stock-1");
    stockService.getPrice("stock-2");

    reset(mockPriceGenerator);

    when(mockPriceGenerator.generateNextPrice(anyString(), anyLong())).thenReturn(1100L);

    long sum = stockService.getSumOfAllStocksSameSecond();

    long expectedSum = 1010L + 1020L + (8 * 1100L);
    assertEquals(expectedSum, sum);

    verify(
            mockPriceGenerator,
            times(8).description("Should only call price generator for the 8 stale stocks"))
        .generateNextPrice(anyString(), anyLong());
  }

  @Test
  void getSumOfAllStocksSameSecond_runsInParallel_completesWithinTimeout() {

    when(mockPriceGenerator.generateNextPrice(anyString(), anyLong()))
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  Thread.sleep(200);
                  return 1000L;
                });

    assertTimeout(
        Duration.ofSeconds(1),
        () -> {
          stockService.getSumOfAllStocksSameSecond();
        },
        "The sum calculation should complete quickly due to parallel execution");
  }

  @Test
  void getPriceAndSum_concurrentRequests_maintainsConsistencyAndCorrectness() throws Exception {
    long newPriceForStock1 = 1010L;
    long newPriceForOtherStocks = 1100L;

    when(mockPriceGenerator.generateNextPrice("stock-1", 1000L))
        .thenAnswer(
            (Answer<Long>)
                invocation -> {
                  Thread.sleep(200);
                  return newPriceForStock1;
                });

    when(mockPriceGenerator.generateNextPrice(not(eq("stock-1")), anyLong()))
        .thenReturn(newPriceForOtherStocks);

    ExecutorService executor = Executors.newFixedThreadPool(2);

    Future<StockSnapshot> getPriceFuture = executor.submit(() -> stockService.getPrice("stock-1"));
    Future<Long> getSumFuture = executor.submit(() -> stockService.getSumOfAllStocksSameSecond());

    StockSnapshot priceResult = getPriceFuture.get(1, TimeUnit.SECONDS);
    long sumResult = getSumFuture.get(1, TimeUnit.SECONDS);

    assertEquals(newPriceForStock1, priceResult.price());

    long expectedSum = newPriceForStock1 + (9 * newPriceForOtherStocks);
    assertEquals(expectedSum, sumResult);

    verify(
            mockPriceGenerator,
            times(1)
                .description("Price generator should only be called once for the contested stock"))
        .generateNextPrice("stock-1", 1000L);

    assertEquals(1, stockService.getStockStates().get("stock-1").popularity());
    assertEquals(0, stockService.getStockStates().get("stock-2").popularity());
  }
}
