package com.qzcy.backend.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public record RelayPromptRuleConfigDto(Boolean enabled, String content,
        @JsonDeserialize(using = ThresholdDeserializer.class) Integer autoBanThreshold) {
    public RelayPromptRuleConfigDto {
        if (autoBanThreshold == null) autoBanThreshold = 3;
    }

    public RelayPromptRuleConfigDto(Boolean enabled, String content) {
        this(enabled, content, 3);
    }

    public static class ThresholdDeserializer extends StdDeserializer<Integer> {
        public ThresholdDeserializer() { super(Integer.class); }
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) {
                throw InvalidFormatException.from(parser, "autoBanThreshold must be an integer", parser.getText(), Integer.class);
            }
            return parser.getIntValue();
        }
    }
}
