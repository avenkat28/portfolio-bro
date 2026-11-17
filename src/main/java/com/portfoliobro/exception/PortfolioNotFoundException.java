package com.portfoliobro.exception;

import java.util.UUID;

public class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException(UUID id) { super("Portfolio " + id + " was not found"); }
}
