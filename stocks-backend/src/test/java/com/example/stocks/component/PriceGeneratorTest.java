package com.example.stocks.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PriceGeneratorTest {

  @Mock Random mockRandom;

  @InjectMocks PriceGeneratorImpl priceGenerator;

  @BeforeEach
  void methodSetUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void generateNextPrice_alwaysReturnsPositiveValue_whenDeltaIsNegative() {
    when(mockRandom.nextInt(201)).thenReturn(0);
    when(mockRandom.nextInt(501)).thenReturn(250);
    long prev = 50L;
    long next = priceGenerator.generateNextPrice("stock-1", prev);
    assertThat(next).isEqualTo(151L);
  }
}
