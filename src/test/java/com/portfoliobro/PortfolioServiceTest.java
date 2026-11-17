package com.portfoliobro;

import com.portfoliobro.domain.Portfolio;
import com.portfoliobro.dto.*;
import com.portfoliobro.exception.PortfolioNotFoundException;
import com.portfoliobro.repository.PortfolioRepository;
import com.portfoliobro.service.PortfolioService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    @Mock PortfolioRepository repository;

    private PortfolioService service() {
        return new PortfolioService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void createUsesNormalizedNameAndServerOwnedFields() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service().create(new CreatePortfolioRequest("  Long term  ", "Savings"));
        assertThat(result.name()).isEqualTo("Long term");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.id()).isNotNull();
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test void patchPreservesOmittedNameAndClearsDescription() {
        var portfolio = new Portfolio("Original", "Clear me", NOW.minusSeconds(60));
        when(repository.findById(portfolio.getId())).thenReturn(Optional.of(portfolio));
        var result = service().update(portfolio.getId(), new UpdatePortfolioRequest(null, ""));
        assertThat(result.name()).isEqualTo("Original");
        assertThat(result.description()).isNull();
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.createdAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test void missingDeleteDoesNotCallRepositoryDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(PortfolioNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
