package paucar.service;

/*define el contenedor que agrupa clases, interfaces y subpaquetes relacionados */
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.tpo.demo.entity.TipoCliente;
import com.uade.tpo.demo.entity.TipoDePago;
import com.uade.tpo.demo.entity.dto.VentaRequest;

public class VentasBackend {

    private final String BASE_URL;/*Variableque contiene la URL base del backend (por ejemplo "http://localhost:8080/api"). */

    private final HttpClient http;/*protocolo de transferencia de hipertexto (Hypertext Transfer Protocol)
                                  para enviar y recibir datos Se utiliza principalmente a través de la API
                                  HttpClient para realizar peticiones GET, POST, PUT y DELETE*/

    private final ObjectMapper TraductorJSON;/*ObjectMapper es una clase de la librería Jackson que sirve para convertir
                                  datos JSON en objetos Java y viceversa*/

    private final ClientesService clientesService;
    private final VentaRequest venta;

    // --- Constructor ---
    public VentasBackend(String BASE_URL, ClientesService clientesService,VentaRequest venta) {/*Recibe un parámetro llamado BASE_URL (un String) que debería ser
                                            la URL base del backend */

        this.BASE_URL = Objects.requireNonNull(BASE_URL);/*si el parámetro es null, lanza un NullPointerException
                                                          (excepción en tiempo de ejecución (RuntimeException)
                                                          que ocurre cuando el programa intenta utilizar una
                                                          referencia de objeto que apunta a null)) inmediatamente.
                                                          Esto evita crear una instancia mal configurada */

        this.http = HttpClient.newHttpClient();/*Crea una instancia por defecto de HttpClient (Java 11+), que
                                               vas a usar para hacer GET/POST al backend */

        this.TraductorJSON = new ObjectMapper();/*El campo traductorJson ahora va a contener un ObjectMapper nuevo */

        this.clientesService = Objects.requireNonNull(clientesService);/*valida que el servicio de clientes no sea
                                                                       null, sino lanza una excepción inmediatamente */

        this.venta = Objects.requireNonNull(venta, "venta (VentaRequest) no puede ser null");

        // defensivo: asegurar listas no nulas
        if (this.venta.getIdProductos() == null) {
            this.venta.setIdProductos(new ArrayList<>());
        }
        if (this.venta.getCantidades() == null) {
            this.venta.setCantidades(new ArrayList<>());
        }

    }

