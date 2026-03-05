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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import paucar.service.ClientesService;
import paucar.service.ProductosService;
import paucar.service.VentasBackend;

public final class Ventas extends BorderPane {

    // ====== Constantes y formateadores ======
    private static final Locale LOCALE_AR = Locale.of("es", "AR");
    private static final String API_BASE = "http://localhost:4002/api";
    private final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE_AR);

    // ====== Servicios / backend ======
    private final ProductosService productosService = new ProductosService(API_BASE);
    private final ClientesService clientesService = new ClientesService(API_BASE);
    private final VentasBackend backend = new VentasBackend(API_BASE, clientesService);

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
    }

    // ====== Estado de la vista ======
    private final ObservableList<Fila> filas = FXCollections.observableArrayList();
    private final ObjectProperty<BigDecimal> total = new SimpleObjectProperty<>(BigDecimal.ZERO);

    // ====== Componentes ======
    private final TableView<Fila> tabla = new TableView<>(filas);
    private final Button btnAgregar = new Button("+ Agregar");
    private final Button btnQuitar = new Button("Quitar seleccionado");

    // ====== Sugerencias (clientes / productos) ======
    private final ObservableList<String> clientes = FXCollections.observableArrayList();

    private final ObservableList<ProductosService.ProductoItem> productos = FXCollections.observableArrayList();

    // ====== Constructor ======
    public Ventas() {
        setPadding(new Insets(16));
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
        filas.addListener((javafx.collections.ListChangeListener<Fila>) c -> recomputeTotal());
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
        btnAgregar.setOnAction(e -> abrirDialogoAgregar());/*Cuando el usuario haga clic en + Agregar,
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

    private Node crearTabla() {/*metodo que contruye la tabla visual UI que vemos en ventas */
        tabla.setEditable(true);/*habilita la edicion de la tabla directamente desde ventas */

        // Columna: Nombre (editable)
        var colNombre = new TableColumn<Fila, String>("Nombre");

        // ValueFactory ORIGINAL (directo a la propiedad):
        colNombre.setCellValueFactory(c -> c.getValue().nombreProperty());

        // Celda editable como ya tenías:
        colNombre.setCellFactory(TextFieldTableCell.forTableColumn());
        colNombre.setOnEditCommit(e -> e.getRowValue().setNombre(e.getNewValue()));
        colNombre.setPrefWidth(200);

        // Columna: Descripción (mostrar TODO el texto con wrap)
var colDesc = new TableColumn<Fila, String>("Descripción");
colDesc.setCellValueFactory(c -> c.getValue().descripcionProperty());

// NUEVO: celda con Text que envuelve (wrap) el contenido
colDesc.setCellFactory(col -> new TableCell<Fila, String>() {
    private final javafx.scene.text.Text text = new javafx.scene.text.Text();

    {
        // Envolver el texto según el ancho de la columna (restamos un margen)
        text.wrappingWidthProperty().bind(col.widthProperty().subtract(16));
        // Dejar que la celda calcule su alto según el contenido
        setGraphic(text);
        setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            text.setText(null);
            setTooltip(null);
            setGraphic(null);
        } else {
            text.setText(item);
            setGraphic(text);

            // (Opcional) tooltip con el texto completo
            var tip = new javafx.scene.control.Tooltip(item);
            setTooltip(tip);
        }
    }
});

