package Microsserv52.product.api;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import Microsserv52.product.domain.ImageUploadTicketRepository;
import Microsserv52.product.domain.ProductImageRepository;
import Microsserv52.product.domain.ProductRepository;
import Microsserv52.product.service.image.HttpImageStorage;
import Microsserv52.product.service.image.ImageStorage;
import Microsserv52.product.support.ContractAssertions;
import Microsserv52.product.support.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductImageApiTest {
    private static final UUID ACCOUNT = UUID.fromString("0195ae95-5be7-7dd3-b35d-7a7d87c404fb");
    private static final String ADMIN = "Bearer " + TestTokens.adminToken(ACCOUNT);
    private static final String USER = "Bearer " + TestTokens.userToken(ACCOUNT);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static HttpServer storageServer;
    private static ConcurrentHashMap<String, byte[]> objects;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository imageRepository;

    @Autowired
    private ImageUploadTicketRepository ticketRepository;

    @Autowired
    private ImageStorage imageStorage;

    @BeforeAll
    static void startStorage() throws IOException {
        objects = new ConcurrentHashMap<>();
        storageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        storageServer.createContext("/", new FakeStorageHandler(objects));
        storageServer.start();
    }

    @AfterAll
    static void stopStorage() {
        if (storageServer != null) {
            storageServer.stop(0);
        }
    }

    @BeforeEach
    void resetStorage() {
        objects.clear();
        imageRepository.deleteAll();
        ticketRepository.deleteAll();
        productRepository.deleteAll();
        ReflectionTestUtils.setField(imageStorage, "baseUrl",
            "http://127.0.0.1:" + storageServer.getAddress().getPort());
    }

    @Test
    void imageWritesRequireAdmin() throws Exception {
        String id = createProduct();
        String upload = uploadJson("image/jpeg", 100, 4, 4);

        mockMvc.perform(post("/products/" + id + "/image/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(upload))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upload))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + UUID.randomUUID() + "\",\"alt\":\"x\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/products/" + id + "/image")
                .header("Authorization", USER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alt\":\"x\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/products/" + id + "/image")
                .header("Authorization", USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void uploadAttachAndDeleteRoundTrip() throws Exception {
        String id = createProduct();
        byte[] jpeg = jpeg(8, 6, 0x00);

        MvcResult upload = mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/jpeg", jpeg.length, 8, 6)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uploadUrl").exists())
            .andExpect(jsonPath("$.uploadHeaders.If-None-Match").value("*"))
            .andReturn();
        String body = upload.getResponse().getContentAsString();
        String uploadId = com.jayway.jsonpath.JsonPath.read(body, "$.uploadId");
        String uploadUrl = com.jayway.jsonpath.JsonPath.read(body, "$.uploadUrl");

        HttpResponse<byte[]> put = CLIENT.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(jpeg))
                .header("Content-Type", "image/jpeg")
                .header("If-None-Match", "*")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(put.statusCode()).isEqualTo(201);

        MvcResult attached = mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + uploadId + "\",\"alt\":\"Fresh ripe tomatoes\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image.alt").value("Fresh ripe tomatoes"))
            .andExpect(jsonPath("$.image.width").value(8))
            .andExpect(jsonPath("$.image.height").value(6))
            .andExpect(jsonPath("$.image.url").value(
                org.hamcrest.Matchers.startsWith("/media/media/products/" + id + "/")))
            .andReturn();
        ContractAssertions.assertValid("product.schema.json",
            attached.getResponse().getContentAsString());

        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image.alt").value("Fresh ripe tomatoes"));

        mockMvc.perform(patch("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alt\":\"Ripe tomatoes on the vine\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image.alt").value("Ripe tomatoes on the vine"));

        MvcResult deleted = mockMvc.perform(delete("/products/" + id + "/image")
                .header("Authorization", ADMIN))
            .andExpect(status().isOk())
            .andReturn();
        String deletedBody = deleted.getResponse().getContentAsString();
        assertThat(deletedBody).contains("\"image\":null");
        assertThat(objects.keySet().stream().anyMatch(key -> key.startsWith("media/"))).isFalse();
    }

    @Test
    void attachRejectsInvalidTicketAndContent() throws Exception {
        String id = createProduct();

        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + UUID.randomUUID() + "\",\"alt\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        byte[] jpeg = jpeg(8, 6, 0x01);
        String uploadId = uploadAndPut(id, jpeg);

        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + uploadId + "\",\"alt\":\"first\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + uploadId + "\",\"alt\":\"replay\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void contentMismatchIsRejected() throws Exception {
        String id = createProduct();
        byte[] other = png(8, 6, 0x02);

        MvcResult upload = mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/jpeg", other.length, 8, 6)))
            .andExpect(status().isCreated())
            .andReturn();
        String body = upload.getResponse().getContentAsString();
        String uploadId = com.jayway.jsonpath.JsonPath.read(body, "$.uploadId");
        String uploadUrl = com.jayway.jsonpath.JsonPath.read(body, "$.uploadUrl");
        CLIENT.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(other))
                .header("Content-Type", "image/png")
                .header("If-None-Match", "*")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());

        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + uploadId + "\",\"alt\":\"wrong bytes\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void attachingTwiceReplacesThePreviousImage() throws Exception {
        String id = createProduct();
        byte[] first = jpeg(8, 6, 0x03);
        byte[] second = jpeg(9, 7, 0x04);
        String firstUpload = uploadAndPut(id, first);
        String secondUpload = uploadAndPut(id, second);

        mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + firstUpload + "\",\"alt\":\"first\"}"))
            .andExpect(status().isOk());
        MvcResult replaced = mockMvc.perform(post("/products/" + id + "/image")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uploadId\":\"" + secondUpload + "\",\"alt\":\"second\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.image.alt").value("second"))
            .andExpect(jsonPath("$.image.width").value(9))
            .andReturn();

        assertThat(replaced.getResponse().getContentAsString()).contains("\"height\":7");
        assertThat(objects.keySet().stream()
                .filter(key -> key.startsWith("media/"))
                .count())
            .isEqualTo(1);
    }

    @Test
    void unknownProductsReturnNotFound() throws Exception {
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(post("/products/" + missing + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/jpeg", 100, 4, 4)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(delete("/products/" + missing + "/image")
                .header("Authorization", ADMIN))
            .andExpect(status().isNotFound());
    }

    @Test
    void uploadsAreValidated() throws Exception {
        String id = createProduct();

        mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/gif", 100, 4, 4)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/jpeg", 5L * 1024 * 1024 + 1, 4, 4)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/products/" + id + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadJson("image/jpeg", 100, 4097, 4)))
            .andExpect(status().isBadRequest());
    }

    private String createProduct() throws Exception {
        MvcResult result = mockMvc.perform(post("/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tomato\",\"price\":10.12,\"unit\":\"kg\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String uploadJson(String contentType, long size, int width, int height) {
        return "{\"fileName\":\"tomato.jpg\",\"contentType\":\"" + contentType
            + "\",\"size\":" + size + ",\"width\":" + width + ",\"height\":" + height + "}";
    }

    private String uploadAndPut(String productId, byte[] bytes) throws Exception {
        String contentType = bytes[0] == (byte) 0x89 ? "image/png" : "image/jpeg";
        int[] dims = dimensions(bytes, contentType);
        MvcResult upload = mockMvc.perform(post("/products/" + productId + "/image/uploads")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileName\":\"tomato." + (contentType.equals("image/png") ? "png" : "jpg")
                    + "\",\"contentType\":\"" + contentType + "\",\"size\":" + bytes.length
                    + ",\"width\":" + dims[0] + ",\"height\":" + dims[1] + "}"))
            .andExpect(status().isCreated())
            .andReturn();
        String body = upload.getResponse().getContentAsString();
        String uploadId = com.jayway.jsonpath.JsonPath.read(body, "$.uploadId");
        String uploadUrl = com.jayway.jsonpath.JsonPath.read(body, "$.uploadUrl");
        CLIENT.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", contentType)
                .header("If-None-Match", "*")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
        return uploadId;
    }

    private int[] dimensions(byte[] bytes, String contentType) throws IOException {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        return new int[]{image.getWidth(), image.getHeight()};
    }

    private byte[] jpeg(int width, int height, int seed) {
        return write(width, height, seed, "jpg");
    }

    private byte[] png(int width, int height, int seed) {
        return write(width, height, seed, "png");
    }

    private byte[] write(int width, int height, int seed, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (seed + x * 31 + y * 17) & 0xFFFFFF);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static final class FakeStorageHandler implements HttpHandler {
        private final ConcurrentHashMap<String, byte[]> objects;

        FakeStorageHandler(ConcurrentHashMap<String, byte[]> objects) {
            this.objects = objects;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                route(exchange);
            } finally {
                exchange.close();
            }
        }

        private void route(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/upload/") && "PUT".equals(method)) {
                put(exchange, path.substring("/upload/".length()), true);
            } else if (path.startsWith("/object/") && "GET".equals(method)) {
                get(exchange, path.substring("/object/".length()));
            } else if (path.startsWith("/object/") && "PUT".equals(method)) {
                put(exchange, path.substring("/object/".length()), true);
            } else if (path.startsWith("/object/") && "DELETE".equals(method)) {
                objects.remove(path.substring("/object/".length()));
                send(exchange, 204, new byte[0]);
            } else if (path.startsWith("/media/") && "GET".equals(method)) {
                get(exchange, path.substring("/media/".length()));
            } else {
                send(exchange, 404, "{}".getBytes(StandardCharsets.UTF_8));
            }
        }

        private void put(HttpExchange exchange, String key, boolean ifNoneMatch) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (ifNoneMatch && objects.containsKey(key)) {
                send(exchange, 412, "{}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            objects.put(key, body);
            send(exchange, 201, new byte[0]);
        }

        private void get(HttpExchange exchange, String key) throws IOException {
            byte[] body = objects.get(key);
            if (body == null) {
                send(exchange, 404, "{}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(exchange, 200, body);
        }

        private void send(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.sendResponseHeaders(status, body.length == 0 && status != 200 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        }
    }
}