    // ============================================================
    //                    VENTAS
    // ============================================================
    public boolean GuardarPedidos(
            Long idCliente,
            List<Long> idProductos,
            List<Integer> cantidades,
            TipoDePago estado,
            String observaciones) {

        try {
            //if (idCliente == null || idProductos.isEmpty() || cantidades.isEmpty()) {/*valida los requerimientos
            //minimos de un pedido valido */
            // System.err.println("Venta inválida");
            // return false;
            // }

            var estadoEfectivo = (estado == null ? TipoDePago.DEBE : estado);
            var obsSeguras = (observaciones == null ? "" : observaciones);

            // *** USAR el VentaRequest inyectado ***
            venta.setIdCliente(idCliente);
            venta.setEstado(estadoEfectivo);
            venta.setObservaciones(obsSeguras);

            // reemplazar contenidos manteniendo alineación
            venta.getIdProductos().clear();
            venta.getCantidades().clear();
            if (idProductos != null && cantidades != null) {
                int n = Math.min(idProductos.size(), cantidades.size());
                for (int i = 0; i < n; i++) {
                    Long idP = idProductos.get(i);
                    Integer cant = cantidades.get(i);
                    if (idP != null && cant != null && cant > 0) {
                        venta.getIdProductos().add(idP);
                        venta.getCantidades().add(cant);
                    }
                }
            }

            // Serializar el MISMO VentaRequest compartido
            String body = TraductorJSON.writeValueAsString(venta);

            var solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/ventas"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;

        } catch (java.io.IOException e) {
            System.err.println("guardarVentaCliente (IO): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("guardarVentaCliente (Interrupted): " + e.getMessage());
            return false;
        }
    }

    
public boolean GuardarPedidoMesas(
            String nombreMesa,
            List<Long> idProductos,
            List<Integer> cantidades,
            TipoDePago estado,
            String observaciones) {

        if (idProductos == null || cantidades == null || idProductos.isEmpty() || cantidades.isEmpty()) {
            System.err.println("Pedido (MESA) inválido por carecer de cantidad o de productos válidos");
            return false;
        }
        if (nombreMesa == null || nombreMesa.isBlank()) {
            System.err.println("Pedido (MESA) inválido: nombreMesa vacío");
            return false;
        }

        // Resolver ID de mesa usando el ClientesService INYECTADO
        Long idMesa = clientesService.obtenerClienteIdPorNombre(nombreMesa);
        if (idMesa == null) {
            System.err.println("Mesa no encontrada: " + nombreMesa);
            return false;
        }
        return GuardarPedidos(idMesa, idProductos, cantidades, estado, observaciones);
    }

    public List<java.util.Map<String, Object>> cargarVentasDelDia(LocalDate fecha) {
        try {
            var solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/ventas?fecha=" + fecha.toString()))
                    .GET()
                    .build();

            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var array = TraductorJSON.readTree(response.body());
                var out = new ArrayList<java.util.Map<String, Object>>();

                if (array.isArray()) {
                    for (var n : array) {
                        String nombre
                                = n.hasNonNull("nombreEmpresa") ? n.get("nombreEmpresa").asText()
                                : n.hasNonNull("nombreCliente") ? n.get("nombreCliente").asText()
                                : n.hasNonNull("nombreMesa") ? n.get("nombreMesa").asText()
                                : "";
                        if ((nombre == null || nombre.isBlank())
                                && n.hasNonNull("cliente")
                                && n.get("cliente").isObject()
                                && n.get("cliente").hasNonNull("nombre")) {
                            nombre = n.get("cliente").get("nombre").asText("").trim();
                        } else {
                            nombre = (nombre == null ? "" : nombre.trim());
                        }
                        var desc = n.hasNonNull("descripcion") ? n.get("descripcion").asText() : "";
                        var obs = n.hasNonNull("observaciones") ? n.get("observaciones").asText() : "";
                        BigDecimal monto = BigDecimal.ZERO;
                        if (n.hasNonNull("monto")) {
                            monto = new BigDecimal(n.get("monto").asText())
                                    .setScale(2, RoundingMode.HALF_UP);
                        }
                        TipoDePago estado = TipoDePago.EFECTIVO;
                        if (n.hasNonNull("estado")) {
                            try { estado = TipoDePago.valueOf(n.get("estado").asText()); } catch (Exception ignore) {}
                        }
                        Long idCliente = null;
                        if (n.hasNonNull("idCliente")) {
                            idCliente = n.get("idCliente").asLong();
                        } else if (n.hasNonNull("cliente")
                                && n.get("cliente").isObject()
                                && n.get("cliente").hasNonNull("idCliente")) {
                            idCliente = n.get("cliente").get("idCliente").asLong();
                        }
                        TipoCliente tipoCli = null;
                        if (n.hasNonNull("tipoCliente")) {
                            tipoCli = TipoCliente.valueOf(n.get("tipoCliente").asText());
                        } else if (n.hasNonNull("cliente")
                                && n.get("cliente").isObject()
                                && n.get("cliente").hasNonNull("tipoCliente")) {
                            tipoCli = TipoCliente.valueOf(n.get("cliente").get("tipoCliente").asText());
                        } else if (nombre.toLowerCase().startsWith("mesa ")) {
                            tipoCli = TipoCliente.MESA;
                        }

                        var fila = new java.util.HashMap<String, Object>();
                        fila.put("nombre", nombre);
                        fila.put("descripcion", desc);
                        fila.put("monto", monto);
                        fila.put("estado", estado);
                        fila.put("observaciones", obs);
                        fila.put("idCliente", idCliente);
                        fila.put("tipoCliente", tipoCli);
                        out.add(fila);
                    }
                }
                return out;
            }

        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("Error recargar ventas: " + e.getMessage());
        }
        return List.of();
    }

    // =====================
// LISTAR CLIENTES POR TIPO (EMPRESA/CLIENTE/MESA)
// =====================
// NUEVO

    public java.util.List<String> obtenerClientesPorTipo(TipoCliente tipoBuscado) {
        try {
            var solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/clientes"))
                    .GET()
                    .build();

            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var json = TraductorJSON.readTree(response.body());
                var out = new java.util.ArrayList<String>();
                if (json.isArray()) {
                    for (var n : json) {
                        String nombre = n.hasNonNull("nombre") ? n.get("nombre").asText() : null;
                        String tipo = n.hasNonNull("tipoCliente") ? n.get("tipoCliente").asText() : null;
                        if (nombre != null && !nombre.isBlank() && tipo != null) {
                            if (tipo.equalsIgnoreCase(tipoBuscado.name())) {
                                out.add(nombre.trim());
                            }
                        }
                    }
                }
                return out.stream()
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("obtenerClientesPorTipo: " + e.getMessage());
        }
        return java.util.List.of();
    }
}
