package com.denkitronik.clienteservice.integration;

import com.denkitronik.clienteservice.domain.entities.Cliente;
import com.denkitronik.clienteservice.domain.entities.Region;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;                             // Spring Boot 4.x
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;  // Spring Boot 4.x
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;  // Spring Boot 4.x: import explícito evita ambigüedad con org.junit.jupiter.api.MediaType
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate   // Spring Boot 4.x: requerido — TestRestTemplate ya no se auto-registra con @SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E tests — flujo completo HTTP → PostgreSQL con Testcontainers")
class ClienteIntegrationTest {

    // Testcontainers levanta PostgreSQL real antes de los tests
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;  // para sembrar datos iniciales

    private static final String BASE    = "/api/v1/cliente-service";
    private static final AtomicBoolean seeded = new AtomicBoolean(false);
    private static Long regionId;   // compartido entre tests (estático)
    private static Long idCreado;   // compartido entre tests (estático)

    @BeforeEach
    void seedDatosIniciales() {
        // Se ejecuta antes de cada test, pero solo siembra datos la primera vez
        if (seeded.compareAndSet(false, true)) {
            jdbcTemplate.execute(
                "INSERT INTO regiones(nombre) VALUES ('América del Sur')");
            regionId = jdbcTemplate.queryForObject(
                "SELECT id FROM regiones WHERE nombre = 'América del Sur'",
                Long.class);
        }
    }

    @Test @Order(1)
    @DisplayName("POST /clientes → 201 crea el cliente y retorna el ID")
    void crearCliente_debeRetornar201() {
        Region region = new Region();
        region.setId(regionId);

        Cliente nuevo = new Cliente();
        nuevo.setNombre("Margaret");
        nuevo.setApellido("Hamilton");
        nuevo.setEmail("margaret@apollo.nasa");
        nuevo.setRegion(region);

        ResponseEntity<Cliente> respuesta =
                restTemplate.postForEntity(BASE + "/clientes", nuevo, Cliente.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().getId()).isNotNull();
        assertThat(respuesta.getBody().getNombre()).isEqualTo("Margaret");

        idCreado = respuesta.getBody().getId();  // guardamos para los tests siguientes
    }

    @Test @Order(2)
    @DisplayName("GET /clientes/{id} → 200 encuentra el cliente creado")
    void buscarClienteCreado_debeRetornar200() {
        ResponseEntity<Cliente> respuesta =
                restTemplate.getForEntity(BASE + "/clientes/" + idCreado, Cliente.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().getEmail()).isEqualTo("margaret@apollo.nasa");
    }

    @Test @Order(3)
    @DisplayName("GET /clientes → 200 la lista incluye el cliente creado")
    void listarClientes_debeIncluirClienteCreado() {
        ResponseEntity<String> respuesta =
                restTemplate.getForEntity(BASE + "/clientes", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("margaret@apollo.nasa");
    }

    @Test @Order(4)
    @DisplayName("POST /clientes inválido → 400 con la lista de errores")
    void crearClienteInvalido_debeRetornar400() {
        Cliente invalido = new Cliente();
        invalido.setNombre("");
        invalido.setApellido("");
        invalido.setEmail("no-es-email");

        ResponseEntity<String> respuesta =
                restTemplate.postForEntity(BASE + "/clientes", invalido, String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).contains("errors");
    }

    @Test @Order(5)
    @DisplayName("PUT /clientes/{id} → 201 actualiza los datos")
    void actualizarCliente_debeRetornar201() {
        Region region = new Region();
        region.setId(regionId);

        Cliente actualizado = new Cliente();
        actualizado.setNombre("Margaret");
        actualizado.setApellido("Hamilton-Updated");
        actualizado.setEmail("margaret@apollo.nasa");
        actualizado.setRegion(region);

        HttpEntity<Cliente> entity =
                new HttpEntity<>(actualizado, headersJson());

        ResponseEntity<Cliente> respuesta =
                restTemplate.exchange(BASE + "/clientes/" + idCreado,
                        HttpMethod.PUT, entity, Cliente.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().getApellido()).isEqualTo("Hamilton-Updated");
    }

    @Test @Order(6)
    @DisplayName("GET /clientes/9999 → 404 ID inexistente")
    void idInexistente_debeRetornar404() {
        ResponseEntity<String> respuesta =
                restTemplate.getForEntity(BASE + "/clientes/9999", String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @Order(7)
    @DisplayName("DELETE /clientes/{id} → 204 y luego GET confirma 404")
    void eliminarCliente_debeRetornar204YLuego404() {
        restTemplate.delete(BASE + "/clientes/" + idCreado);

        ResponseEntity<String> respuesta =
                restTemplate.getForEntity(BASE + "/clientes/" + idCreado, String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders headersJson() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
