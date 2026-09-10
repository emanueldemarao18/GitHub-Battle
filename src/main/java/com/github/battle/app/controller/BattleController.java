package com.github.battle.app.controller;

import com.github.battle.app.dto.BattleResponse;
import com.github.battle.app.service.BattleService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/battles")
public class BattleController {
    private final BattleService battles;

    public BattleController(BattleService battles) {
        this.battles = battles;
    }

    @GetMapping
    public BattleResponse compareProfiles(@RequestParam("left") String left, @RequestParam("right") String right) {
        return battles.compare(left, right);
    }
}
