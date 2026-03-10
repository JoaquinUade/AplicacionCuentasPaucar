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
                Long id = this.obtenerClienteIdPorNombre(nombre);
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

    public Long obtenerClienteIdPorNombre(String nombre) {
        try {
            if (nombre == null || nombre.isBlank()) {/*Si el nombre es nulo o está vacío salir del método retornando null */
                return null;
            }

            String url = BASE_URL + "/clientes?nombre="/*añade al final de la url que ya teniamos "/clientes?nombre="" */
                    + URLEncoder.encode(nombre, StandardCharsets.UTF_8);/*codifica la url para que sea valida ya que no acepta ñ o tildes */

            var solicitud = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();/*Arma una solicitud HTTP de tipo GET hacia la URL que construiste, lista para ser enviada */
            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());/*Envía la solicitud HTTP solicitud al servidor y recibe la respuesta completa en response, leyendo el cuerpo como texto (String) */

            if (response.statusCode() >= 200 && response.statusCode() < 300) {/*si el codigo http  esta entre 200 y 300*/
                var json = TraductorJSON.readTree(response.body());/*Convierte el texto que vino del servidor en
                                                                   la respuesta (response.body()) a un objeto
                                                                   JSON (JsonNode) usando Jackson */
                com.fasterxml.jackson.databind.JsonNode name = null;

                if (json.isArray()) {
                    String buscado = nombre.trim();
                    for (com.fasterxml.jackson.databind.JsonNode elem : json) {
                        if (elem != null && elem.hasNonNull("nombre")) {
                            String n = elem.get("nombre").asText("").trim();
                            if (n.equalsIgnoreCase(buscado)) {
                                name = elem;
                                break;
                            }
                        }
                    }
                } else if (json.isObject()) {
                    name = json;
                }

                if (name == null) {
                    return null;
                }
                if (name.hasNonNull("idCliente")) {
                    Long id = name.get("idCliente").asLong();
                    // >>> INTEGRACIÓN VentaRequest <<<
                    try {
                        if (this.venta != null) {
                            this.venta.setIdCliente(id); // usar VentaRequest
                        }
                    } catch (Exception ignore) {
                        // defensivo: nunca romper el flujo original
                    }
                    return id;
                }
            }
        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("obtenerClienteIdPorNombre (general): " + e.getMessage());
            return null;
        }
        return null;
    }
    public static TipoCliente deducirTipoCliente(String nombre) {
        if (nombre == null) {/*si el nombre es null */
            return TipoCliente.CLIENTE;/*asumo que es un cliente */
        }
        String n = nombre.trim().toLowerCase();/*le quita los espacio en blanco del principio y final del
                                               nombre y lo pasa a minuscula para facilitar las
                                               comparaciones */

        if (n.startsWith("mesa ")) {/*revisa si el nombre empieza con "mesa " */
            return TipoCliente.MESA;/*si es asi asume que es unamesa */
        }

        if (n.contains(" srl") || n.endsWith(" srl") || n.contains(" s.a") || n.contains(" sa")/*cambiar esto */
                || n.contains("empresa") || n.contains("estudio") || n.contains("industria")) {
            return TipoCliente.EMPRESA;
        }
        return TipoCliente.CLIENTE;
    }
}
