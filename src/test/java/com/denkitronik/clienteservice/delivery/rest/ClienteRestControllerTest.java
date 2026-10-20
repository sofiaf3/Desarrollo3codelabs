package com.denkitronik.clienteservice.delivery.rest;

import com.denkitronik.clienteservice.domain.exception.ClienteNotFoundException;
import com.denkitronik.clienteservice.domain.entities.Cliente;
import com.denkitronik.clienteservice.domain.entities.Region;
import com.denkitronik.clienteservice.domain.services.IClienteService;
import tools.jackson.databind.ObjectMapper;               // Spring Boot 4.x
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;    // Spring Boot 4.x
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClienteRestController.class)
@DisplayName("Integration tests (web slice) — ClienteRestController con MockMvc")
class ClienteRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean                         // reemplaza IClienteService en el contexto de Spring
    private IClienteService clienteService;

    @Autowired
    private ObjectMapper objectMapper;  // para serializar objetos a JSON

    private Cliente cliente;
    private static final String BASE = "/api/v1/cliente-service";

    @BeforeEach
    void setUp() {
        Region region = new Region();
        region.setId(4L);
        region.setNombre("Europa");

        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Ada");
        cliente.setApellido("Lovelace");
        cliente.setEmail("ada@babbage.uk");
        cliente.setRegion(region);
    }

    @Test
    @DisplayName("GET /clientes/1 → 200 con el cliente correcto")
    void buscarCliente_idExistente_debeRetornar200ConCliente() throws Exception {
        when(clienteService.findById(1L)).thenReturn(cliente);

        mockMvc.perform(get(BASE + "/clientes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Ada"))
                .andExpect(jsonPath("$.apellido").value("Lovelace"));
    }

    @Test
    @DisplayName("GET /clientes/999 → 404 cuando el cliente no existe")
    void buscarCliente_idInexistente_debeRetornar404() throws Exception {
        when(clienteService.findById(999L))
                .thenThrow(new ClienteNotFoundException(999L));

        mockMvc.perform(get(BASE + "/clientes/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /clientes con nombre vacío → 400 con lista de errores")
    void crearCliente_nombreVacio_debeRetornar400() throws Exception {
        cliente.setNombre("");  // viola @NotEmpty @Size(min=2)

        mockMvc.perform(post(BASE + "/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cliente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("nombre"))));
    }

    @Test
    @DisplayName("POST /clientes con email inválido → 400")
    void crearCliente_emailInvalido_debeRetornar400() throws Exception {
        cliente.setEmail("esto-no-es-un-email");

        mockMvc.perform(post(BASE + "/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cliente)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /clientes/1 válido → 201 Created")
    void actualizarCliente_valido_debeRetornar201() throws Exception {
        when(clienteService.update(eq(1L), any(Cliente.class))).thenReturn(cliente);

        mockMvc.perform(put(BASE + "/clientes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cliente)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /clientes/999 → 404 cuando el cliente no existe")
    void actualizarCliente_idInexistente_debeRetornar404() throws Exception {
        when(clienteService.update(eq(999L), any(Cliente.class)))
                .thenThrow(new ClienteNotFoundException(999L));

        mockMvc.perform(put(BASE + "/clientes/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cliente)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /clientes → 200 con la lista de clientes")
    void listarClientes_debeRetornar200ConLista() throws Exception {
        when(clienteService.findAll()).thenReturn(List.of(cliente));

        mockMvc.perform(get(BASE + "/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ada@babbage.uk"));
    }

    @Test
    @DisplayName("GET /clientes/page/0 → 200 con content y metadatos de página")
    void listarClientesPaginado_debeRetornar200ConPagina() throws Exception {
        when(clienteService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 4), 1));

        mockMvc.perform(get(BASE + "/clientes/page/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Ada"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /clientes válido → 201 Created con el cliente")
    void crearCliente_valido_debeRetornar201() throws Exception {
        when(clienteService.save(any(Cliente.class))).thenReturn(cliente);

        mockMvc.perform(post(BASE + "/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cliente)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /clientes/1 → 204 No Content")
    void eliminarCliente_idExistente_debeRetornar204() throws Exception {
        mockMvc.perform(delete(BASE + "/clientes/1"))
                .andExpect(status().isNoContent());

        verify(clienteService, times(1)).delete(1L);
    }

    @Test
    @DisplayName("DELETE /clientes/999 → 404 cuando el cliente no existe")
    void eliminarCliente_idInexistente_debeRetornar404() throws Exception {
        doThrow(new ClienteNotFoundException(999L)).when(clienteService).delete(999L);

        mockMvc.perform(delete(BASE + "/clientes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
