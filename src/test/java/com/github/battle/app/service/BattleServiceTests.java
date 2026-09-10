package com.github.battle.app.service;

import com.github.battle.app.dto.CategoryResponse;
import com.github.battle.app.exception.BattleException;
import com.github.battle.app.model.Profile;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BattleServiceTests {
    private Profile profile(String name, long value, Long contribution) {
        return new Profile(name, name, "", "", value, value, value, contribution, contribution, Instant.EPOCH);
    }

    @Test
    void emptyProfilesTieWithoutDivisionByZero() {
        var result = BattleService.score(profile("left", 0, 0L), profile("right", 0, 0L));
        assertThat(result.tie()).isTrue();
        assertThat(result.winner()).isNull();
        assertThat(result.leftScore()).isEqualByComparingTo("50.00");
    }

    @Test
    void strongerProfileWinsAndScoresSumToOneHundred() {
        var result = BattleService.score(profile("left", 3, 3L), profile("right", 1, 1L));
        assertThat(result.winner()).isEqualTo("left");
        assertThat(result.leftScore()).isEqualByComparingTo("75.00");
        assertThat(result.leftScore().add(result.rightScore())).isEqualByComparingTo("100");
        var reversed = BattleService.score(result.right(), result.left());
        assertThat(reversed.rightScore()).isEqualByComparingTo(result.leftScore());
    }

    @Test
    void missingContributionsAreExcludedForBothPlayers() {
        var result = BattleService.score(profile("left", 1, null), profile("right", 1, 500L));
        assertThat(result.tie()).isTrue();
        assertThat(result.categories().stream().filter(CategoryResponse::available)).hasSize(3);
        assertThat(result.notes().get(0)).contains("excluded for both");
    }

    @Test
    void categoriesUseDocumentedWeights() {
        var left = new Profile("left", "", "", "", 0, 0, 100, 0L, 0L, Instant.EPOCH);
        var right = new Profile("right", "", "", "", 100, 100, 0, 100L, 100L, Instant.EPOCH);
        assertThat(BattleService.score(left, right).leftScore()).isEqualByComparingTo("30");
    }

    @Test
    void invalidOrIdenticalNamesNeverCallGithub() {
        ProfileProvider client = mock(ProfileProvider.class);
        BattleService service = new BattleService(client);
        for (String name : new String[]{"", "../admin", "a--b", "-abc", "abc-", "a".repeat(40)}) {
            assertThatThrownBy(() -> service.compare(name, "other")).isInstanceOf(BattleException.class);
        }
        assertThatThrownBy(() -> service.compare(" Octocat ", "octocat")).isInstanceOf(BattleException.class);
        verifyNoInteractions(client);
        assertThat(BattleService.username(" A-b ")).isEqualTo("a-b");
    }
}
