package paucar.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uade.tpo.demo.entity.dto.VentaRequest;

public class ProductosService {

    private final String BASE_URL;
    private final HttpClient http;
    private final ObjectMapper TraductorJSON;
    private final VentaRequest venta;

    // --- DTOs internos ---
    public record ProductoItem(Long id, String nombre) {/*Un record (Java 16+) es una forma corta de declarar
                                                       una clase pensada para transportar datos (lo que antes
                                                       llamábamos “DTO”) 
                                                       la idea de un record es evitar escribir todo el codigo
                                                       repetitivo del constructor, getters, equals/hashCode, 
                                                       toString.*/
    }

    public ProductosService(String BASE_URL, VentaRequest venta) {
        this.BASE_URL = Objects.requireNonNull(BASE_URL);
        this.http = HttpClient.newHttpClient();
        this.TraductorJSON = new ObjectMapper();
        this.venta = Objects.requireNonNull(venta, "venta (VentaRequest) no puede ser null");

        if (this.venta.getIdProductos() == null) {
            this.venta.setIdProductos(new ArrayList<>());
        }
        if (this.venta.getCantidades() == null) {
            this.venta.setCantidades(new ArrayList<>());
        }
    }

    public List<ProductoItem> cargarProductos() {/*este metodo hace que aparezcan los productos para añadir,
                                                 si quitas el if desaparecen todos los productos */
        try {
            var solicitud = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/productos"))
                    .GET()/*prepara solicitud get */
                    .build();

            var response = http.send(solicitud, HttpResponse.BodyHandlers.ofString());/*envia la solicitud y
                                                                                      guarda la response */

            System.out.println("[/productos] status=" + response.statusCode());
            System.out.println("[/productos] body=" + response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {/*si el codigo de la response esta
                                                                              entre 200 y 299*/


                var array = TraductorJSON.readTree(response.body());
                var out = new ArrayList<ProductoItem>();

                // *** USO de VentaRequest (sin estructuras nuevas) ***
                List<Long> idsSeleccionados = venta.getIdProductos();
                System.out.println("DEBUG idsSeleccionados = " + idsSeleccionados);
                if (array.isArray()) {/*verifica que la variable array es un vector/lista */
                    for (var n : array) {/*recorre con n el vector array de productos */

                        Long id = n.hasNonNull("idProducto")
                                ? n.get("idProducto").asLong()
                                : null;
                        String nombre = n.hasNonNull("nombre")
                                ? n.get("nombre").asText()
                                : null;

                        if (id != null && nombre != null && !nombre.isBlank()) {
                            // Excluir productos ya presentes en la venta
                            if (idsSeleccionados.contains(id)) {
                                continue;
                            }
                            out.add(new ProductoItem(id, nombre.trim()));
                        }
                    }
                }
                return out.stream() /*Tomá la lista out (que contiene muchos ProductoItem) y convertila en un
                                     stream para poder aplicarle operaciones como ordenar, filtrar, mapear, etc */
                        .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.nombre(), b.nombre()))/*Ordená los elementos del stream usando un comparador alfabetico ignorando mayúsculas/minúsculas */
                        .collect(Collectors.toList());/*Materializá ese stream ordenado en una lista nueva de
                                                      Java (un List<ProductoItem>), osea devuelve la lista de
                                                      productos al que hay que agregar a los pedidos, ese es el
                                                      objetivo del metodo */
            }

        } catch (java.io.IOException | InterruptedException e) {
            System.err.println("Error productos: " + e.getMessage());
        }

        return List.of();/*retorna la lista de productos vacia */
    }
}
