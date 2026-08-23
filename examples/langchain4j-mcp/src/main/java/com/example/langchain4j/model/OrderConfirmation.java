/*
 * Copyright (c) 2026 Konstantin Pavlov/IT Staff and contributors.
 */

package com.example.langchain4j.model;

/** Composite output of {@code OrderService.placeOrder} — computed totals for the placed order. */
public record OrderConfirmation(String orderId, String customer, int totalItems, double totalPrice) {}
