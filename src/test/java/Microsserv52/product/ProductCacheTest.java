package Microsserv52.product;

import java.util.UUID;

import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductCacheTest {
    private static final UUID ACCOUNT = UUID.fromString("0195ae95-5be7-7dd3-b35d-7a7d87c404fb");
    private static final String ADMIN = "Bearer " + TestTokens.adminToken(ACCOUNT);
    private static final String TOMATO = """
        {"name":"Tomato","price":10.12,"unit":"kg"}
        """;

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName.parse("postgres:17.12-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4.12-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("platform.cache.enabled", () -> true);
        registry.add("spring.data.redis.url",
            () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/0");
        registry.add("management.health.redis.enabled", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void cachesProductReadsAndInvalidatesOnWrite() throws Exception {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        MvcResult created = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated())
            .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(
            created.getResponse().getContentAsString(), "$.id");

        String key = "product:product::" + id;
        assertThat(redis.hasKey(key)).isFalse();
        mockMvc.perform(get("/products/" + id)).andExpect(status().isOk());
        assertThat(redis.hasKey(key)).isTrue();

        productRepository.deleteById(UUID.fromString(id));
        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Tomato"));

        cacheManager.getCache("product").clear();
        assertThat(redis.hasKey(key)).isFalse();
        mockMvc.perform(get("/products/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void creatingAProductInvalidatesTheListCache() throws Exception {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        productRepository.deleteAll();

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        assertThat(redis.hasKey("product:product-list::all")).isTrue();

        mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TOMATO))
            .andExpect(status().isCreated());
        assertThat(redis.hasKey("product:product-list::all")).isFalse();

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
