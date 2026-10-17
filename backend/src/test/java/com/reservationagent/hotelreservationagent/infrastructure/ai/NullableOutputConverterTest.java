package com.reservationagent.hotelreservationagent.infrastructure.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lives in the adapter's package because the converter is package-private. */
class NullableOutputConverterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum Outcome { OFFER_RECEIVED, VOICEMAIL }

    record Fee(String name, BigDecimal amount) {
    }

    record Sample(String roomType, BigDecimal total, Integer guests, Boolean available, LocalDate checkIn,
                  Outcome outcome, Fee mainFee, List<Fee> fees, List<String> notes) {
    }

    private final NullableOutputConverter<Sample> converter = new NullableOutputConverter<>(Sample.class);

    private static List<String> types(JsonNode property) {
        List<String> types = new ArrayList<>();
        JsonNode type = property.get("type");
        if (type == null) return types;
        if (type.isArray()) type.forEach(t -> types.add(t.asString()));
        else types.add(type.asString());
        return types;
    }

    private static boolean allowsNull(JsonNode property) {
        if (types(property).contains("null")) return true;
        JsonNode anyOf = property.get("anyOf");
        if (anyOf != null) {
            for (JsonNode alt : anyOf) {
                if (types(alt).contains("null")) return true;
            }
        }
        return false;
    }

    @Test
    void everyPropertyAcceptsNullAndKeepsItsOriginalType() {
        JsonNode schema = JSON.readTree(converter.getJsonSchema());
        JsonNode properties = schema.get("properties");
        Iterator<String> names = properties.propertyNames().iterator();
        List<String> seen = new ArrayList<>();
        while (names.hasNext()) {
            String name = names.next();
            seen.add(name);
            assertThat(allowsNull(properties.get(name))).as("%s is nullable", name).isTrue();
        }
        assertThat(seen).containsExactlyInAnyOrder("roomType", "total", "guests", "available", "checkIn", "outcome",
                "mainFee", "fees", "notes");
        assertThat(types(properties.get("roomType"))).contains("string");
        assertThat(types(properties.get("guests"))).contains("integer");
        assertThat(types(properties.get("available"))).contains("boolean");
    }

    @Test
    void enumsAcceptNullAsAValue() {
        JsonNode outcome = JSON.readTree(converter.getJsonSchema()).get("properties").get("outcome");
        List<String> values = new ArrayList<>();
        outcome.get("enum").forEach(v -> values.add(v.isNull() ? null : v.asString()));
        assertThat(values).containsExactlyInAnyOrder("OFFER_RECEIVED", "VOICEMAIL", null);
    }

    @Test
    void nestedObjectsAndArrayItemsAreWidenedToo() {
        JsonNode schema = JSON.readTree(converter.getJsonSchema());
        // Nested records live in $defs: their properties are widened in place...
        JsonNode fee = schema.get("$defs").get("Fee");
        assertThat(allowsNull(fee.get("properties").get("amount"))).isTrue();
        assertThat(allowsNull(fee.get("properties").get("name"))).isTrue();
        assertThat(fee.path("additionalProperties").asBoolean(true)).isFalse();
        // ...and a $ref property becomes "the referenced object, or null".
        JsonNode mainFee = schema.get("properties").get("mainFee");
        assertThat(mainFee.get("anyOf").get(0).get("$ref").asString()).isEqualTo("#/$defs/Fee");
        assertThat(allowsNull(mainFee)).isTrue();
        assertThat(schema.get("properties").get("fees").get("items").get("$ref").asString()).isEqualTo("#/$defs/Fee");
    }

    @Test
    void strictModeIsKept() {
        JsonNode schema = JSON.readTree(converter.getJsonSchema());
        // Strict structured output needs every property required and no extra properties.
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(r -> required.add(r.asString()));
        assertThat(required).hasSize(schema.get("properties").size());
    }

    @Test
    void schemaMapMatchesTheSchemaString() {
        Map<String, Object> map = converter.getJsonSchemaMap();
        JsonNode fromMap = JSON.valueToTree(map);
        assertThat(fromMap).isEqualTo(JSON.readTree(converter.getJsonSchema()));
    }

    @Test
    void convertsNullsAsUnknownRatherThanZero() {
        Sample s = converter.convert("""
                {"roomType": null, "total": null, "guests": null, "available": true, "checkIn": "2026-11-20",
                 "outcome": null, "mainFee": null, "fees": [], "notes": null}
                """);
        assertThat(s.total()).isNull();
        assertThat(s.guests()).isNull();
        assertThat(s.outcome()).isNull();
        assertThat(s.available()).isTrue();
        assertThat(s.checkIn()).isEqualTo(LocalDate.parse("2026-11-20"));
    }
}
