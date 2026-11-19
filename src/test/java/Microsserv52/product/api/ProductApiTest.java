package Microsserv52.product.api;

import java.util.UUID;

import Microsserv52.product.domain.IdempotencyRepository;
import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.support.ContractAssertions;
import Microsserv52.product.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {
    private static final UUID ACCOUNT = UUID.fromString("0195ae95-5be7-7dd3-b35d-7a7d87c404fb");
    private static final String ADMIN = "Bearer " + TestTokens.adminToken(ACCOUNT);
    private static final String USER = "Bearer " + TestTokens.userToken(ACCOUNT);
    private static final String TOMATO = """
        {"name":"Tomato","price":10.12,"unit":"kg"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void catalogueReadsArePublicAndWritesRequireAdmin() throws Exception {
        MvcResult created = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.name").value("Tomato"))
            .andReturn();
        ContractAssertions.assertValid("product.schema.json",
            created.getResponse().getContentAsString());

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/products/" + idOf(created)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unit").value("kg"));

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/products")
                .header("Authorization", USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validationFailuresUseTheStableErrorEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","price":0,"unit":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.length()").value(3))
            .andReturn();
        ContractAssertions.assertValid("error.schema.json",
            result.getResponse().getContentAsString());
    }

    @Test
    void malformedJsonAndUnknownIdsReturnStableErrors() throws Exception {
        MvcResult malformed = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andReturn();
        ContractAssertions.assertValid("error.schema.json",
            malformed.getResponse().getContentAsString());

        mockMvc.perform(get("/products/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void deleteRemovesTheProductAndRequiresAdmin() throws Exception {
        MvcResult created = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated())
            .andReturn();
        String id = idOf(created);

        mockMvc.perform(delete("/products/" + id))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/products/" + id).header("Authorization", USER))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/products/" + id).header("Authorization", ADMIN))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void idempotencyKeyReplaysTheOriginalProduct() throws Exception {
        MvcResult first = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", "create-tomato-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated())
            .andReturn();
        MvcResult second = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", "create-tomato-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyWithAnotherBodyConflicts() throws Exception {
        mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", "create-tomato-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", "create-tomato-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cheese","price":0.62,"unit":"slice"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
            .andReturn();
        ContractAssertions.assertValid("error.schema.json",
            conflict.getResponse().getContentAsString());
    }

    @Test
    void invalidIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .header("Idempotency-Key", "bad key with spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void oversizedRequestsAreRejected() throws Exception {
        String tooLarge = "{\"name\":\"" + "x".repeat(20_000) + "\",\"price\":1,\"unit\":\"kg\"}";
        mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooLarge))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    private String idOf(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.id");
    }
}
