package ru.trafficmarkering.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.User;

import ru.trafficmarkering.model.Role;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findAllByRole(Role role);

    List<User> findAllByOrderByCreatedAtDesc();
}
