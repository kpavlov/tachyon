/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j.model;

/** A single line item in a {@link PlaceOrderRequest}. */
public record OrderItem(String name, int quantity) {}
