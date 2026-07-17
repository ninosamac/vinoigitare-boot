package com.vinoigitare.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the songbook selection JSON {@code static/js/songbook.js} submits
 * -- {@code [{id, transpose}, ...]} -- shared between {@link
 * SongbookController} (Phase A, admin-gated direct generation) and {@link
 * SongbookCheckoutController} (Phase B, the public paywall), rather than
 * duplicating the same parse-or-400 logic in both.
 */
final class SongbookSelectionParser {

    private SongbookSelectionParser() {
    }

    /** Matches static/js/songbook.js's localStorage entry shape exactly. */
    record SelectionEntry(String id, int transpose) {
    }

    static List<SelectionEntry> parse(String selection, ObjectMapper objectMapper) {
        try {
            return List.of(objectMapper.readValue(selection, SelectionEntry[].class));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed selection", e);
        }
    }
}
