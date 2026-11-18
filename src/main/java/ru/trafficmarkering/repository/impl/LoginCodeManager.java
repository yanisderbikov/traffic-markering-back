package ru.trafficmarkering.repository.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.LoginCode;
import ru.trafficmarkering.repository.LoginCodeStore;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Log4j2
class LoginCodeManager implements LoginCodeStore {

    private final LoginCodeRepo loginCodeRepo;

    @Override
    public Optional<LoginCode> getLatestActive(String email) {
        try {
            return loginCodeRepo.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);
        } catch (Exception e) {
            log.error("getLatestActive failed", e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public LoginCode save(LoginCode code) {
        try {
            return loginCodeRepo.save(code);
        } catch (Exception e) {
            log.error("save failed", e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    @Transactional
    public void invalidateAll(String email) {
        try {
            loginCodeRepo.invalidateAllByEmail(email);
        } catch (Exception e) {
            log.error("invalidateAll failed", e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
