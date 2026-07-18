package com.vinoigitare.web;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * H2-backed persistence for {@link SongbookRequest} -- Phase B
 * (~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md,
 * §2/§7 step 7). Plain {@link JdbcTemplate}, matching {@code
 * DatabaseSongRepository}'s existing convention rather than introducing
 * an ORM for a second entity.
 */
@Repository
public class SongbookRequestRepository {

    private static final RowMapper<SongbookRequest> ROW_MAPPER = SongbookRequestRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public SongbookRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SongbookRequest save(SongbookRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        jdbcTemplate.update(
                "INSERT INTO songbook_request (id, selection, book_title, include_chord_diagrams, song_count, "
                        + "page_count, amount_cents, pdf_bytes, paid, created_at, paid_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                request.id(), request.selection(), request.bookTitle(), request.includeChordDiagrams(),
                request.songCount(), request.pageCount(), request.amountCents(), request.pdfBytes(), request.paid(),
                Timestamp.from(request.createdAt()),
                request.paidAt() != null ? Timestamp.from(request.paidAt()) : null);
        return request;
    }

    public Optional<SongbookRequest> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        List<SongbookRequest> rows = jdbcTemplate.query(
                "SELECT id, selection, book_title, include_chord_diagrams, song_count, page_count, amount_cents, "
                        + "pdf_bytes, paid, created_at, paid_at FROM songbook_request WHERE id = ?",
                ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    /**
     * Marks a request paid -- called only from the signature-verified
     * Stripe webhook (see {@code SongbookCheckoutController}), never from
     * the success-redirect alone (a redirect can be interrupted or
     * replayed; the webhook is the actual source of truth, per the plan).
     */
    public void markPaid(String id, Instant paidAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(paidAt, "paidAt must not be null");
        jdbcTemplate.update("UPDATE songbook_request SET paid = TRUE, paid_at = ? WHERE id = ?",
                Timestamp.from(paidAt), id);
    }

    private static SongbookRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new SongbookRequest(
                rs.getString("id"),
                rs.getString("selection"),
                rs.getString("book_title"),
                rs.getBoolean("include_chord_diagrams"),
                rs.getInt("song_count"),
                rs.getInt("page_count"),
                rs.getInt("amount_cents"),
                rs.getBytes("pdf_bytes"),
                rs.getBoolean("paid"),
                rs.getTimestamp("created_at").toInstant(),
                paidAt != null ? paidAt.toInstant() : null);
    }
}
