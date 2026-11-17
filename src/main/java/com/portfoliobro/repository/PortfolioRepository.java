package com.portfoliobro.repository;

import com.portfoliobro.domain.Portfolio;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :id")
    Optional<Portfolio> findForUpdate(@Param("id") UUID id);
}
