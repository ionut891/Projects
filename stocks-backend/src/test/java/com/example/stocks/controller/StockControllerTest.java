package com.example.stocks.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.stocks.model.StockSnapshot;
import com.example.stocks.service.StockService;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
public class StockControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private StockService stockService;

  @Test
  void getStock_whenTickerExists_returnsStockSnapshot() throws Exception {
    // Arrange
    StockSnapshot snapshot = new StockSnapshot("stock-1", 1010L);
    given(stockService.getPrice("stock-1")).willReturn(snapshot);

    // Act & Assert
    mockMvc
        .perform(get("/stocks/stock-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticker").value("stock-1"))
        .andExpect(jsonPath("$.price").value(1010));
  }

  @Test
  void getStock_whenTickerDoesNotExist_returnsNotFound() throws Exception {
    // Arrange
    given(stockService.getPrice("unknown-stock")).willThrow(new NoSuchElementException());

    // Act & Assert
    mockMvc.perform(get("/stocks/unknown-stock")).andExpect(status().isNotFound());
  }

  @Test
  void popularStocks_returnsListOfStrings() throws Exception {
    // Arrange
    List<String> popular = List.of("stock-5", "stock-2", "stock-8");
    given(stockService.getTopPopular(3)).willReturn(popular);

    // Act & Assert
    mockMvc
        .perform(get("/popular-stocks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("stock-5"))
        .andExpect(jsonPath("$[1]").value("stock-2"))
        .andExpect(jsonPath("$[2]").value("stock-8"));
  }

  @Test
  void sumStocks_returnsSumInMap() throws Exception {
    // Arrange
    long totalSum = 10500L;
    given(stockService.getSumOfAllStocksSameSecond()).willReturn(totalSum);

    // Act & Assert
    mockMvc
        .perform(get("/sum-stocks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sum").value(10500));
  }
}
