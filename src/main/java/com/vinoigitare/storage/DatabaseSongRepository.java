package com.vinoigitare.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.vinoigitare.model.Song;

/**
 * H2-backed {@link SongRepository}, introduced in Phase 4a. This is the
 * {@code @Primary} (actively wired) implementation from this phase
 * onward -- {@link TextFileSongRepository} stays in the codebase behind
 * the same interface (per the migration plan) and is still used directly
 * by {@link SongImporter} to read the original {@code .tab} fixtures, but
 * no longer backs {@code com.vinoigitare.service.SongService} by default.
 *
 * <p>Uses plain {@link JdbcTemplate} rather than JPA/Hibernate: this app's
 * query needs are simple CRUD plus a handful of filters and sorts, so a
 * {@link RowMapper} keeps the row-to-{@link Song} mapping fully explicit
 * with no ORM entity lifecycle to reason about -- a better fit than an
 * ORM for a solo hobbyist maintainer. The row schema is defined in {@code
 * src/main/resources/schema.sql}.
 *
 * <p>{@link Song#id()} is a {@code String} in the shared domain type (see
 * {@code Song}'s Javadoc for why), so every id here is parsed to/from
 * {@code Long} at the boundary of this class.
 */
@Repository
@Primary
public class DatabaseSongRepository implements SongRepository {

    private static final RowMapper<Song> ROW_MAPPER = DatabaseSongRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseSongRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Song> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Long numericId = parseId(id);
        if (numericId == null) {
            return Optional.empty();
        }
        List<Song> rows = jdbcTemplate.query(
                "SELECT id, artist, title, slug, chords, created_at, views FROM song WHERE id = ?",
                ROW_MAPPER, numericId);
        return rows.stream().findFirst();
    }

    @Override
    public List<Song> findAll() {
        return jdbcTemplate.query(
                "SELECT id, artist, title, slug, chords, created_at, views FROM song ORDER BY artist, title",
                ROW_MAPPER);
    }

    /**
     * Inserts a new row if {@code song.id()} isn't an existing numeric row
     * id (this is the case for every song built via {@code Song}'s 3-arg
     * constructor, e.g. from an admin "new song" form, whose id defaults to
     * the legacy {@code "artist - title"} string -- never a valid row id),
     * or updates the existing row otherwise. Returns the persisted
     * {@link Song}, with a freshly assigned numeric {@link Song#id()} on
     * insert.
     */
    @Override
    public Song save(Song song) {
        Objects.requireNonNull(song, "song must not be null");
        Long numericId = parseId(song.id());
        Instant createdAt = song.createdAt() != null ? song.createdAt() : Instant.now();

        if (numericId != null && existsById(song.id())) {
            // views is included here (fixed alongside adding
            // incrementViews() below): the UPDATE previously silently
            // dropped it, so any future caller relying on save() to
            // persist a views change would have lost that write with no
            // error. incrementViews() is still the correct way to record
            // an actual view (see its Javadoc for why), but save() should
            // not silently ignore fields it claims to accept either way.
            jdbcTemplate.update(
                    "UPDATE song SET artist = ?, title = ?, slug = ?, chords = ?, views = ? WHERE id = ?",
                    song.artist(), song.title(), song.slug(), song.chords(), song.views(), numericId);
            return new Song(song.id(), song.artist(), song.title(), song.slug(), song.chords(),
                    createdAt, song.views());
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO song (artist, title, slug, chords, created_at, views) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, song.artist());
            statement.setString(2, song.title());
            statement.setString(3, song.slug());
            statement.setString(4, song.chords());
            statement.setTimestamp(5, Timestamp.from(createdAt));
            statement.setLong(6, song.views());
            return statement;
        }, keyHolder);

        long generatedId = keyHolder.getKey().longValue();
        return new Song(String.valueOf(generatedId), song.artist(), song.title(), song.slug(),
                song.chords(), createdAt, song.views());
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Long numericId = parseId(id);
        if (numericId != null) {
            jdbcTemplate.update("DELETE FROM song WHERE id = ?", numericId);
        }
    }

    @Override
    public boolean existsById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Long numericId = parseId(id);
        if (numericId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM song WHERE id = ?", Integer.class,
                numericId);
        return count != null && count > 0;
    }

    @Override
    public void incrementViews(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Long numericId = parseId(id);
        if (numericId != null) {
            jdbcTemplate.update("UPDATE song SET views = views + 1 WHERE id = ?", numericId);
        }
    }

    private static Long parseId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Song mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Song(
                String.valueOf(rs.getLong("id")),
                rs.getString("artist"),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("chords"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("views"));
    }
}
