package com.github.battle.app.dto;

import com.github.battle.app.model.Profile;
import java.math.BigDecimal;
import java.util.List;

public record BattleResponse(Profile left, Profile right, List<CategoryResponse> categories,
                             BigDecimal leftScore, BigDecimal rightScore, String winner,
                             boolean tie, List<String> notes, String sharePath) {
}
