package com.github.battle.app.model;

import java.time.Instant;

public record Profile(String username, String name, String avatarUrl, String profileUrl,
                      long followers, long repositories, long stars, Long commits,
                      Long longestStreak, Instant fetchedAt) {
}
