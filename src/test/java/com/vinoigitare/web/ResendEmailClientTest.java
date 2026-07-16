package com.vinoigitare.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * {@link ResendEmailClient} in isolation, against a mocked HTTP server
 * rather than the real Resend API -- confirms the request this app actually
 * sends (endpoint, auth header, JSON body shape) without needing a real API
 * key or network access. See
 * ~/knowledge/projects/vinoigitare/visitor-feedback-form-plan.md, §8.
 */
@Tag("fast")
class ResendEmailClientTest {

    private static ResendEmailClient clientBackedBy(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder();
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new ResendEmailClient(builder, "test-api-key", "feedback@vinoigitare.com");
    }

    @Test
    void sendsExpectedRequestShapeAndAuthHeader() {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        ResendEmailClient client = clientBackedBy(serverHolder);
        MockRestServiceServer server = serverHolder[0];

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.from").value("feedback@vinoigitare.com"))
                .andExpect(jsonPath("$.to[0]").value("vinogitare@gmail.com"))
                .andExpect(jsonPath("$.subject").value("Vino i gitare feedback from Marko"))
                .andExpect(jsonPath("$.text").value("Love the site!"))
                .andExpect(jsonPath("$.reply_to").value("marko@example.com"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.send("vinogitare@gmail.com", "Vino i gitare feedback from Marko", "Love the site!",
                "marko@example.com");

        server.verify();
    }

    @Test
    void omitsReplyToWhenNoneGiven() {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        ResendEmailClient client = clientBackedBy(serverHolder);
        MockRestServiceServer server = serverHolder[0];

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(jsonPath("$.reply_to").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.send("vinogitare@gmail.com", "Vino i gitare feedback from Anonymous", "Hi.", null);

        server.verify();
    }

    @Test
    void wrapsNon2xxResponsesInResendEmailException() {
        MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
        ResendEmailClient client = clientBackedBy(serverHolder);
        MockRestServiceServer server = serverHolder[0];

        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.send("vinogitare@gmail.com", "Subject", "Text", null))
                .isInstanceOf(ResendEmailException.class);

        server.verify();
    }
}