colDesc.setEditable(false);
colDesc.setPrefWidth(420);

        // Columna: Monto (formateado)
        var colMonto = new TableColumn<Fila, String>("Monto");
        colMonto.setCellValueFactory(c -> Bindings.createStringBinding(
                () -> formatear(c.getValue().getMonto()), c.getValue().montoProperty()));
        colMonto.setCellFactory(TextFieldTableCell.forTableColumn());
        colMonto.setEditable(false);
        colMonto.setPrefWidth(140);

        // Columna: Estado (ComboBox por fila)
        var colEstado = new TableColumn<Fila, TipoDePago>("Estado");
        colEstado.setCellValueFactory(c -> c.getValue().estadoProperty());
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<TipoDePago> combo = new ComboBox<>();

            {
                combo.getItems().setAll(TipoDePago.values());
                combo.valueProperty().addListener((o, a, b) -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        getTableView().getItems().get(getIndex()).setEstado(b);
                    }
                });
            }

            @Override
            protected void updateItem(TipoDePago item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : combo);
                if (!empty) {
                    combo.setValue(item);
                }
            }
        });
        colEstado.setPrefWidth(180);

        // Botón quitar
        btnQuitar.getStyleClass().add("btn-danger");
        btnQuitar.disableProperty().bind(Bindings.isNull(tabla.getSelectionModel().selectedItemProperty()));
        btnQuitar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) {
                filas.remove(sel);
            }
            recomputeTotal();
        });

        tabla.getColumns().setAll(java.util.List.of(colNombre, colDesc, colMonto, colEstado));

        var cont = new VBox(8, tabla, btnQuitar);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return cont;
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
                .bind(Bindings.createStringBinding(() -> formatear(total.get()), total));/*Cada vez que total
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

    // =========================================================================================
    // Sección: Cargas asíncronas y backend
    // =========================================================================================
    private void cargarClientesAsync() {
        CompletableFuture
                .supplyAsync(() -> clientesService.obtenerTodosLosClientesMenosMesas())
                .thenAccept(lista -> Platform.runLater(() -> clientes.setAll(lista)));
    }

    private void cargarProductosAsync() {
        CompletableFuture
                .supplyAsync(productosService::cargarProductos) // List<ProductosService.ProductoItem>
                .thenAccept(items -> Platform.runLater(() -> productos.setAll(items)));
    }

    public void recargarDelBackend() {
        CompletableFuture
                .supplyAsync(() -> backend.cargarVentasDelDia(LocalDate.now()))
                .thenAccept(lista -> Platform.runLater(() -> {
            var nuevas = FXCollections.<Fila>observableArrayList();
            for (VentasBackend.VentaFilaDto dto : lista) {
                Fila f = new Fila();
                f.setNombre(dto.nombre());
                f.setDescripcion(dto.descripcion());
                f.setMonto(dto.monto());
                f.setEstado(dto.estado());
                nuevas.add(f);
            }
            filas.setAll(nuevas);
            recomputeTotal();
        }));
    }

    // =========================================================================================
    // Sección: Diálogo "Agregar pedido" (modular)
    // =========================================================================================
    private void abrirDialogoAgregar() {
        var dlg = new Agregar(clientes, productos); // usa las listas ya cargadas
        var res = dlg.show(getScene() == null ? null : getScene().getWindow());

        res.ifPresent(this::confirmarPedidoAsync);
    }

    // =========================================================================================
    // Utilitarios
    // =========================================================================================
    private void recomputeTotal() {
        BigDecimal t = filas.stream()
                .map(Fila::getMonto)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        total.set(t.setScale(2, RoundingMode.HALF_UP));
    }

    private String formatear(BigDecimal v) {
        if (v == null) {
            return "$ 0,00";
        }
        return MONEDA.format(v);
    }

    // Si no lo usás, podés eliminarlo o anotar @SuppressWarnings("unused")
    @SuppressWarnings("unused")
    private BigDecimal parseMoneda(String s) {
        try {
            String limpio = s.replace("$", "").replace(" ", "").replace(".", "").replace(",", ".");
            return new BigDecimal(limpio).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    // =========================================================================================
    // Guardado asíncrono (backend)
    // =========================================================================================
    private void confirmarPedidoAsync(Agregar.PedidoNuevo p) {
        TipoCliente tipo = deducirTipoCliente(p.nombreCliente);

        if (tipo == TipoCliente.MESA) {
            CompletableFuture
                    .supplyAsync(() -> backend.GuardarPedidoMesas(
                    p.nombreCliente, p.idProductos, p.cantidades, p.estado, p.observaciones))
                    .thenAccept(ok -> Platform.runLater(() -> {
                if (ok) {
                    recargarDelBackend();

                }
            }));
            return;
        }

        CompletableFuture
                .runAsync(() -> clientesService.crearClienteSiNoExiste(p.nombreCliente, tipo))
                .thenCompose(v -> CompletableFuture.supplyAsync(() -> clientesService.obtenerClienteIdPorNombre(p.nombreCliente)))
                .thenCompose(idCliente -> {
                    if (idCliente == null) {
                        Platform.runLater(() -> {
                            var dlg = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                            dlg.setTitle("Cliente no encontrado");
                            dlg.setHeaderText("No se pudo obtener el ID del cliente");
                            dlg.setContentText("Verificá el nombre del cliente o volvé a intentar.");
                            dlg.showAndWait();
                        });
                        return CompletableFuture.completedFuture(false);
                    }
                    return CompletableFuture.supplyAsync(() -> backend.GuardarPedidos(
                            idCliente, p.idProductos, p.cantidades, p.estado, p.observaciones));
                })
                .thenAccept(ok -> Platform.runLater(() -> {
            if (ok) {
                if (!clientes.contains(p.nombreCliente)) {
                    clientes.add(p.nombreCliente);
                    FXCollections.sort(clientes, String.CASE_INSENSITIVE_ORDER);
                }
                recargarDelBackend();
            }
        }));
    }

    // =========================================================================================
    // Heurística de tipo de cliente
    // =========================================================================================
    private TipoCliente deducirTipoCliente(String nombre) {
        if (nombre == null) {
            return TipoCliente.CLIENTE;
        }
        String n = nombre.trim().toLowerCase();

        if (n.startsWith("mesa ")) {
            return TipoCliente.MESA;
        }

        if (n.contains(" srl") || n.endsWith(" srl") || n.contains(" s.a") || n.contains(" sa")
                || n.contains("empresa") || n.contains("estudio") || n.contains("industria")) {
            return TipoCliente.EMPRESA;
        }
        return TipoCliente.CLIENTE;
    }
}
