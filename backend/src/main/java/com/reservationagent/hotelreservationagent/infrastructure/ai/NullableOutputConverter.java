package com.reservationagent.hotelreservationagent.infrastructure.ai;

import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * A {@link BeanOutputConverter} whose JSON schema lets every property be {@code null}.
 *
 * <p>OpenAI structured output runs in strict mode: every property must be present, and Spring AI's
 * generated schema gives each one a non-null type. The model then cannot say "not stated" and fills
 * in 0, "" or a made-up value instead, which for prices and dates is a wrong answer rather than an
 * unknown one. Widening each type to {@code [type, "null"]} keeps strict mode and lets the model
 * return {@code null}, which the application treats as unknown.
 */
class NullableOutputConverter<T> extends BeanOutputConverter<T> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String nullableSchema;

    NullableOutputConverter(Class<T> type) {
        super(type);
        ObjectNode schema = (ObjectNode) JSON.readTree(super.getJsonSchema());
        widen(schema);
        this.nullableSchema = schema.toPrettyString();
    }

    @Override
    public String getJsonSchema() {
        return nullableSchema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getJsonSchemaMap() {
        return JSON.readValue(nullableSchema, Map.class);
    }

    /** Makes every property of this object schema (and of nested object schemas) nullable. */
    private static void widen(ObjectNode schema) {
        for (String container : new String[] {"$defs", "definitions"}) {
            if (schema.get(container) instanceof ObjectNode defs) {
                defs.properties().forEach(e -> {
                    if (e.getValue() instanceof ObjectNode def) widen(def);
                });
            }
        }
        if (!(schema.get("properties") instanceof ObjectNode properties)) {
            return;
        }
        properties.properties().forEach(e -> {
            if (!(e.getValue() instanceof ObjectNode property)) return;
            if (property.get("items") instanceof ObjectNode items) widen(items);
            widen(property);
            properties.set(e.getKey(), nullable(property));
        });
    }

    private static ObjectNode nullable(ObjectNode property) {
        JsonNode type = property.get("type");
        if (type == null) {
            // A $ref or anyOf: offer null as one more alternative.
            ObjectNode wrapper = JSON.createObjectNode();
            ArrayNode anyOf = wrapper.putArray("anyOf");
            anyOf.add(property);
            anyOf.addObject().put("type", "null");
            return wrapper;
        }
        if (type.isString() && !"null".equals(type.asString())) {
            ArrayNode types = JSON.createArrayNode().add(type.asString()).add("null");
            property.set("type", types);
        } else if (type instanceof ArrayNode types && !containsNull(types)) {
            types.add("null");
        }
        if (property.get("enum") instanceof ArrayNode values && !containsNull(values)) {
            values.addNull();
        }
        return property;
    }

    private static boolean containsNull(ArrayNode values) {
        for (JsonNode v : values) {
            if (v.isNull() || (v.isString() && "null".equals(v.asString()))) return true;
        }
        return false;
    }
}
