package paucar.ventas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.uade.tpo.demo.entity.TipoCliente;
import com.uade.tpo.demo.entity.TipoDePago;
import com.uade.tpo.demo.entity.dto.VentaRequest;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import paucar.service.ClientesService;
import paucar.service.ProductosService;
import paucar.service.VentasBackend;

public final class Ventas extends BorderPane {

    // ====== Constantes y formateadores ======
    private static final Locale LOCALE_AR = Locale.of("es", "AR");
    private static final String API_BASE = "http://localhost:4002/api";
    private final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE_AR);

    private final VentaRequest venta = new VentaRequest();
    // ====== Servicios / backend ======
    private final ProductosService productosService = new ProductosService(API_BASE, venta);
    private final ClientesService clientesService = new ClientesService(API_BASE, venta);
    private final VentasBackend backend = new VentasBackend(API_BASE, clientesService, venta);

    // ====== Modelo de Fila (UI de la tabla) ======
    public static class Fila {

        /*Property (propiedad): es una variable con sensor que avisa cuando cambia para que la interfaz
                               (JavaFX) se actualice sola */
        private final StringProperty nombre = new SimpleStringProperty("");/*osea todos estos son strings que
                                                                                         cuando se les asigne valor(porque
                                                                                         empiezan vacios) a sus variables se
                                                                                         actualizaran al toque sin tener que
                                                                                         hacer refresh */
        private final StringProperty descripcion = new SimpleStringProperty("");

        /*ObjectProperty: tipo de dato de JavaFX en el que se puede guardar cualquier tipo de dato, o
                          objeto o vector y que se actualiza para que si se cambia el contenido se pueda
                          ver visualmente el contenido actual*/
        private final ObjectProperty<BigDecimal> monto = new SimpleObjectProperty<>(BigDecimal.ZERO);/*el object property le meto el tipo de dato
                                                                                                      bigdecimal qeu es un tipo de dato (que sirve
                                                                                                      para guarda números con decimales súper
                                                                                                      precisos, sirve para dinero)y inicializo mi
                                                                                                      variable monto y le doy el valor zero(0.0 pesos)*/
        private final ObjectProperty<TipoDePago> estado
                = new SimpleObjectProperty<>(TipoDePago.DEBE);/*Al ObjectProperty le meto el enum TipoDePago
                                                                               y creo mi variable estado La inicializo con
                                                                               el valor DEBE (o sea, que por defecto la
                                                                               forma de pago empieza siendo ‘debe’)*/

        private final StringProperty observaciones = new SimpleStringProperty("");

        public String getNombre() {
            return nombre.get();
        }/*retorna el valor que está guardado en el StringProperty nombre*/

        public void setNombre(String v) {
            nombre.set(v);
        }/*setea osea le da valor a la variable nombre */

        public StringProperty nombreProperty() {
            return nombre;
        }/*retorna nombre porque la UI la requiere */

        public String getDescripcion() {
            return descripcion.get();
        }/*retorna el contenido de la descripcion*/

        public void setDescripcion(String v) {
            descripcion.set(v);
        }/*le da valor a la variable descripcion */

        public StringProperty descripcionProperty() {
            return descripcion;
        }/*retorna la descripcion porque la UI la requiere*/

        public BigDecimal getMonto() {
            return monto.get();
        }/*retorna el valor del monto*/

        public void setMonto(BigDecimal v) {
            monto.set(v);
        }/*setea el valor de monto */

        public ObjectProperty<BigDecimal> montoProperty() {
            return monto;
        }/*retorna el object property del monto porque la UI la requiere */

        public TipoDePago getEstado() {
            return estado.get();
        }/*obtiene(retorna) el valor de estado*/

        public void setEstado(TipoDePago v) {
            estado.set(v);
        }/*setea el valor de estado */

        public ObjectProperty<TipoDePago> estadoProperty() {
            return estado;
        }/*retorna el object property del estado porque la UI la requiere */

        public String getObservaciones() {
            return observaciones.get();
        }

        public void setObservaciones(String v) {
            observaciones.set(v);
        }

        public StringProperty observacionesProperty() {
            return observaciones;
        }
    }

    // ====== Estado de la vista ======
    private final ObservableList<Fila> RenglonDeLaTabla = FXCollections.observableArrayList();
    private final ObjectProperty<BigDecimal> total = new SimpleObjectProperty<>(BigDecimal.ZERO);

    private final Button btnAgregar = new Button("+ Agregar");

    // ====== Sugerencias (clientes / productos) ======
    private final ObservableList<String> clientes = FXCollections.observableArrayList();

    private final ObservableList<ProductosService.ProductoItem> productos = FXCollections.observableArrayList();

    // ====== Constructor ======
    public Ventas() {
        setPadding(new Insets(16));
        venta.setEstado(TipoDePago.DEBE);
        venta.setObservaciones("");
        initUI();
        initAsync();
        initBindings();
    }

    // =========================================================================================
    // Inicialización modular
    // =========================================================================================
    private void initUI() {
        setTop(crearHeader());
        setCenter(crearTabla());
        setBottom(crearFooter());
    }

    private void initAsync() {
        cargarClientesAsync();
        cargarProductosAsync();
        recargarDelBackend();
    }

    private void initBindings() {
        RenglonDeLaTabla.addListener((javafx.collections.ListChangeListener<Fila>) c -> MontoTotalActual());
    }
    // importá tu clase nueva arriba:
