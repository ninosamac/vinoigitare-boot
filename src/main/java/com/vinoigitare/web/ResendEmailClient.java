package com.vinoigitare.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends the visitor feedback form's email via the
 * <a href="https://resend.com/docs/api-reference/emails/send-email">Resend
 * HTTP API</a> rather than SMTP. See
 * ~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md, §8: the
 * original design (spring-boot-starter-mail + JavaMailSender over Gmail
 * SMTP) worked in local dev but failed in production -- DigitalOcean blocks
 * outbound SMTP ports (25/465/587) by default on Droplets. An HTTP API over
 * 443 (already open for everything else this app does) sidesteps that.
 */
@Component
public class ResendEmailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public ResendEmailClient(RestClient.Builder restClientBuilder,
            @Value("${vinoigitare.resend-api-key}") String apiKey,
            @Value("${vinoigitare.feedback-from-email}") String fromEmail) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public void send(String to, String subject, String text, String replyTo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", fromEmail);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("text", text);
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", replyTo);
        }
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (RestClientException e) {
            throw new ResendEmailException("Resend API call failed", e);
        }
    }
}
