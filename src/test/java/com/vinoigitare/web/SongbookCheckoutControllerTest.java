package com.vinoigitare.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stripe.exception.ApiConnectionException;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;

import com.vinoigitare.pdf.SongbookPdfRenderer;
import com.vinoigitare.security.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Personalized songbook PDF, Phase B -- the real public paywall. See
 * {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md},
 * §2/§7 step 8, §1c (page-count pricing, 2026-07-18).
 *
 * <p>{@link StripeCheckoutSessionCreator} is mocked rather than the SDK's
 * static {@code Session.create(...)} -- see that class's Javadoc for why.
 * The webhook signature is a genuinely valid one, computed the same way
 * Stripe's own SDK does ({@link Webhook.Util#computeHmacSha256}), not
 * mocked. {@code pdfRenderer.render(...)} is mocked to return a real,
 * minimal PDF built directly via PDFBox's own {@code PDDocument}/{@code
 * PDPage} ({@link #pdfWithPages}) with a specific, controlled page count
 * -- {@link SongbookCheckoutController#checkout} then exercises its real
 * page-counting code (PDFBox parsing the bytes back) genuinely, not
 * mocked around.
 */
@Tag("fast")
@WebMvcTest(SongbookCheckoutController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "vinoigitare.stripe-secret-key=sk_test_dummy",
        "vinoigitare.stripe-webhook-secret=whsec_test_dummy"
})
class SongbookCheckoutControllerTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongbookRequestRepository requestRepository;

    @MockitoBean
    private SongbookPdfRenderer pdfRenderer;

    @MockitoBean
    private StripeCheckoutSessionCreator sessionCreator;

    @Test
    void checkoutRejectsEmptySelectionWithoutCreatingRequestOrSession() throws Exception {
        mockMvc.perform(post("/songbook/checkout").with(csrf()).param("selection", "[]"))
                .andExpect(status().isBadRequest());

        then(pdfRenderer).should(never()).render(any(), any(), anyBoolean());
        then(requestRepository).should(never()).save(any());
        then(sessionCreator).should(never()).create(any());
    }

    @Test
    void checkoutWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/songbook/checkout").param("selection", "[{\"id\":\"1\",\"transpose\":0}]"))
                .andExpect(status().isForbidden());

        then(sessionCreator).should(never()).create(any());
    }

    @Test
    void checkoutUnder20PagesSavesRequestAndChargesThreeDollars() throws Exception {
        given(pdfRenderer.render(any(), any(), anyBoolean())).willReturn(pdfWithPages(10));
        Session fakeSession = mock(Session.class);
        given(fakeSession.getUrl()).willReturn("https://checkout.stripe.com/c/pay/cs_test_abc123");
        given(sessionCreator.create(any())).willReturn(fakeSession);

        mockMvc.perform(post("/songbook/checkout").with(csrf())
                        .param("selection", "[{\"id\":\"1\",\"transpose\":2},{\"id\":\"2\",\"transpose\":0}]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://checkout.stripe.com/c/pay/cs_test_abc123"));

        then(requestRepository).should().save(any());
        var paramsCaptor = forClass(SessionCreateParams.class);
        then(sessionCreator).should().create(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getLineItems().get(0).getPriceData().getUnitAmount()).isEqualTo(300L);
    }

    @Test
    void checkoutTwentyToFortyNinePagesChargesFiveDollars() throws Exception {
        given(pdfRenderer.render(any(), any(), anyBoolean())).willReturn(pdfWithPages(30));
        Session fakeSession = mock(Session.class);
        given(fakeSession.getUrl()).willReturn("https://checkout.stripe.com/c/pay/cs_test_abc123");
        given(sessionCreator.create(any())).willReturn(fakeSession);

        mockMvc.perform(post("/songbook/checkout").with(csrf()).param("selection", "[{\"id\":\"1\",\"transpose\":0}]"))
                .andExpect(status().is3xxRedirection());

        var paramsCaptor = forClass(SessionCreateParams.class);
        then(sessionCreator).should().create(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getLineItems().get(0).getPriceData().getUnitAmount()).isEqualTo(500L);
    }

    @Test
    void checkoutFiftyToNinetyNinePagesChargesSevenDollars() throws Exception {
        given(pdfRenderer.render(any(), any(), anyBoolean())).willReturn(pdfWithPages(75));
        Session fakeSession = mock(Session.class);
        given(fakeSession.getUrl()).willReturn("https://checkout.stripe.com/c/pay/cs_test_abc123");
        given(sessionCreator.create(any())).willReturn(fakeSession);

        mockMvc.perform(post("/songbook/checkout").with(csrf()).param("selection", "[{\"id\":\"1\",\"transpose\":0}]"))
                .andExpect(status().is3xxRedirection());

        var paramsCaptor = forClass(SessionCreateParams.class);
        then(sessionCreator).should().create(paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getLineItems().get(0).getPriceData().getUnitAmount()).isEqualTo(700L);
    }

    @Test
    void checkoutRejects100OrMorePagesWithoutCreatingRequestOrSession() throws Exception {
        given(pdfRenderer.render(any(), any(), anyBoolean())).willReturn(pdfWithPages(100));

        mockMvc.perform(post("/songbook/checkout").with(csrf()).param("selection", "[{\"id\":\"1\",\"transpose\":0}]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/songbook?tooManyPages"));

        then(requestRepository).should(never()).save(any());
        then(sessionCreator).should(never()).create(any());
    }

    @Test
    void checkoutFailureFromStripeReturnsBadGateway() throws Exception {
        given(pdfRenderer.render(any(), any(), anyBoolean())).willReturn(pdfWithPages(10));
        given(sessionCreator.create(any()))
                .willThrow(new ApiConnectionException("Could not connect to Stripe"));

        mockMvc.perform(post("/songbook/checkout").with(csrf())
                        .param("selection", "[{\"id\":\"1\",\"transpose\":0}]"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void webhookWithInvalidSignatureIsRejected() throws Exception {
        mockMvc.perform(post("/songbook/stripe-webhook")
                        .header("Stripe-Signature", "t=1,v1=not-a-real-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        then(requestRepository).should(never()).markPaid(anyString(), any());
    }

    @Test
    void webhookWithValidSignatureMarksTheRequestPaid() throws Exception {
        String payload = """
                {
                  "id": "evt_test_webhook",
                  "object": "event",
                  "api_version": "2024-06-20",
                  "created": 1700000000,
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_abc123",
                      "object": "checkout.session",
                      "client_reference_id": "req-abc-123"
                    }
                  }
                }""";
        String signatureHeader = signatureFor(payload);

        mockMvc.perform(post("/songbook/stripe-webhook")
                        .header("Stripe-Signature", signatureHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        then(requestRepository).should().markPaid(eq("req-abc-123"), any());
    }

    @Test
    void webhookIgnoresEventTypesOtherThanCheckoutSessionCompleted() throws Exception {
        String payload = """
                {
                  "id": "evt_test_webhook",
                  "object": "event",
                  "type": "payment_intent.created",
                  "data": {
                    "object": {
                      "id": "pi_test_abc123",
                      "object": "payment_intent"
                    }
                  }
                }""";
        String signatureHeader = signatureFor(payload);

        mockMvc.perform(post("/songbook/stripe-webhook")
                        .header("Stripe-Signature", signatureHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        then(requestRepository).should(never()).markPaid(anyString(), any());
    }

    @Test
    void statusPageShowsNotFoundForAnUnknownRequestId() throws Exception {
        given(requestRepository.findById("unknown")).willReturn(Optional.empty());

        mockMvc.perform(get("/songbook/view/unknown"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("find that order")));
    }

    @Test
    void statusPageShowsPendingForAnUnpaidRequest() throws Exception {
        SongbookRequest request = SongbookRequest.createNew("[]", null, true, 1, 5, pdfWithPages(5));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}", request.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("processing")));
    }

    @Test
    void statusPageShowsDownloadLinkForAPaidUnexpiredRequest() throws Exception {
        SongbookRequest request = paidRequest(Instant.now().minus(1, ChronoUnit.DAYS));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}", request.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("/songbook/view/" + request.id() + "/pdf")));
    }

    @Test
    void statusPageShowsExpiredForAPaidRequestPastTheWindow() throws Exception {
        SongbookRequest request = paidRequest(Instant.now().minus(8, ChronoUnit.DAYS));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}", request.id()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void downloadReturns404ForAnUnknownRequestId() throws Exception {
        given(requestRepository.findById("unknown")).willReturn(Optional.empty());

        mockMvc.perform(get("/songbook/view/unknown/pdf"))
                .andExpect(status().isNotFound());

        then(pdfRenderer).should(never()).render(any(), any(), anyBoolean());
    }

    @Test
    void downloadReturns403ForAnUnpaidRequest() throws Exception {
        SongbookRequest request = SongbookRequest.createNew("[]", null, true, 1, 5, pdfWithPages(5));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}/pdf", request.id()))
                .andExpect(status().isForbidden());

        then(pdfRenderer).should(never()).render(any(), any(), anyBoolean());
    }

    @Test
    void downloadReturns410ForAnExpiredRequest() throws Exception {
        SongbookRequest request = paidRequest(Instant.now().minus(8, ChronoUnit.DAYS));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}/pdf", request.id()))
                .andExpect(status().isGone());

        then(pdfRenderer).should(never()).render(any(), any(), anyBoolean());
    }

    @Test
    void downloadServesTheBytesStoredAtCheckoutTimeWithoutReRendering() throws Exception {
        byte[] storedPdf = pdfWithPages(5);
        SongbookRequest request = new SongbookRequest("req-1", "[{\"id\":\"1\",\"transpose\":0}]", "My Book", true, 1,
                5, 200, storedPdf, true, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));
        given(requestRepository.findById("req-1")).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/req-1/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(storedPdf))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("My Book.pdf")));

        then(pdfRenderer).should(never()).render(any(), any(), anyBoolean());
    }

    private static SongbookRequest paidRequest(Instant paidAt) throws IOException {
        SongbookRequest created = SongbookRequest.createNew("[{\"id\":\"1\",\"transpose\":0}]", null, true, 1, 5,
                pdfWithPages(5));
        return new SongbookRequest(created.id(), created.selection(), created.bookTitle(),
                created.includeChordDiagrams(), created.songCount(), created.pageCount(), created.amountCents(),
                created.pdfBytes(), true, created.createdAt(), paidAt);
    }

    /** A real, minimal PDF with exactly {@code pageCount} blank pages -- built via PDFBox directly, not mocked, so the controller's own PDFBox page-counting genuinely runs against real bytes. */
    private static byte[] pdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String signatureFor(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }
}
