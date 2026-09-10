package com.github.battle.app.exception;

import org.springframework.http.HttpStatus;

public class BattleException extends RuntimeException {
    private final HttpStatus status;

    public BattleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
