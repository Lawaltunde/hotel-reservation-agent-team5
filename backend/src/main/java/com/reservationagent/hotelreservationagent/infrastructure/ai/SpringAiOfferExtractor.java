package com.reservationagent.hotelreservationagent.infrastructure.ai;

import com.reservationagent.hotelreservationagent.application.port.out.OfferExtractor;
import com.reservationagent.hotelreservationagent.domain.task.StayDetails;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Extracts a typed offer from a call transcript with Spring AI structured output. The model only
 * reads what was said; Java validates the result and does all arithmetic and comparison.
 *
 * <p>Provider-neutral: it uses whatever {@link ChatClient.Builder} is auto-configured (OpenAI today,
 * see {@code spring.ai.openai.*}). Native structured output asks the provider to enforce the
 * JSON schema of {@link ExtractedOffer} instead of relying on format instructions in the prompt.
 */
@Component
class SpringAiOfferExtractor implements OfferExtractor {

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final NullableOutputConverter<ExtractedOffer> outputConverter = new NullableOutputConverter<>(ExtractedOffer.class);

    SpringAiOfferExtractor(ChatClient.Builder builder,
                           @Value("classpath:prompts/offer-extraction.st") Resource systemPrompt) {
        this.chatClient = builder.build();
        this.systemPrompt = systemPrompt;
    }

    @Override
    public ExtractedOffer extract(String transcript, StayDetails stay) {
        try {
            ExtractedOffer offer = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .system(s -> s.text(systemPrompt)
                            .param("checkIn", stay.checkIn().toString())
                            .param("checkOut", stay.checkOut().toString())
                            .param("guests", String.valueOf(stay.guests()))
                            .param("rooms", String.valueOf(stay.rooms())))
                    .user(transcript)
                    .call()
                    .entity(outputConverter);
            if (offer == null) {
                throw new OfferExtractionException("The AI model returned no structured offer", null);
            }
            return offer;
        } catch (OfferExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new OfferExtractionException("Offer extraction failed: " + e.getMessage()
                    + " (check that OPENAI_API_KEY is set and OPENAI_CHAT_MODEL is a valid model)", e);
        }
    }

    static final class OfferExtractionException extends RuntimeException {
        OfferExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
