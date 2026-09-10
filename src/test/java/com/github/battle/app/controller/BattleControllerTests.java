package com.github.battle.app.controller;

import com.github.battle.app.exception.BattleException;
import com.github.battle.app.exception.GlobalExceptionHandler;
import com.github.battle.app.model.Profile;
import com.github.battle.app.service.BattleService;
import com.github.battle.app.service.ProfileProvider;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BattleControllerTests {
    private MockMvc mvc;
    private ProfileProvider github;

    @BeforeEach
    void setup() {
        github = mock(ProfileProvider.class);
        mvc = MockMvcBuilders.standaloneSetup(new BattleController(new BattleService(github)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingAndInvalidParametersReturnProblemDetails() throws Exception {
        mvc.perform(get("/api/battles").param("left", "octocat"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/battles").param("left", "../test").param("right", "other"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(github);
    }

    @Test
    void returnsSerializedBattleAndShareablePath() throws Exception {
        when(github.fetch("octocat")).thenReturn(new Profile("octocat", null, "", "", 0, 0, 0, null, null, Instant.EPOCH));
        when(github.fetch("other")).thenReturn(new Profile("other", null, "", "", 0, 0, 0, null, null, Instant.EPOCH));
        mvc.perform(get("/api/battles").param("left", "Octocat").param("right", "other"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tie").value(true))
                .andExpect(jsonPath("$.leftScore").value(50))
                .andExpect(jsonPath("$.categories.length()").value(5))
                .andExpect(jsonPath("$.sharePath").value("/api/battles?left=octocat&right=other"));
    }

    @Test
    void propagatesSafeUpstreamFailure() throws Exception {
        when(github.fetch("missing")).thenThrow(new BattleException(HttpStatus.NOT_FOUND, "User not found."));
        mvc.perform(get("/api/battles").param("left", "missing").param("right", "other"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("User not found."));
    }
}
