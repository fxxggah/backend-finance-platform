package com.finance.ai.extractor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.ai.exception.AiException;
import com.finance.ai.model.ExtractedTransaction;
import com.finance.common.enums.TransactionType;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExtractionParser {
    private static final Set<String> FIELDS = Set.of("type", "amount", "date", "installments",
            "categoryRef", "accountRef", "creditCardRef");
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ExtractedTransaction parse(String text) {
        try {
            if (text == null || text.length() > 16_384) throw AiException.invalidResponse();
            JsonNode root = JSON.readTree(text);
            if (root == null || !root.isObject() || root.size() != 1 || !root.path("events").isArray()
                    || root.get("events").size() > 3) throw AiException.invalidResponse();
            JsonNode events = root.get("events");
            for (JsonNode event : events) {
                if (!event.isObject() || event.size() != 7 || !FIELDS.stream().allMatch(event::has)) {
                    throw AiException.invalidResponse();
                }
            }
            if (events.isEmpty()) throw new AiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "UNCLEAR_AUDIO", "Não foi possível identificar uma transação. Grave novamente ou preencha manualmente.");
            if (events.size() > 1) throw new AiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MULTIPLE_TRANSACTIONS", "Grave somente uma transação por áudio.");
            JsonNode event = events.get(0);
            String typeText = string(event, "type");
            TransactionType type = typeText == null ? null : TransactionType.valueOf(typeText);
            String amountText = string(event, "amount");
            if (amountText != null && !amountText.matches("[0-9]{1,9}\\.[0-9]{2}")) throw AiException.invalidResponse();
            BigDecimal amount = amountText == null ? null : new BigDecimal(amountText);
            if (amount != null && amount.signum() <= 0) throw AiException.invalidResponse();
            String dateText = string(event, "date");
            if (dateText != null && !dateText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw AiException.invalidResponse();
            LocalDate date = dateText == null ? null : LocalDate.parse(dateText);
            JsonNode number = event.get("installments");
            if (!number.isNull() && (!number.isIntegralNumber() || !number.canConvertToInt()
                    || number.intValue() < 1 || number.intValue() > 24)) throw AiException.invalidResponse();
            Integer installments = number.isNull() ? null : number.intValue();
            String category = string(event, "categoryRef"), account = string(event, "accountRef"), card = string(event, "creditCardRef");
            if (account != null && card != null || card != null && type == TransactionType.INCOME
                    || installments != null && installments > 1 && (account != null || type == TransactionType.INCOME)
                    || amount != null && installments != null && amount.compareTo(BigDecimal.valueOf(installments, 2)) < 0) {
                throw AiException.invalidResponse();
            }
            return new ExtractedTransaction(type, amount, date, installments, category, account, card);
        } catch (AiException ex) { throw ex; }
        catch (Exception ex) { throw AiException.invalidResponse(); }
    }

    private String string(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        if (!value.isTextual()) throw AiException.invalidResponse();
        return value.textValue();
    }

    public static Map<String, Object> schema() {
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
        Map<String, Object> event = Map.of("type", "object", "additionalProperties", false,
                "required", FIELDS.stream().sorted().toList(), "properties", Map.of(
                        "type", Map.of("type", List.of("string", "null"), "enum", Arrays.asList("INCOME", "EXPENSE", null)),
                        "amount", nullableString, "date", nullableString,
                        "installments", Map.of("type", List.of("integer", "null"), "minimum", 1, "maximum", 24),
                        "categoryRef", nullableString, "accountRef", nullableString, "creditCardRef", nullableString));
        return Map.of("type", "object", "additionalProperties", false, "required", List.of("events"),
                "properties", Map.of("events", Map.of("type", "array", "minItems", 0, "maxItems", 3, "items", event)));
    }
}
