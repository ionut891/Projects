# Stocks Backend Simulation

A Spring Boot (Java 21) backend that simulates a fictional stock market for a web-based game.  
It generates random stock prices for 10 tickers in real time, with concurrency handling to avoid redundant expensive calculations.

## Features

- **10 stocks**: `stock-1` to `stock-10`, starting at **1000 GBP**.
- **Price updates every second** via an expensive mock generator (500–1000 ms delay).
- **Whole-pound prices** only (no pennies).
- **Deduplication**: Multiple requests in the same second for the same stock share the same calculation.
- **Virtual threads** for efficient concurrency (Java 21).
- **Endpoints**:
  - `GET /stocks/{ticker}` → current price for a ticker (increments popularity count).
  - `GET /popular-stocks` → top 3 most queried stocks since server start.
  - `GET /sum-stocks` → sum of all stock prices at the same second (stable snapshot).

## Requirements

- Java 21+
- Maven 3.9+
- No persistence; all state is in memory and resets on restart.

## Running

```bash
mvn spring-boot:run
