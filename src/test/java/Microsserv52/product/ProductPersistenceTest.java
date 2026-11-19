package Microsserv52.product;

import java.util.UUID;

import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductPersistenceTest {
    private static final UUID ACCOUNT = UUID.fromString("0195ae95-5be7-7dd3-b35d-7a7d87c404fb");
    private static final String ADMIN = "Bearer " + TestTokens.adminToken(ACCOUNT);

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName.parse("postgres:17.12-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("platform.cache.enabled", () -> false);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void writesAndReadsBackThroughFlywayManagedSchema() throws Exception {
        MvcResult created = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Tomato","price":10.12,"unit":"kg"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
            created.getResponse().getContentAsString(), "$.id"));

        assertThat(productRepository.findById(id)).isPresent();
        mockMvc.perform(get("/products/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/products/" + id).header("Authorization", ADMIN))
            .andExpect(status().isNoContent());
        assertThat(productRepository.findById(id)).isEmpty();
    }
}
