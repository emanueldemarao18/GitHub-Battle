package com.github.battle.app.service;

import com.github.battle.app.model.Profile;

/** Boundary for retrieving complete profile statistics without coupling scoring to GitHub HTTP calls. */
public interface ProfileProvider {
    Profile fetch(String username);
}
