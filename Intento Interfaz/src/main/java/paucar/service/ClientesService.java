package paucar.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.tpo.demo.entity.TipoCliente;
import com.uade.tpo.demo.entity.dto.VentaRequest;

public class ClientesService {

    private final String BASE_URL;
    private final HttpClient http;
    private final ObjectMapper TraductorJSON;
    private final VentaRequest venta;

    public ClientesService(String BASE_URL, VentaRequest venta) {
        this.BASE_URL = Objects.requireNonNull(BASE_URL);/*/*si la URL que me pasaste existe y no es nula,
                                                         la guardo; si es nula, te aviso enseguida porque
                                                         no puedo trabajar sin eso*/
        this.http = HttpClient.newHttpClient();/*/*Creame una herramienta para poder hacer llamadas al
                                               backend (como GET y POST) y guardámela para usarla cada
                                               vez que necesite hablar con la API*/
        this.TraductorJSON = new ObjectMapper();/*Creame un traductor que convierta JSON a objetos Java y
                                                objetos Java a JSON, porque lo voy a necesitar cada vez
                                                que hable con el backend */
        this.venta = Objects.requireNonNull(venta, "venta (VentaRequest) no puede ser null");/*Guardá la venta que me pasaste, pero antes asegurate
                                                                                                      de que no sea nula; si viene nula, frená todo y avisame
                                                                                                      con un error claro */

        if (this.venta.getIdProductos() == null) {/*Si las listas de productos no existen crealas vacías
                                                  ahora  para que todo siga funcionando. Una lista vacía
                                                  es segura; una null hace explotar el programa*/
            this.venta.setIdProductos(new ArrayList<>());
        }
        if (this.venta.getCantidades() == null) {/*lo mismo con las cantidades*/
            this.venta.setCantidades(new ArrayList<>());
        }
    }

    public List<String> obtenerTodosLosClientesMenosMesas() {/*Método que devuelve una lista de nombres que NO
                                                             sean mesas */

        try {/*es try porqeu si la operacion falla tirara error osea ira a catch */

            var solicitud = HttpRequest.newBuilder()/*crea una solicitud http osea hace una solicitud al servidor(API)*/
                    .uri(URI.create(BASE_URL + "/clientes"))/*/ Le pongo la URL de destino (BASE_URL + "/clientes") */
                    .GET()/*Indico que el objetivo de la variable solicitud es hacer un GET (pedir/leer datos)*/
                    .build();/*Termino de construir la solicitud (queda lista, pero todavía sin enviar) */

            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());/*Enviar la solicitud al
                                                                                      servidor y guardar la
                                                                                      respuesta como texto */

            if (response.statusCode() >= 200 && response.statusCode() < 300) {/*Si el código es entre 200 y 299,
                                                                              entonces todo salió bien */

                var json = TraductorJSON.readTree(response.body());/*Toma el texto que vino del servidor
                                                                   (normalmente  string JSON) y lo
                                                                   convierte en un objeto (un árbol JSON) 
                                                                   JSON que podés leer por campos*/
                var out = new ArrayList<String>();

                if (json.isArray()) {/*Verifica que 'json' sea un vector */

                    for (var n : json) {/*Recorre cada elemento del vector */
                        var nombre = n.hasNonNull("nombre") ? n.get("nombre").asText() : null;/*Si el objeto tiene la clave 'nombre' y no es null 
                                                                                                                    entonces obtiene su valor como String sino 'nombre'
                                                                                                                    queda null*/

                        var tipo = n.hasNonNull("tipoCliente") ? n.get("tipoCliente").asText() : null;/*si el objeto tiene la clave 'tipoCliente'
                                                                                                                           y no es null lo lee como string, sino 'tipo'
                                                                                                                           queda null */
                        if (nombre != null && !nombre.isBlank()) {/*Filtra: 'nombre' debe existir y NO estar vacío/espacios */
                            if (tipo == null || !tipo.equalsIgnoreCase("MESA")) {/*Si 'tipo' es null O distinto de "MESA" */
                                out.add(nombre.trim());/*entonces agrega el 'nombre' (sin espacios extremos) a la lista */
                            }
                        }
                    }
                }
                return out.stream()
                        .distinct()/*borra duplicados */
                        .sorted(String.CASE_INSENSITIVE_ORDER)/*ordena alfabeticamente */
                        .collect(Collectors.toList());/*Retorna una lista ordenada alfabéticamente sin nombres duplicados */
            }
        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("Error clientes: " + e.getMessage());
        }

