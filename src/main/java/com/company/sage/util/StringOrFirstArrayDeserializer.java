package com.company.sage.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * Accepts a JSON string or a string array; arrays use the first non-null element.
 */
public class StringOrFirstArrayDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return parser.getText();
        }
        if (token == JsonToken.START_ARRAY) {
            String first = null;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.VALUE_STRING) {
                    String value = parser.getText();
                    if (first == null && value != null) {
                        first = value;
                    }
                } else if (parser.currentToken() != JsonToken.VALUE_NULL) {
                    parser.skipChildren();
                }
            }
            return first;
        }
        return (String) context.handleUnexpectedToken(String.class, parser);
    }
}
