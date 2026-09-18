package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.LoginCode;

import java.util.Optional;

public interface LoginCodeStore {

    Optional<LoginCode> getLatestActive(String email);

    LoginCode save(LoginCode code);

    void invalidateAll(String email);
}