// import paucar.ventas.ui.Tabla;

private void cargarClientesAsync() {
    CompletableFuture
        .supplyAsync(() -> clientesService.obtenerTodosLosClientesMenosMesas())
        .thenAccept(lista -> Platform.runLater(() -> clientes.setAll(lista)));
}

private void cargarProductosAsync() {
    CompletableFuture
        .supplyAsync(productosService::cargarProductos)
        .thenAccept(items -> Platform.runLater(() -> productos.setAll(items)));
}

private Node crearTabla() {
    // Le pasás la lista observable y el Locale para formatear el monto de la columna
    var pane = new Tabla(RenglonDeLaTabla, LOCALE_AR);
    return pane; // o pane.asNode() si tu clase expone ese método
}

    // =========================================================================================
    // Sección: Header / Tabla / Footer
    // =========================================================================================
    private Node crearHeader() {
        var hoy = LocalDate.now();/*guardamos en la variable hoy la fecha actual */
        var dow = hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_AR).toUpperCase();/*guardamos en
                                                                                             la variable dow
                                                                                            el dia de la
                                                                                           semana que estamos 
                                                                                           en argentina*/

        var lblTitulo = new Label(dow + " " + hoy.getDayOfMonth() + "/" + hoy.getMonthValue() + "/" + hoy.getYear());/*parte visual de la fecha grande en la pantalla */
        lblTitulo.getStyleClass().add("title-xl");/*Aplicále al Label todos los estilos definidos para
                                                    la clase .title-xl de mi css */

        btnAgregar.getStyleClass().add("btn-success");/*ponele los estilos de mi css llamado btn
                                                         success */
        btnAgregar.setOnAction(e -> VentanaAgregarPedido());/*Cuando el usuario haga clic en + Agregar,
                                                           abrí el diálogo para cargar un nuevo pedido */

        var separador = new Region();/*crea separador invisible */
        HBox.setHgrow(separador, Priority.ALWAYS);

        var barra = new HBox(12, lblTitulo, separador, btnAgregar);/*ordena el titulo con la
                                                                            fecha el separador y el boton
                                                                            agregar */
        barra.setAlignment(Pos.CENTER_LEFT);/*centra */
        barra.setPadding(new Insets(0, 0, 10, 0));/*añade 10px abajo del boton
                                                                          + agregar */
        return barra;/*retorna la barra */
    }
    private Node crearFooter() {
        var TituloTotal = new Label("Total:");/*texto del total de la suma de precio de productos */
        TituloTotal.getStyleClass().add("total-titulo");/*crea total-titulo para en algun momento
                                                            estilarlo con css */
        var TextoVisualTotal = new Label();/*Creá un Label vacío llamado lblTotal. Después lo voy a llenar
                                   automáticamente con el total formateado */
        TextoVisualTotal.getStyleClass().add("total-monto");/*crea total-monto para en algun momento
                                                               estilarlo con css */
        TextoVisualTotal.textProperty()
                .bind(Bindings.createStringBinding(() -> FormatearMonto(total.get()), total));/*Cada vez que total
                                                                                 cambie, actualiza
                                                                              automáticamente el texto del
                                                                             Label con el total formateado */

        var separador = new Region();/*crea una separacion, es como un bloque que no muestra nada pero
                                     ocupa espacio */
        HBox.setHgrow(separador, Priority.ALWAYS);/*hace que el separador ocupe todo el espacio
                                                   horizontal disponible entre el titulo total y
                                                   el texto del total, empujando al texto del total
                                                   hacia la derecha */

        var box = new HBox(10, separador, TituloTotal, TextoVisualTotal);/*crea una caja que
                                                                                  posiciona en orden de 
                                                                                  izquierda a derecha
                                                                                  donde estara el espacio
                                                                                  y el contenido visual */
        box.setAlignment(Pos.CENTER_RIGHT);/*posiciona el contenido de box de forma centrada verticalmente */
        box.setPadding(new Insets(10, 0, 0, 0));/*agrega 10 px arriba del contenido */
        return box;/*retorna la box */
    }

    public void recargarDelBackend() {
        CompletableFuture /*CompletableFuture es una herramienta de Java que te permite ejecutar código en
                          segundo plano sin trabar la interfaz gráfica (UI)*/
                .supplyAsync(() -> backend.cargarVentasDelDia(LocalDate.now()))/*Ejecuta en segundo plano el cargarVentasDelDia
                                                                                del backend, pasándole la fecha de hoy
                                                                                (LocalDate.now()), para obtener la lista de
                                                                                ventas del día sin trabar la UI*/
                .thenAccept(lista -> Platform.runLater(() -> {/*cuando termine la operación asíncrona anterior y se
                                                              obtenga la lista, se ejecutará el siguiente bloque de
                                                              código usando Platform.runLater para actualizar la
                                                              interfaz gráfica */

            var nuevas = FXCollections.<Fila>observableArrayList();/*Crea una lista VACÍA que va a contener objetos Fila,
                                                                   será usada para construir el NUEVO contenido de la
                                                                   tabla antes de reemplazar lo que está actualmente en pantalla*/

            for (java.util.Map<String, Object> dto : lista) {/*Recorre cada elemento de lista con dto*/

                Fila f = new Fila();/*Esa línea crea un objeto nuevo de tipo Fila, o sea, crea un NUEVO
                                     renglón para la tabla */
                f.setNombre((String) dto.getOrDefault("nombre", ""));/*Tomá del mapa dto el valor de la clave "nombre" (o "" si no existe), 
                                                                                       convertí ese valor a String y ponelo en el campo nombre de la Fila f */
                f.setDescripcion((String) dto.getOrDefault("descripcion", ""));
                f.setMonto((java.math.BigDecimal) dto.getOrDefault("monto", java.math.BigDecimal.ZERO));/*Obtiene del mapa dto el valor asociado a la clave
                                                                                                            "monto" (o BigDecimal.ZERO si no está), lo castea a
                                                                                                            BigDecimal y lo asigna al campo monto de la Fila f */
                f.setEstado((com.uade.tpo.demo.entity.TipoDePago) dto.getOrDefault(
                        "estado", com.uade.tpo.demo.entity.TipoDePago.DEBE));/*Toma del mapa dto el valor de la clave "estado" (si existe), y si falta usa
                                                                                  TipoDePago.DEBE como valor por defecto; luego convierte ese valor al tipo
                                                                                  TipoDePago y se lo asigna al campo estado de la Fila f */
                f.setObservaciones((String) dto.getOrDefault("observaciones", ""));
                nuevas.add(f);/*Agrega la fila f (que acabás de construir con los datos de una venta)
                              dentro de la lista nuevas */
            }

            RenglonDeLaTabla.setAll(nuevas);
            MontoTotalActual();
        }));
    }

    private void VentanaAgregarPedido() {/*este metodo es el que se ejecuta cuando tocás el botón “+ Agregar”
                                        en la pantalla de Ventas */

        var dlg = new Agregar(clientes, productos, clientesService, venta);/*crea un objeto nuevo de la clase Agregar y le
                                                          pasa clientes, productos y venta al constructor */

        var res = dlg.Mostrar(getScene() == null ? null : getScene().getWindow());/*Muestra el diálogo dlg
                                                                             usando como owner la ventana
                                                                             actual (si existe) y guarda en
                                                                          res el Optional con el resultado
                                                                           del usuario */

        res.ifPresent(nombre -> {
            var tipo = dlg.getTipoSeleccionado();
            confirmarPedidoAsync(nombre, tipo);

        });
    }

    // =========================================================================================
    // Utilitarios
    // =========================================================================================
    private void MontoTotalActual() {
        BigDecimal t = RenglonDeLaTabla.stream()/*convierto la lista RenglonDeLaTabla en un Stream, que es
                                                una forma de recorrer la lista como una secuencia de
                                                elementos para poder aplicar operaciones como map, filter y
                                                reduce */
                .filter(f -> f != null/*filtra si la fila es valida (tambien podria ser un renglon vacio */
                && f.getEstado() != null/*filtra si tiene estado */
                && f.getEstado() != TipoDePago.DEBE)/*y filtra si el tipodepago no es "DEBE"*/
                .map(Fila::getMonto)/*de cada fila obtengo solo el monto */
                .reduce(BigDecimal.ZERO, BigDecimal::add);/*recorro los montos y los junto en un único
                                                          resultado sumándolos, empezando desde 0 */
        total.set(t.setScale(2, RoundingMode.HALF_UP));/*ajusto el total a 2 decimales con
                                                                 redondeo clásico y actualizo la propiedad
                                                                 'total' para refrescar la UI */
    }

    private String FormatearMonto(BigDecimal v) {
        if (v == null) {/*si el valor del monto es null */
            return "$ 0,00";/*retorna cero con 2 decimales */
        }
        return MONEDA.format(v);/*sino retorna el monto formateado como moneda */
    }

    // Si no lo usás, podés eliminarlo o anotar @SuppressWarnings("unused")
    @SuppressWarnings("unused")
    private BigDecimal parseMoneda(String s) {
        try {
            String limpio = s.replace("$", "")/*quita el simbolo de moneda */
                    .replace(" ", "")/*quita los espacios vacios */
                    .replace(".", "")/*quita los puntos */
                    .replace(",", ".");/*reemplaza las comas por puntos para
                                                                    utilizar los decimales en modo gringo */

            return new BigDecimal(limpio).setScale(2, RoundingMode.HALF_UP);/*retorna el valor
                                                                                      limpio y solo con 2
                                                                                      decimales */
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);/*retorna cero con 2
                                                                               decimales */
        }
    }

    private void confirmarPedidoAsync(String nombreCliente, TipoCliente tipo) {

        // 1) Tomar COPIAS defensivas de las listas del VentaRequest
        java.util.List<Long> ids = new java.util.ArrayList<>(venta.getIdProductos());
        java.util.List<Integer> cants = new java.util.ArrayList<>(venta.getCantidades());
        TipoDePago estado = venta.getEstado();
        String obs = (venta.getObservaciones() == null ? "" : venta.getObservaciones());

        if (tipo == TipoCliente.MESA) {
            // 2) Pasar las COPIAS al backend (no las listas internas de 'venta')
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> backend.GuardarPedidoMesas(nombreCliente, ids, cants, estado, obs))
                    .thenAccept(ok -> javafx.application.Platform.runLater(() -> {
                if (ok) {
                    recargarDelBackend();

                }
            }));
            return;
        }

        java.util.concurrent.CompletableFuture
        .supplyAsync(() -> {
            // 1) Asegurar que exista el cliente con el TIPO correcto
            clientesService.crearClienteSiNoExiste(nombreCliente, tipo);

            // 2) Obtener el id del cliente filtrando por (nombre, tipo)
            return clientesService.obtenerClienteIdPorNombre(nombreCliente, tipo);
        })
        .thenCompose(idCliente -> {
            if (idCliente == null) {
                javafx.application.Platform.runLater(() -> {
                    var dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                    dlg.setTitle("Cliente no encontrado");
                    dlg.setHeaderText("No se pudo obtener el ID del cliente");
                    dlg.setContentText("Verificá el nombre del cliente o volvé a intentar.");
                    dlg.showAndWait();
                });
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }

            venta.setIdCliente(idCliente);

            // 3) Guardar el/los pedidos
            return java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> backend.GuardarPedidos(idCliente, ids, cants, estado, obs)
            );
        })
        .thenAccept(ok -> javafx.application.Platform.runLater(() -> {
            if (ok) {
                if (!clientes.contains(nombreCliente)) {
                    clientes.add(nombreCliente);
                    javafx.collections.FXCollections.sort(clientes, String.CASE_INSENSITIVE_ORDER);
                }
                recargarDelBackend();
            }
        }));
    }
}
