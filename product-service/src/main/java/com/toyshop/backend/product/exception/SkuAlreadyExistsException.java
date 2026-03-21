package com.toyshop.backend.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when SKU is already registered.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SkuAlreadyExistsException extends RuntimeException {

    public SkuAlreadyExistsException(String sku) {
        super("Product with SKU already exists: " + sku);
    }
}