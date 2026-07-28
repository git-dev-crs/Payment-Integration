package com.paypal.wallet_service.repository;

import com.paypal.wallet_service.entity.WalletHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {
    Optional<WalletHold> findByHoldReference(String holdReference);

    List<WalletHold> findByStatusAndExpiresAtBefore(String active, LocalDateTime now);
}