        return List.of();/*Si no pude obtener clientes válidos, te devuelvo una lista vacía para evitar null
                          y que el código llamador no falle, ademas no te muestra la lsita de clientes ya
                          ingresados no me queda claro por que*/
    }

    public void crearClienteSiNoExiste(String nombre, TipoCliente tipoCli) {
        if (nombre == null || nombre.isBlank()) {/*si el nombre no es valido, o es null o solo son espacios
                                                 vacios se salga del metodo*/
            return;
        }
        if (tipoCli == TipoCliente.MESA) {/*si el tipo de cliente es mesa salga del metodo ya que no nos interesa
                                       recordar el historial de compra de la gente de las mesas */
            return;
        }

        try {
            var payload = TraductorJSON.createObjectNode()/*Pensalo como: “arranco un JSON {} para llenarlo con nombre y tipCliente */
                    .put("nombre", nombre.trim())/* Agrega al ObjectNode el campo "nombre" y el valor
                                                           que le pases, toma el string nombre y le saca los
                                                           espacios del principio y del final*/
                    .put("tipoCliente", tipoCli.name());/*Agregá al JSON un campo que se llama
                                                                  tipoCliente y poné un valor ahi, ya sea
                                                                  empresa, cliente o mesa*/

            var solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/clientes"))/* Le pongo la URL de destino (BASE_URL + "/clientes") */
                    .header("Content-Type", "application/json")/* evitá dobles barras accidentales.
                                                                           Si BASE_URL termina con /, no pongas
                                                                           otra / en el path  y aclaro que el
                                                                           contenido está escrito en JSON*/
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))/*Indico que el objetivo de la variable req es hacer un POST (enviar datos) y le paso el objeto lo convierto en JSON*/
                    .build();

            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());/*envío la solicitud HTTP al servidor y guardo la respuesta completa en response como texto */

            if (!(response.statusCode() == 200 || response.statusCode() == 201
                    || response.statusCode() == 400 || response.statusCode() == 409)) {/*Si NO es 200, NI 201, NI 400, NI 409 entonces tira error */
                System.err.println("Error al crear cliente: HTTP " + response.statusCode());
            }

            try {
                Long id = this.obtenerClienteIdPorNombre(nombre, tipoCli);/*Intento obtener el ID del cliente que acabo de crear (o que ya existía) para guardarlo en la venta actual, así después puedo usar ese ID para asociar la venta a ese cliente en el backend */
                if (id != null && this.venta != null) {
                    this.venta.setIdCliente(id);
                }
            } catch (Exception ignore) {
                // defensivo
            }

        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("crearClienteSiNoExiste: " + e.getMessage());
        }
    }
