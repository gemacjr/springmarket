package com.springmarket.order;

import com.springmarket.shared.error.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidOrderStateException extends DomainException {
    public InvalidOrderStateException(OrderStatus from, OrderStatus to) {
        super(HttpStatus.BAD_REQUEST, "Cannot transition order from " + from + " to " + to);
    }
}
