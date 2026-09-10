package com.github.battle.app.dto;

import java.math.BigDecimal;

public record CategoryResponse(String name, int weight, Long leftValue, Long rightValue,
                               boolean available, BigDecimal leftPoints, BigDecimal rightPoints) {
}
