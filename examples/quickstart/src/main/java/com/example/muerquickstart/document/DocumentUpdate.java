package com.example.muerquickstart.document;

/** Request body for {@code POST /api/documents/{id}}. */
public record DocumentUpdate(String status) {
}
