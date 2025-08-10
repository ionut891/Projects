package com.example.stocks.config;

import com.example.stocks.component.PriceGenerator;
import com.example.stocks.component.PriceGeneratorImpl;
import com.example.stocks.service.StockService;
import com.example.stocks.service.StockServiceImpl;
import java.time.Clock;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public Random random() {
    return new Random();
  }

}
