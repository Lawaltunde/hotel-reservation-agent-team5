package com.reservationagent.hotelreservationagent.slice;

import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase;
import com.reservationagent.hotelreservationagent.application.port.in.HandleWebhookUseCase.Result;
import com.reservationagent.hotelreservationagent.domain.webhook.WebhookProvider;
import com.reservationagent.hotelreservationagent.presentation.rest.WebhookController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    /** Odd spacing on purpose: signatures cover the exact bytes, so nothing may re-serialise the body. */
    private static final String RAW = "{ \"type\" : \"post_call_transcription\",\n  \"data\":{\"conversation_id\":\"conv-1\"} }";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HandleWebhookUseCase webhooks;

    @Test
    void passesTheRawBodyAndLowerCasedHeadersToTheUseCase() throws Exception {
        when(webhooks.handle(any(), anyMap(), anyString())).thenReturn(Result.ACCEPTED);

        mvc.perform(post("/webhooks/elevenlabs").contentType(MediaType.APPLICATION_JSON).content(RAW)
                        .header("ElevenLabs-Signature", "t=1,v0=abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(webhooks).handle(eq(WebhookProvider.ELEVENLABS), headers.capture(), eq(RAW));
        assertThat(headers.getValue()).containsEntry("elevenlabs-signature", "t=1,v0=abc")
                .doesNotContainKey("ElevenLabs-Signature");
    }

    @Test
    void resendHeadersAreForwarded() throws Exception {
        when(webhooks.handle(any(), anyMap(), anyString())).thenReturn(Result.ACCEPTED);

        mvc.perform(post("/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Svix-Id", "msg_1").header("Svix-Timestamp", "1700000000").header("Svix-Signature", "v1,AAA"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(webhooks).handle(eq(WebhookProvider.RESEND), headers.capture(), eq("{}"));
        assertThat(headers.getValue()).containsEntry("svix-id", "msg_1")
                .containsEntry("svix-timestamp", "1700000000")
                .containsEntry("svix-signature", "v1,AAA");
    }

    @Test
    void duplicateDeliveryIsAcknowledgedSoTheProviderStopsRetrying() throws Exception {
        when(webhooks.handle(any(), anyMap(), anyString())).thenReturn(Result.DUPLICATE);

        mvc.perform(post("/webhooks/resend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = WebhookProvider.class, names = {"ELEVENLABS", "RESEND"})
    void badSignatureIs401(WebhookProvider provider) throws Exception {
        when(webhooks.handle(eq(provider), anyMap(), anyString())).thenReturn(Result.REJECTED_SIGNATURE);

        mvc.perform(post("/webhooks/" + provider.name().toLowerCase()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void emptyBodyIsPassedAsEmptyString() throws Exception {
        when(webhooks.handle(any(), anyMap(), anyString())).thenReturn(Result.REJECTED_SIGNATURE);

        mvc.perform(post("/webhooks/elevenlabs")).andExpect(status().isUnauthorized());

        verify(webhooks).handle(eq(WebhookProvider.ELEVENLABS), anyMap(), eq(""));
    }
}
