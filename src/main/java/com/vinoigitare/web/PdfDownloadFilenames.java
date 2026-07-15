package com.vinoigitare.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Builds an RFC 6266 section 5 {@code Content-Disposition} header value by
 * hand -- shared between {@link SongPdfController} and {@link
 * SongbookController}, both of which download a PDF whose filename comes
 * from song artist/title text that routinely contains š/đ/č/ć/ž.
 *
 * <p>Extracted from {@code SongPdfController} (2026-07-15) once the
 * personalized-songbook PDF needed the exact same logic -- see this
 * class's methods for the original reasoning on why each step exists.
 */
final class PdfDownloadFilenames {

    private PdfDownloadFilenames() {
    }

    /**
     * A plain ASCII-transliterated {@code filename=} for clients that don't
     * understand the extended form, plus the accurate {@code filename*=
     * UTF-8''...} percent-encoded form for clients that do.
     *
     * <p>Deliberately not using Spring's {@code ContentDisposition.filename
     * (String, Charset)}: with a non-ASCII charset it falls back to RFC 2047
     * encoded-word syntax ({@code =?UTF-8?Q?...?=}) for the plain {@code
     * filename=} parameter (verified by hitting the single-song download
     * endpoint directly), which is a MIME/email-header convention, not what
     * RFC 6266 recommends for HTTP.
     */
    static String contentDispositionFor(String rawFileName) {
        return "attachment; filename=\"" + asciiTransliterate(rawFileName) + "\"; filename*=UTF-8''"
                + percentEncode(rawFileName);
    }

    /**
     * NFD-decompose then strip combining marks: this turns š/č/ć/ž into
     * their plain-Latin base letters (they're accented Latin letters with a
     * canonical decomposition into base + combining mark). Serbian đ/Đ are
     * NOT accented letters in Unicode's eyes -- "Latin small/capital letter
     * D with stroke" has no canonical decomposition, so NFD leaves it
     * untouched (verified directly: {@code Normalizer.normalize("đ", NFD)}
     * stays {@code "đ"}) -- hence the explicit substitution before NFD.
     * Anything still outside printable ASCII afterwards (or a double quote,
     * which would break the quoted string) becomes an underscore as a
     * last-resort fallback.
     */
    private static String asciiTransliterate(String text) {
        String withPlainD = text.replace('Đ', 'D').replace('đ', 'd'); // Đ -> D, đ -> d
        String decomposed = Normalizer.normalize(withPlainD, Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}", "");
        return withoutMarks.replaceAll("[^\\x20-\\x7E]|\"", "_");
    }

    /** RFC 5987 percent-encoding: UTF-8 bytes, percent-escaped, spaces as %20 (not "+"). */
    private static String percentEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
