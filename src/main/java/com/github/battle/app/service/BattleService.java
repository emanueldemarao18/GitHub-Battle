package com.github.battle.app.service;

import com.github.battle.app.dto.BattleResponse;
import com.github.battle.app.dto.CategoryResponse;
import com.github.battle.app.exception.BattleException;
import com.github.battle.app.model.Profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BattleService {
    private final ProfileProvider github;

    public BattleService(ProfileProvider github) { this.github = github; }

    public BattleResponse compare(String first, String second) {
        String left = username(first), right = username(second);
        if (left.equals(right)) throw new BattleException(HttpStatus.BAD_REQUEST, "Choose two different GitHub users.");
        return score(github.fetch(left), github.fetch(right));
    }

    static String username(String value) {
        String username = value == null ? "" : value.trim();
        if (!username.matches("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?") || username.contains("--"))
            throw new BattleException(HttpStatus.BAD_REQUEST, "Enter a valid GitHub username (1–39 letters, numbers, or single hyphens).");
        return username.toLowerCase(Locale.ROOT);
    }

    static BattleResponse score(Profile left, Profile right) {
        var categories = List.of(category("followers", 20, left.followers(), right.followers()),
                category("repositories", 15, left.repositories(), right.repositories()),
                category("stars", 30, left.stars(), right.stars()),
                category("commits", 25, left.commits(), right.commits()),
                category("longestStreak", 10, left.longestStreak(), right.longestStreak()));
        int availableWeight = categories.stream().filter(CategoryResponse::available).mapToInt(CategoryResponse::weight).sum();
        BigDecimal leftPoints = categories.stream().filter(CategoryResponse::available).map(CategoryResponse::leftPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal leftScore = leftPoints.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(availableWeight), 2, RoundingMode.HALF_UP);
        BigDecimal rightScore = BigDecimal.valueOf(100).subtract(leftScore);
        String winner = leftScore.compareTo(rightScore) == 0 ? null :
                leftScore.compareTo(rightScore) > 0 ? left.username() : right.username();
        boolean full = categories.stream().allMatch(CategoryResponse::available);
        return new BattleResponse(left, right, categories, leftScore, rightScore, winner, winner == null,
                full ? List.of("Contribution metrics cover GitHub's past-year contribution window; longestStreak counts consecutive active calendar days.") :
                        List.of("Commit and streak data require a server GitHub token. Unavailable categories are excluded for both players; remaining weights are normalized."),
                "/api/battles?left=" + left.username() + "&right=" + right.username());
    }

    private static CategoryResponse category(String name, int weight, Long left, Long right) {
        if (left == null || right == null) return new CategoryResponse(name, weight, left, right, false, null, null);
        // Split the category's points in proportion to its raw values; empty categories tie.
        BigDecimal points = left == 0 && right == 0 ? BigDecimal.valueOf(weight).divide(BigDecimal.valueOf(2)) :
                BigDecimal.valueOf(left).multiply(BigDecimal.valueOf(weight))
                        .divide(BigDecimal.valueOf(left).add(BigDecimal.valueOf(right)), 10, RoundingMode.HALF_UP);
        return new CategoryResponse(name, weight, left, right, true, points, BigDecimal.valueOf(weight).subtract(points));
    }

}