public Long obtenerClienteIdPorNombre(String nombre, TipoCliente tipo) {
    try {
        if (nombre == null || nombre.isBlank() || tipo == null) {
            return null;
        }

        String url = BASE_URL + "/clientes?nombre="
                + URLEncoder.encode(nombre, StandardCharsets.UTF_8)
                + "&tipoCliente="  // <- OJO: '&' literal (no &amp;)
                + URLEncoder.encode(tipo.name(), StandardCharsets.UTF_8);

        var solicitud = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            var json = TraductorJSON.readTree(response.body());

            com.fasterxml.jackson.databind.JsonNode match = null; // <- declarar una sola variable

            if (json.isArray()) {
                String buscado = nombre.trim();
                for (com.fasterxml.jackson.databind.JsonNode elem : json) {
                    if (elem != null && elem.hasNonNull("nombre")) {
                        String n = elem.get("nombre").asText("").trim();
                        String t = elem.hasNonNull("tipoCliente") ? elem.get("tipoCliente").asText("") : "";
                        // Coincidencia por nombre y tipo (case-insensitive)
                        if (n.equalsIgnoreCase(buscado) && t.equalsIgnoreCase(tipo.name())) {
                            match = elem;
                            break;
                        }
                    }
                }
            } else if (json.isObject()) {
                String n = json.hasNonNull("nombre") ? json.get("nombre").asText("").trim() : "";
                String t = json.hasNonNull("tipoCliente") ? json.get("tipoCliente").asText("") : "";
                if (n.equalsIgnoreCase(nombre.trim()) && t.equalsIgnoreCase(tipo.name())) {
                    match = json;
                }
            }

            if (match != null && match.hasNonNull("idCliente")) {
                Long id = match.get("idCliente").asLong();
                // Integración con VentaRequest (como ya hacías)
                try {
                    if (this.venta != null) {
                        this.venta.setIdCliente(id);
                    }
                } catch (Exception ignore) {}
                return id;
            }
        }
    } catch (java.io.IOException | InterruptedException e) {
        System.err.println("obtenerClienteIdPorNombre(nombre,tipo): " + e.getMessage());
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    // Sin fallback a la versión 1-parámetro para evitar confundir "Victoria CLIENTE" con "Victoria EMPRESA".
    return null;
}

    public java.util.List<String> obtenerNombresPorTipo(com.uade.tpo.demo.entity.TipoCliente tipo) {
        if (tipo == null) {
            return java.util.List.of();
        }

        try {
            // 1) Intento con ?tipoCliente=TIPO
            String url = BASE_URL + "/clientes?tipoCliente="
                    + java.net.URLEncoder.encode(tipo.name(), java.nio.charset.StandardCharsets.UTF_8);
            var req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .GET()
                    .build();
            var res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                var json = TraductorJSON.readTree(res.body());
                var out = new java.util.ArrayList<String>();

                if (json.isArray()) {
                    for (var n : json) {
                        String nombre = n.hasNonNull("nombre") ? n.get("nombre").asText() : null;
                        String tipoStr = n.hasNonNull("tipoCliente") ? n.get("tipoCliente").asText() : null;

                        if (nombre != null && !nombre.isBlank()) {
                            if (tipoStr == null) {
                                // No vino el campo tipoCliente para este ítem: lo evaluamos luego
                                out.add(nombre.trim());
                            } else if (tipoStr.equalsIgnoreCase(tipo.name())) {
                                out.add(nombre.trim());
                            }
                        }
                    }
                } else if (json.isObject()) {
                    String nombre = json.hasNonNull("nombre") ? json.get("nombre").asText() : null;
                    String tipoStr = json.hasNonNull("tipoCliente") ? json.get("tipoCliente").asText() : null;

                    if (nombre != null && !nombre.isBlank()) {
                        if (tipoStr == null || tipoStr.equalsIgnoreCase(tipo.name())) {
                            out.add(nombre.trim());
                        }
                    }
                }

                // Si NINGÚN elemento traía campo tipoCliente, asumimos que el backend ya filtró.
                // Si AL MENOS uno traía el campo, ya filtramos arriba por coincidencia exacta.
                return out.stream()
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(java.util.stream.Collectors.toList());
            }

            // 2) Fallback: pedir todos y filtrar por el campo 'tipoCliente'
            String urlAll = BASE_URL + "/clientes";
            var reqAll = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(urlAll))
                    .GET()
                    .build();
            var resAll = http.send(reqAll, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (resAll.statusCode() >= 200 && resAll.statusCode() < 300) {
                var json = TraductorJSON.readTree(resAll.body());
                var out = new java.util.ArrayList<String>();

                if (json.isArray()) {
                    for (var n : json) {
                        String nombre = n.hasNonNull("nombre") ? n.get("nombre").asText() : null;
                        String tipoStr = n.hasNonNull("tipoCliente") ? n.get("tipoCliente").asText() : null;

                        if (nombre != null && !nombre.isBlank() && tipoStr != null
                                && tipoStr.equalsIgnoreCase(tipo.name())) {
                            out.add(nombre.trim());
                        }
                    }
                } else if (json.isObject()) {
                    String nombre = json.hasNonNull("nombre") ? json.get("nombre").asText() : null;
                    String tipoStr = json.hasNonNull("tipoCliente") ? json.get("tipoCliente").asText() : null;

                    if (nombre != null && !nombre.isBlank() && tipoStr != null
                            && tipoStr.equalsIgnoreCase(tipo.name())) {
                        out.add(nombre.trim());
                    }
                }

                return out.stream()
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(java.util.stream.Collectors.toList());
            }

        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("obtenerNombresPorTipo: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return java.util.List.of();
    }
}
