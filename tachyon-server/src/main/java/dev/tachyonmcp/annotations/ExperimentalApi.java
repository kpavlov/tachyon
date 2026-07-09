/*
 * Copyright (c) 2026 Konstantin Pavlov and contributors.
 */

package dev.tachyonmcp.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an annotated element is experimental and may be subject to
 * change or removal in future versions. This annotation serves as a warning
 * to developers that the API is not stable and its behavior may not be finalized.
 * <p>
 * Use this annotation to mark classes, methods, constructors, fields, or packages
 * that are in an experimental state and should be used with caution.
 * <p>
 * Such elements are typically introduced for testing purposes or early
 * feedback and are not guaranteed to be part of the final API.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
public @interface ExperimentalApi {}
