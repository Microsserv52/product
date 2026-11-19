package Microsserv52.product.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

public final class ContractAssertions {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private ContractAssertions() {
    }

    public static void assertValid(String schemaName, String payload) {
        JsonSchema schema = FACTORY.getSchema(readSchema(schemaName));
        try {
            JsonNode node = MAPPER.readTree(payload);
            Set<ValidationMessage> errors = schema.validate(node);
            if (!errors.isEmpty()) {
                throw new AssertionError("Contract violations for " + schemaName + ": " + errors);
            }
        } catch (IOException exception) {
            throw new AssertionError("Invalid JSON payload", exception);
        }
    }

    private static JsonNode readSchema(String schemaName) {
        try (InputStream stream = ContractAssertions.class.getResourceAsStream(
                "/contract/schemas/" + schemaName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing contract schema " + schemaName);
            }
            return MAPPER.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read contract schema " + schemaName, exception);
        }
    }
}
