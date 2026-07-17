package com.vinoigitare.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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

import com.vinoigitare.pdf.SongbookPdfRenderer;
import com.vinoigitare.security.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Personalized songbook PDF, Phase B -- the real public paywall. See
 * {@code ~/knowledge/projects/vinoigitare/personalized-songbook-pdf-plan.md},
 * §2/§7 step 8.
 *
 * <p>{@link StripeCheckoutSessionCreator} is mocked rather than the SDK's
 * static {@code Session.create(...)} -- see that class's Javadoc for why.
 * The webhook signature is a genuinely valid one, computed the same way
 * Stripe's own SDK does ({@link Webhook.Util#computeHmacSha256}), not
 * mocked -- signature verification is exactly the behavior worth actually
 * exercising, not stubbing around.
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
    void checkoutSavesRequestAndRedirectsToStripesSessionUrl() throws Exception {
        Session fakeSession = mock(Session.class);
        given(fakeSession.getUrl()).willReturn("https://checkout.stripe.com/c/pay/cs_test_abc123");
        given(sessionCreator.create(any())).willReturn(fakeSession);

        mockMvc.perform(post("/songbook/checkout").with(csrf())
                        .param("selection", "[{\"id\":\"1\",\"transpose\":2},{\"id\":\"2\",\"transpose\":0}]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://checkout.stripe.com/c/pay/cs_test_abc123"));

        then(requestRepository).should().save(any());
    }

    @Test
    void checkoutFailureFromStripeReturnsBadGateway() throws Exception {
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
        SongbookRequest request = SongbookRequest.createNew("[]", null, true, 5);
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

        then(pdfRenderer).should(never()).render(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void downloadReturns403ForAnUnpaidRequest() throws Exception {
        SongbookRequest request = SongbookRequest.createNew("[]", null, true, 5);
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}/pdf", request.id()))
                .andExpect(status().isForbidden());

        then(pdfRenderer).should(never()).render(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void downloadReturns410ForAnExpiredRequest() throws Exception {
        SongbookRequest request = paidRequest(Instant.now().minus(8, ChronoUnit.DAYS));
        given(requestRepository.findById(request.id())).willReturn(Optional.of(request));

        mockMvc.perform(get("/songbook/view/{id}/pdf", request.id()))
                .andExpect(status().isGone());

        then(pdfRenderer).should(never()).render(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void downloadReturnsPdfForAPaidUnexpiredRequest() throws Exception {
        SongbookRequest request = new SongbookRequest("req-1", "[{\"id\":\"1\",\"transpose\":0}]", "My Book", true, 1,
                500, true, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        given(requestRepository.findById("req-1")).willReturn(Optional.of(request));
        byte[] fakePdf = { 1, 2, 3 };
        given(pdfRenderer.render(any(), eq("My Book"), eq(true))).willReturn(fakePdf);

        mockMvc.perform(get("/songbook/view/req-1/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(fakePdf))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("My Book.pdf")));
    }

    private static SongbookRequest paidRequest(Instant paidAt) {
        SongbookRequest created = SongbookRequest.createNew("[{\"id\":\"1\",\"transpose\":0}]", null, true, 1);
        return new SongbookRequest(created.id(), created.selection(), created.bookTitle(),
                created.includeChordDiagrams(), created.songCount(), created.amountCents(), true,
                created.createdAt(), paidAt);
    }

    private static String signatureFor(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String signedPayload = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, signedPayload);
        return "t=" + timestamp + ",v1=" + signature;
    }
}
