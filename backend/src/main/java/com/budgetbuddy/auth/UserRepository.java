package com.budgetbuddy.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repository-Zugriff auf {@link User} (auth-internes Interface, kein modulübergreifender Zugriff). */
public interface UserRepository extends JpaRepository<User, Long> {
}
