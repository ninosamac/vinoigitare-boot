# vinoigitare-boot

Spring Boot rewrite of the old Vaadin/OSGi Vinoigitare guitar-chord songbook
("Vino i gitare" — wine and guitars) for ex-Yugoslav songs, aiming for
feature parity with [pesmarica.rs](https://www.pesmarica.rs/) on a modern,
mobile-first stack.

## Prerequisites

- **Java 21.** If you don't already have a JDK on `PATH`, install one via
  [SDKMAN](https://sdkman.io/):
  ```
  curl -s "https://get.sdkman.io" | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk install java 21.0.4-tem
  ```
  (`21.0.4-tem` is the exact Temurin build this project was developed and
  verified against — any Java 21 JDK already on `PATH` works too.)
- No separate Maven install needed — this repo ships the Maven Wrapper
  (`./mvnw`), which downloads the right Maven version on first use.

## Build

```
./mvnw verify
```

Compiles the project and runs the full test suite.

## Run

```
./mvnw spring-boot:run
```

Starts the app on port **8080** by default: <http://localhost:8080>.

## Test

```
./mvnw test
```

Runs the whole suite. Tests are tagged `@Tag("fast")` (pure unit / MockMvc
tests, no I/O) or `@Tag("io")` (real file-system and full-Spring-context
tests). There's no dedicated Maven profile or Surefire config wiring these
up yet, but the tags work out of the box with the JUnit Platform provider
via system properties:

```
./mvnw test -Dgroups=fast   # fast tests only
./mvnw test -Dgroups=io     # io tests only
```

## Configuration

Song files live under the directory set by `vinoigitare.songs-dir` in
`src/main/resources/application.yml`, which defaults to `./data/songs` and
can be overridden with the `VINOIGITARE_HOME` environment variable —
mirroring the old app's `VINOIGITARE_HOME/vinoigitare.properties`
convention.

`./data/songs` ships with a few sample `.tab` fixtures (fictional lyrics,
not real song data) so there's something to browse locally out of the box —
one of them is diacritics-heavy (š, đ, č, ć, ž) as a UTF-8 sanity check.

## Project status

This is a phased rewrite in progress. **Done so far:** project skeleton,
song display & browse, and search. Not yet implemented: PDF export, chord
transposition, and the rest of the pesmarica.rs feature set — don't be
surprised that they're missing, they're simply later phases.
