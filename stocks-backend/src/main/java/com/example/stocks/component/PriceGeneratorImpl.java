package com.example.stocks.component;

import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of the PriceGenerator interface that simulates an expensive, time-consuming
 * function to generate the next stock price.
 */
@Component
public class PriceGeneratorImpl implements PriceGenerator {

  private static final Logger log = LoggerFactory.getLogger(PriceGeneratorImpl.class);

  @Autowired private Random random;

  @Override
  public long generateNextPrice(String ticker, long currentPrice) {
    try {
      long delay = 500 + random.nextInt(501);
      log.info("Generating price for ticker [{}], simulating delay of {}ms", ticker, delay);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Price generation for ticker [{}] was interrupted.", ticker, e);
    }

    int delta = random.nextInt(201) - 100; // Delta between -100 and 100
    long next = currentPrice + delta;

    // Safeguard to ensure the price is always positive.
    if (next <= 0) {
      log.warn(
          "Calculated negative price for ticker [{}]. currentPrice={}, delta={}. Applying safeguard.",
          ticker,
          currentPrice,
          delta);
      next = Math.max(1, currentPrice + Math.abs(delta) + 1);
    }

    log.info("Generated new price for ticker [{}]: {}", ticker, next);
    return next;
  }
}
