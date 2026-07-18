package com.vinoigitare.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import com.vinoigitare.pdf.SongbookPdfRenderer;
import com.vinoigitare.pdf.SongbookPdfRenderer.SongbookItem;

/**
 * Personalized songbook PDF, Phase B -- the real public paywall, replacing
 * {@link SongbookController#generate}'s admin-only gate for anonymous
 * visitors. See
 * {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md},
 * §2/§7 step 8.
 *
 * <p>Routes live under {@code /songbook/view/**} (status/download) rather
 * than the plan's originally-sketched flat {@code /songbook/{requestId}}
 * -- a bare {@code /songbook/*} wildcard in {@code SecurityConfig}'s
 * allowlist would also match {@code /songbook/generate}, which must stay
 * admin-gated (Phase A). The {@code view} prefix avoids that collision
 * entirely rather than relying on rule ordering to get it right.
 *
 * <p>No accounts exist (Phase 5 skipped) -- the opaque {@link
 * SongbookRequest#id()} is the entire access control for a paid download,
 * passed to Stripe as {@code client_reference_id} so the actual selection
 * data never needs to leave this app at all.
 *
 * <p><b>Pricing switched to page count, 2026-07-18 (§1c):</b> {@link
 * #checkout} now renders the PDF itself, before payment, purely to
 * determine its real page count -- {@link SongbookPricing} prices from
 * that, and a selection that would render 100+ pages is rejected
 * outright. The rendered bytes are kept ({@link SongbookRequest#pdfBytes()})
 * and served as-is by {@link #download}, rather than rendering a second
 * time after payment -- avoids double rendering, and guarantees the
 * downloaded file is exactly what was priced, even if the underlying
 * song data changes in between.
 */
@Controller
public class SongbookCheckoutController {

    private static final Logger LOG = LoggerFactory.getLogger(SongbookCheckoutController.class);

    private static final Duration DOWNLOAD_WINDOW = Duration.ofDays(7);

    private final SongbookRequestRepository requestRepository;
    private final SongbookPdfRenderer pdfRenderer;
    private final ObjectMapper objectMapper;
    private final StripeCheckoutSessionCreator sessionCreator;
    private final String stripeWebhookSecret;

