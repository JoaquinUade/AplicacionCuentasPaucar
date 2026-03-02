package paucar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
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
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
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
    private final java.util.concurrent.atomic.AtomicBoolean actualizandoEditor
            = new java.util.concurrent.atomic.AtomicBoolean(false);

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
    private final FilteredList<String> clientesFiltrados = new FilteredList<>(clientes, s -> true);

    private final ObservableList<ProductosService.ProductoItem> productos = FXCollections.observableArrayList();

    // ====== DTOs internos ======
    private static class PedidoNuevo {

        String nombreCliente;
        java.util.List<Long> idProductos = new java.util.ArrayList<>();
        java.util.List<Integer> cantidades = new java.util.ArrayList<>();
        TipoDePago estado;
        String observaciones;
    }

    private static record LineaPedido(Long idProducto, Integer cantidad) {

    }

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

        // Columna: Descripción (solo muestra)
        var colDesc = new TableColumn<Fila, String>("Descripción");
        colDesc.setCellValueFactory(c -> c.getValue().descripcionProperty());
        colDesc.setCellFactory(TextFieldTableCell.forTableColumn());
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
        Dialog<PedidoNuevo> dialog = construirDialogoAgregar();
        var res = dialog.showAndWait();
        res.ifPresent(this::confirmarPedidoAsync);
    }

    private Dialog<PedidoNuevo> construirDialogoAgregar() {
        Dialog<PedidoNuevo> dialog = new Dialog<>();
        dialog.setTitle("Agregar pedido");

        ButtonType okType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        // --- Cliente con autocompletar ---
        ComboBox<String> cbCliente = crearComboClientes();

        // --- Productos (líneas dinámicas) ---
        VBox contLineas = new VBox(6);
        contLineas.setPadding(new Insets(6));

        Button btnAgregarLinea = new Button("+ Producto");
        btnAgregarLinea.getStyleClass().add("btn-primary");
        btnAgregarLinea.setOnAction(e -> contLineas.getChildren().add(crearLineaProducto(contLineas)));

        // Al menos una línea inicial
        contLineas.getChildren().add(crearLineaProducto(contLineas));

        // --- Estado y observaciones ---
        ComboBox<TipoDePago> cbEstado = crearComboEstado();
        TextField tfObs = crearTextFieldObservaciones();

        // --- Layout ---
        GridPane grid = construirGridDialogo(cbCliente, contLineas, btnAgregarLinea, cbEstado, tfObs);

        // --- Validación del botón OK ---
        Node okBtn = dialog.getDialogPane().lookupButton(okType);
        okBtn.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> dialogInvalido(cbCliente, contLineas),
                        cbCliente.getEditor().textProperty(),
                        contLineas.getChildren()
                )
        );

        dialog.getDialogPane().setContent(grid);

        // --- ResultConverter (mapea UI -> PedidoNuevo) ---
        dialog.setResultConverter(btn -> {
            if (btn == okType) {
                return construirPedidoDesdeUI(cbCliente, cbEstado, tfObs, contLineas);
            }
            return null;
        });

        return dialog;
    }

    private ComboBox<String> crearComboClientes() {
        ComboBox<String> cbCliente = new ComboBox<>(clientesFiltrados);/*Hacé una cajita para elegir clientes, 
                                                                   y llenala con los papelitos que están
                                                                   en la bolsa clientesFiltrados */
        cbCliente.setEditable(true);/*permite escribir para filtrarclientes, por alguna razon si quito
        //                            esto si se puede seleccionar un cliente */
        cbCliente.setPromptText("Nombre (cliente/mesa/empresa)");

        // 1) Filtrado en vivo mientras escribe
        cbCliente.getEditor().textProperty().addListener((obs, TextoPrevio, TextoActual) -> {/*Cada vez que el usuario escribe
                                                                         en el ComboBox, este listener se
                                                                         activa y ejecuta tu código para
                                                                         filtrar las opciones y mostrar
                                                                         solo las que coinciden */
            if (actualizandoEditor.get()) {
                return;
            }
            String txt = (TextoActual == null ? "" : TextoActual.trim().toLowerCase());/*Convierte lo que
                                                                                   escribió el usuario en
                                                                                   un texto limpio, sin
                                                                                   espacios raros, en
                                                                                   minúsculas, o vacío si
                                                                                   es null */
            clientesFiltrados.setPredicate(s -> s == null || txt.isEmpty() || s.toLowerCase().contains(txt));/*Esa línea decide, para cada cliente s, si se muestra o no 
                                                                                                         en el ComboBox según lo que escribió el usuario (txt): si
                                                                                                         txt está vacío, muestra todo; si no, muestra solo los que
                                                                                                         contienen ese texto (ignorando mayúsculas/minúsculas) */
            if (!cbCliente.isShowing() && !txt.isEmpty()) {
                cbCliente.show();/* Si el ComboBox NO está abierto
                                                                       y el usuario escribió algo,
                                                                       entonces abrilo */
            }
        });

        // 2) Interceptar el clic en cada celda de la lista para forzar la selección por ÍTEM (no por índice)
        cbCliente.setCellFactory(listView -> {
            var cell = new javafx.scene.control.ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item);
                }
            };

            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                if (!cell.isEmpty()) {
                    String item = cell.getItem();

                    // Seleccionar explícitamente por ítem y reflejar en el editor
                    actualizandoEditor.set(true);
                    try {
                        cbCliente.getSelectionModel().select(item); // <- selecciono por ítem (no índice)
                        cbCliente.setValue(item);                   // <- alinear value
                        cbCliente.getEditor().setText(item);        // <- mostrar en el editor
                        cbCliente.getEditor().positionCaret(item.length());
                    } finally {
                        actualizandoEditor.set(false);
                    }

                    // Cerrar el popup y consumir el evento para que el SelectionModel no re-seleccione por índice
                    cbCliente.hide();
                    ev.consume();
                }
            });

            return cell;
        });

        // Botón del combo (lo que se ve cuando está cerrado): que muestre el texto del ítem
        cbCliente.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
            }
        });

        // 3) NO restaures predicate en selección ni al cerrar; si querés, al ABRIR sí:
        cbCliente.showingProperty().addListener((o, was, is) -> {
            if (is) {
                // Mostrar TODO al abrir (opcional). Si preferís, podés quitar esta línea también.
                clientesFiltrados.setPredicate(s -> true);
            }
            // Al cerrar: NO toques el predicate (evita carreras de índice).
        });

        // 4) (Opcional) Alinear editor y value cuando se dispare la acción (Enter)
        cbCliente.setOnAction(e -> {
            String v = cbCliente.getValue();
            if (v != null) {
                actualizandoEditor.set(true);
                try {
                    cbCliente.getEditor().setText(v);
                    cbCliente.getEditor().positionCaret(v.length());
                } finally {
                    actualizandoEditor.set(false);
                }
            }
        });
        return cbCliente;
    }

    private ComboBox<TipoDePago> crearComboEstado() {
        ComboBox<TipoDePago> cbEstado = new ComboBox<>();
        cbEstado.getItems().setAll(TipoDePago.values());
        cbEstado.setValue(TipoDePago.TRANSFERENCIA);
        return cbEstado;
    }

    private TextField crearTextFieldObservaciones() {
        TextField tfObs = new TextField();
        tfObs.setPromptText("Observaciones (opcional)");
        return tfObs;
    }

    private GridPane construirGridDialogo(ComboBox<String> cbCliente,
            VBox contLineas,
            Button btnAgregarLinea,
            ComboBox<TipoDePago> cbEstado,
            TextField tfObs) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        int r = 0;

        grid.add(new Label("Nombre:"), 0, r);
        grid.add(cbCliente, 1, r++);

        grid.add(new Label("Productos:"), 0, r);
        VBox productosBox = new VBox(6, contLineas, btnAgregarLinea);
        grid.add(productosBox, 1, r++);

        grid.add(new Label("Estado:"), 0, r);
        grid.add(cbEstado, 1, r++);

        grid.add(new Label("Observaciones:"), 0, r);
        grid.add(tfObs, 1, r++);

        return grid;
    }

    private HBox crearLineaProducto(VBox contLineas) {
        // Lista filtrada que "envuelve" a la lista original de productos
        FilteredList<ProductosService.ProductoItem> productosFiltrados
                = new FilteredList<>(productos, p -> true);

        // Combo de productos con autocompletar y filtro "contiene"
        ComboBox<ProductosService.ProductoItem> cbProd = new ComboBox<>(productosFiltrados);
        cbProd.setPrefWidth(280);
        cbProd.setPromptText("Producto");
        cbProd.setEditable(true);

        final java.util.concurrent.atomic.AtomicBoolean actualizandoProd = new java.util.concurrent.atomic.AtomicBoolean(false);

        configurarConverterProducto(cbProd);
        configurarAutocompletarProducto(cbProd, productosFiltrados, actualizandoProd);
        configurarRendererProducto(cbProd, actualizandoProd);

        // Campo cantidad
        TextField tfCant = new TextField();
        tfCant.setPromptText("Cant.");
        tfCant.setPrefWidth(70);
        tfCant.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.matches("\\d*")) {
                tfCant.setText(b.replaceAll("[^\\d]", ""));
            }
        });

        // Botón eliminar
        Button btnDel = new Button("✕");
        btnDel.getStyleClass().add("btn-danger");

        HBox fila = new HBox(6, cbProd, tfCant, btnDel);
        fila.setAlignment(Pos.CENTER_LEFT);
        btnDel.setOnAction(e -> contLineas.getChildren().remove(fila));

        return fila;
    }

    private void configurarConverterProducto(ComboBox<ProductosService.ProductoItem> cbProd) {
        cbProd.setConverter(new StringConverter<>() {
            @Override
            /*toString:es un metodo que convierte un objeto en texto */
            public String toString(ProductosService.ProductoItem p) {/*p es un objeto productoitem */

                if (p == null) {/*si no tiene valor retorna vacio*/
                    return "";
                } else {
                    return p.nombre();/*sino retorna nombre */
                }/*La condición sirve para que el ComboBox muestre el nombre del producto cuando existe,
                   y muestre vacío sin errores cuando no hay ningún producto seleccionado por ejemplo,
                   las casillas vacias de la tabla*/
            }

            @Override
            public ProductosService.ProductoItem fromString(String text) {
                if (text == null) {
                    return null;
                }
                String s = text.trim();
                if (s.isEmpty()) {
                    return null;
                }
                for (ProductosService.ProductoItem p : productos) {
                    if (p.nombre().equalsIgnoreCase(s)) {
                        return p; // solo match exacto

                    }
                }
                return null;
            }
        });
    }

    private void configurarAutocompletarProducto(
            ComboBox<ProductosService.ProductoItem> cbProd,
            FilteredList<ProductosService.ProductoItem> productosFiltrados,
            java.util.concurrent.atomic.AtomicBoolean actualizandoProd) {

        // 1) Filtrar en vivo mientras escribe (contiene)
        cbProd.getEditor().textProperty().addListener((obs, TextoPrevio, TextoActual) -> {
            if (actualizandoProd.get()) {
                return; // NO filtrar si estoy seteando por código

            }
            String txt = (TextoActual == null ? "" : TextoActual.trim().toLowerCase());
            if (txt.isEmpty()) {
                // Mostrar TODO cuando no hay texto
                productosFiltrados.setPredicate(p -> true);
            } else {
                productosFiltrados.setPredicate(p
                        -> p != null && p.nombre() != null && p.nombre().toLowerCase().contains(txt)
                );
                if (!cbProd.isShowing()) {
                    cbProd.show();
                }
            }
        });
// 2) Al seleccionar: reflejar selección SIN tocar predicate ni limpiar editor
        cbProd.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) {
                Platform.runLater(() -> {
                    actualizandoProd.set(true);
                    try {
                        cbProd.setValue(b);
                        cbProd.getEditor().setText(b.nombre());
                        cbProd.getEditor().positionCaret(b.nombre().length());
                    } finally {
                        actualizandoProd.set(false);
                    }
                });
            }
        });

        // 3) Al abrir el popup, asegurate de mostrar todo
        cbProd.showingProperty().addListener((o, was, is) -> {
            if (is) {
                productosFiltrados.setPredicate(p -> true);
            }
        });

        // 4) Al perder foco, intentá resolver el texto contra la lista (match exacto),
        //    pero NO borres la selección si no hay match y NO limpies value cuando el editor queda vacío.
        cbProd.getEditor().focusedProperty().addListener((o, was, is) -> {
            if (!is) {
                var elegido = cbProd.getConverter().fromString(cbProd.getEditor().getText());
                if (elegido != null) {
                    actualizandoProd.set(true);
                    try {
                        cbProd.setValue(elegido);
                        cbProd.getEditor().setText(elegido.nombre());
                        cbProd.getEditor().positionCaret(elegido.nombre().length());
                        productosFiltrados.setPredicate(p -> true);
                    } finally {
                        actualizandoProd.set(false);
                    }
                } else {
                    productosFiltrados.setPredicate(p -> true);
                }
            }
        });
    }

    private void configurarRendererProducto(
            ComboBox<ProductosService.ProductoItem> cbProd,
            java.util.concurrent.atomic.AtomicBoolean actualizandoProd) {
        cbProd.setCellFactory(list -> {
            javafx.scene.control.ListCell<ProductosService.ProductoItem> cell
                    = new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(ProductosService.ProductoItem item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.nombre());
                }
            };

            // Interceptar el clic: seleccionar por OBJETO, actualizar editor, cerrar y consumir
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
                if (!cell.isEmpty()) {
                    var item = cell.getItem();
                    actualizandoProd.set(true);
                    try {
                        cbProd.getSelectionModel().select(item); // seleccionar por objeto (no índice)
                        cbProd.setValue(item);
                        cbProd.getEditor().setText(item.nombre());
                        cbProd.getEditor().positionCaret(item.nombre().length());
                    } finally {
                        actualizandoProd.set(false);
                    }
                    cbProd.hide();
                    ev.consume(); // evita que el SelectionModel re-mapée por índice
                }
            });

            return cell;
        });
        cbProd.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ProductosService.ProductoItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.nombre());
            }
        });
    }

    private boolean dialogInvalido(ComboBox<String> cbCliente, VBox contLineas) {
        String nombre = cbCliente.getEditor().getText();
        boolean nombreVacio = (nombre == null || nombre.isBlank()) && cbCliente.getValue() == null;
        if (nombreVacio) {
            return true;
        }

        // Debe haber al menos una línea válida
        for (var n : contLineas.getChildren()) {
            if (n instanceof HBox fila && esLineaValida(fila)) {
                return false; // habilitar OK

            }
        }
        return true; // deshabilitar OK
    }

    private boolean esLineaValida(HBox fila) {
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> cb
                = (ComboBox<ProductosService.ProductoItem>) fila.getChildren().get(0);
        TextField tf = (TextField) fila.getChildren().get(1);

        if (cb.getValue() == null) {
            return false;
        }
        if (tf.getText() == null || tf.getText().isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(tf.getText()) >= 1;
        } catch (NumberFormatException ignore) {
            return false;
        }
    }

    private PedidoNuevo construirPedidoDesdeUI(ComboBox<String> cbCliente,
            ComboBox<TipoDePago> cbEstado,
            TextField tfObs,
            VBox contLineas) {
        PedidoNuevo p = new PedidoNuevo();

        String nombre = cbCliente.getEditor().getText();
        if (nombre == null || nombre.isBlank()) {
            nombre = cbCliente.getValue();
        }
        p.nombreCliente = (nombre == null ? "" : nombre.trim());

        p.estado = cbEstado.getValue();
        p.observaciones = tfObs.getText() == null ? "" : tfObs.getText().trim();

        // Mapear las líneas
        for (var n : contLineas.getChildren()) {
            if (n instanceof HBox fila) {
                aLineaPedido(fila).ifPresent(lp -> {
                    p.idProductos.add(lp.idProducto());
                    p.cantidades.add(lp.cantidad());
                });
            }
        }
        return p;
    }

    private Optional<LineaPedido> aLineaPedido(HBox fila) {
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> cb
                = (ComboBox<ProductosService.ProductoItem>) fila.getChildren().get(0);
        TextField tf = (TextField) fila.getChildren().get(1);

        var prod = cb.getValue();
        if (prod == null) {
            return Optional.empty();
        }
        try {
            int c = Integer.parseInt(tf.getText());
            if (c >= 1) {
                return Optional.of(new LineaPedido(prod.id(), c));
            }
        } catch (NumberFormatException ignore) {
        }
        return Optional.empty();
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
    private void confirmarPedidoAsync(PedidoNuevo p) {
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
