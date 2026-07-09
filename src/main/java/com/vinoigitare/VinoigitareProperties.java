package com.vinoigitare;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level application configuration, bound from the {@code vinoigitare.*}
 * prefix in {@code application.yml}.
 *
 * <p>Mirrors the old app's {@code VINOIGITARE_HOME/vinoigitare.properties}
 * convention: {@code songsDir} defaults to a local folder but can be
 * overridden by the {@code VINOIGITARE_HOME} environment variable (see
 * {@code application.yml}).
 *
 * @param songsDir directory containing song files (Phase 1: flat {@code .tab}
 *                 files, one per song)
 */
@ConfigurationProperties(prefix = "vinoigitare")
public record VinoigitareProperties(String songsDir) {
}
