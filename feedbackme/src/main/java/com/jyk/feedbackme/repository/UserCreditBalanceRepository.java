package com.jyk.feedbackme.repository;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.UserCreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, Long> {
    Optional<UserCreditBalance> findByUser(AppUser user);
}
