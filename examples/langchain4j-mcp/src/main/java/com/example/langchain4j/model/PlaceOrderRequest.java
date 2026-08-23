/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j.model;

import java.util.List;

/** Composite input to {@code OrderService.placeOrder} — a customer name and a list of items. */
public record PlaceOrderRequest(String customer, List<OrderItem> items) {}
