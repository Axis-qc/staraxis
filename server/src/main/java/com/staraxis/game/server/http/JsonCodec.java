package com.staraxis.game.server.http;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonCodec() {
    }

    public static <T> T read(byte[] bodyBytes, Class<T> type) throws IOException {
        return MAPPER.readValue(bodyBytes, type);
    }

    public static byte[] write(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsBytes(value);
    }
}
