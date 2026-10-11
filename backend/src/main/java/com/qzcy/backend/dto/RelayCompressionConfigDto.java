package com.qzcy.backend.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public record RelayCompressionConfigDto(Boolean gzipEnabled, Boolean zstdEnabled,
        @JsonDeserialize(using = BudgetDeserializer.class) Integer memoryBudgetMb) {
    public static class BudgetDeserializer extends StdDeserializer<Integer> {
        public BudgetDeserializer() { super(Integer.class); }
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) {
                throw InvalidFormatException.from(parser, "memoryBudgetMb must be an integer", parser.getText(), Integer.class);
            }
            return parser.getIntValue();
        }
    }
}