    public SongbookCheckoutController(SongbookRequestRepository requestRepository, SongbookPdfRenderer pdfRenderer,
            ObjectMapper objectMapper, StripeCheckoutSessionCreator sessionCreator,
            @Value("${vinoigitare.stripe-secret-key}") String stripeSecretKey,
            @Value("${vinoigitare.stripe-webhook-secret}") String stripeWebhookSecret) {
        this.requestRepository = requestRepository;
        this.pdfRenderer = pdfRenderer;
        this.objectMapper = objectMapper;
        this.sessionCreator = sessionCreator;
        this.stripeWebhookSecret = stripeWebhookSecret;
        // Stripe.apiKey is a static field the whole SDK reads from -- set
        // once here rather than per-request, same lifecycle as any other
        // singleton bean's one-time setup.
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Renders the PDF, rejects it outright if it's 100+ pages (§1c),
     * otherwise creates the pending {@link SongbookRequest} row (keeping
     * the rendered bytes) and starts a Stripe Checkout Session priced
     * from the real page count via inline {@code price_data} -- no
     * Stripe Dashboard price configuration needed -- then redirects to
     * Stripe's hosted checkout page.
     */
    @PostMapping("/songbook/checkout")
    public String checkout(@RequestParam String selection, @RequestParam(required = false) String bookTitle,
            @RequestParam(defaultValue = "true") boolean includeChordDiagrams, HttpServletRequest httpRequest) {
        List<SongbookSelectionParser.SelectionEntry> entries = SongbookSelectionParser.parse(selection,
                objectMapper);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selection is empty");
        }

        List<SongbookItem> items = entries.stream()
                .map(entry -> new SongbookItem(entry.id(), entry.transpose()))
                .toList();
        byte[] pdf = pdfRenderer.render(items, bookTitle, includeChordDiagrams);
        int pageCount = countPages(pdf);
        if (SongbookPricing.exceedsMaxPages(pageCount)) {
            LOG.info("Songbook checkout rejected: {} pages exceeds the {}-page max", pageCount,
                    SongbookPricing.MAX_PAGES);
            return "redirect:/songbook?tooManyPages";
        }

        SongbookRequest request = SongbookRequest.createNew(selection, bookTitle, includeChordDiagrams,
                entries.size(), pageCount, pdf);
        requestRepository.save(request);

        String baseUrl = ServletUriComponentsBuilder.fromContextPath(httpRequest).build().toUriString();
        String productName = "Vino i gitare -- personalized songbook PDF (" + request.pageCount() + " pages)";
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(baseUrl + "/songbook/view/" + request.id())
                .setCancelUrl(baseUrl + "/songbook")
                .setClientReferenceId(request.id())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount((long) request.amountCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(productName)
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = sessionCreator.create(params);
            LOG.info("Songbook checkout started: request {} ({} pages, {} cents)", request.id(),
                    request.pageCount(), request.amountCents());
            return "redirect:" + session.getUrl();
        } catch (StripeException e) {
            LOG.warn("Could not create Stripe Checkout Session for songbook request {}", request.id(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start checkout", e);
        }
    }

    /** A malformed PDF here would be this app's own rendering bug, not recoverable input -- IOException wrapped unchecked, same as SongbookPdfRenderer does for its own PDF I/O. */
    private static int countPages(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read rendered PDF to count pages", e);
        }
    }

    /**
     * Signature-verified (via the Stripe SDK, not just trusted-by-network)
     * -- the source of truth for payment success, not the success-URL
     * redirect alone: redirects can be interrupted or replayed, a
     * signature-verified webhook can't be spoofed.
     */
    @PostMapping("/songbook/stripe-webhook")
    @ResponseBody
    public ResponseEntity<String> webhook(@RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            LOG.warn("Rejected a Stripe webhook call with an invalid signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = deserializeSession(event);
            String requestId = session.getClientReferenceId();
            if (requestId != null) {
                requestRepository.markPaid(requestId, Instant.now());
                LOG.info("Songbook request {} marked paid via Stripe webhook", requestId);
            } else {
                LOG.warn("Stripe checkout.session.completed event with no client_reference_id: session {}",
                        session.getId());
            }
        }
        return ResponseEntity.ok("");
    }

    private Session deserializeSession(Event event) {
        Optional<StripeObject> deserialized = event.getDataObjectDeserializer().getObject();
        if (deserialized.isPresent()) {
            return (Session) deserialized.get();
        }
        // Stripe's own recommended fallback when the webhook was sent
        // under a different API version than this SDK expects.
        try {
            return (Session) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not deserialize webhook event", e);
        }
    }

    /** Status/download page: pending, paid-and-downloadable, expired, or unknown. */
    @GetMapping("/songbook/view/{requestId}")
    public String status(@PathVariable String requestId, Model model) {
        Optional<SongbookRequest> found = requestRepository.findById(requestId);
        if (found.isEmpty()) {
            model.addAttribute("state", "not_found");
            return "songbook-status";
        }
        SongbookRequest request = found.get();
        if (!request.paid()) {
            model.addAttribute("state", "pending");
        } else if (isExpired(request)) {
            model.addAttribute("state", "expired");
        } else {
            model.addAttribute("state", "paid");
            model.addAttribute("requestId", requestId);
        }
        return "songbook-status";
    }

    /**
     * Serves the PDF rendered at checkout time (§1c) as-is -- no
     * re-rendering -- gated on {@code paid} and the 7-day download window
     * (unlimited downloads within it, friendlier than single-use for a
     * visitor's own personal file, per the plan's §1a decision).
     */
    @GetMapping(value = "/songbook/view/{requestId}/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> download(@PathVariable String requestId) {
        SongbookRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown songbook request"));
        if (!request.paid()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not paid");
        }
        if (isExpired(request)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Download window has expired");
        }

        String fileName = (request.bookTitle() == null || request.bookTitle().isBlank() ? "Vino i gitare"
                : request.bookTitle().trim()) + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Disposition", PdfDownloadFilenames.contentDispositionFor(fileName))
                .contentType(MediaType.APPLICATION_PDF)
                .body(request.pdfBytes());
    }

    private static boolean isExpired(SongbookRequest request) {
        return request.paidAt() != null && request.paidAt().plus(DOWNLOAD_WINDOW).isBefore(Instant.now());
    }
}
