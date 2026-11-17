package com.portfoliobro.repository;

import com.portfoliobro.domain.Portfolio;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {}
