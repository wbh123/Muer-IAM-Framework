package com.example.muerquickstart.document;

/**
 * A minimal business resource for the quick-start.
 *
 * <p>{@code projectId} is what Muer's resource scoping keys on: Document 1001
 * belongs to Project 101, Document 2001 to Project 202.</p>
 */
public record Document(String id, String projectId, String status) {
}
