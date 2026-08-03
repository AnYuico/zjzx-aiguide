package com.tzp.zjzx.agent.exception;

public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException(String message) {
        super(message);
    }

    public ProductCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
