package com.github.battle.app.config;

import com.github.battle.app.controller.BattleController;
import com.github.battle.app.exception.GlobalExceptionHandler;
import com.github.battle.app.service.BattleService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BattleTrafficInterceptorTests {
    private static BattleProperties properties(int rate) {
        return new BattleProperties(Duration.ofSeconds(30), Duration.ofMinutes(5), 10, 4, rate, "");
    }
    @Test void rateLimitReturnsProblemDetailsAndRetryAfter() throws Exception {
        var traffic = new BattleTrafficInterceptor(properties(1));
        var mvc = MockMvcBuilders.standaloneSetup(new BattleController(new BattleService(name -> null)))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(traffic).build();
        mvc.perform(get("/api/battles")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/battles")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void concurrentRequestsAreBoundedAndPermitsAreReleased() {
        var traffic = new BattleTrafficInterceptor(properties(1000));
        var requests = new java.util.ArrayList<MockHttpServletRequest>();
        var response = new MockHttpServletResponse();
        for (int i = 0; i < 8; i++) {
            var request = new MockHttpServletRequest("GET", "/api/battles");
            traffic.preHandle(request, response, this);
            requests.add(request);
        }
        assertThatThrownBy(() -> traffic.preHandle(new MockHttpServletRequest("GET", "/api/battles"), response, this))
                .isInstanceOf(com.github.battle.app.exception.BattleException.class);
        traffic.afterCompletion(requests.get(0), response, this, null);
        assertThat(traffic.preHandle(new MockHttpServletRequest("GET", "/api/battles"), response, this)).isTrue();
    }
}
