package paucar.ventas;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uade.tpo.demo.entity.TipoCliente;
import com.uade.tpo.demo.entity.TipoDePago;
import com.uade.tpo.demo.entity.dto.VentaRequest;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import paucar.service.ClientesService;
import paucar.service.ProductosService;

public class Agregar {

    public static record Formulario(Long idProducto, Integer cantidad) {

    }

    // ====== Datos de trabajo que vienen de Ventas ======
    private final ObservableList<String> clientes; // lista base
    private final ObservableList<ProductosService.ProductoItem> productos; // lista base
    private final ClientesService clientesService;
    private final VentaRequest venta;

    public Agregar(ObservableList<String> clientes,
            ObservableList<ProductosService.ProductoItem> productos, ClientesService clientesService, VentaRequest venta) {
        // Usamos directamente las listas provistas por Ventas
        this.clientes = clientes;
        this.clientesService = clientesService;
        this.productos = productos;
        this.venta = venta;
    }

    private TipoCliente tipoSeleccionado = null;

    public TipoCliente getTipoSeleccionado() {
        return tipoSeleccionado;
    }

    public Optional<String> Mostrar(Window owner) {
        Dialog<String> VentanaEmergente = construirDialogoAgregar();/*creo una variable y le asigno todo
                                                                    el contenido que construye el metodo */

        if (owner != null) {/*Esta línea pregunta si existe una ventana principal (owner) antes de asociar
                            el diálogo a ella */
            VentanaEmergente.initOwner(owner);/*Le asigna una ventana principal para que la ventana
                                               emergente se muestre encima y funcione como un diálogo
                                               modal (ventana emergente que no te permite acceder a la
                                               ventana de atras hasta que la cierres*/
        }
        return VentanaEmergente.showAndWait();/*Devuelve el resultado de mostrar la ventana emergente y
                                              esperar hasta que el usuario la cierre */
    }

    private Dialog<String> construirDialogoAgregar() {
        Dialog<String> dialog = new Dialog<>();/*Creá una ventana emergente llamada dialog que, cuando se
                                               cierre presionando ‘Agregar’, va a devolver un texto como
                                               resultado */

        dialog.setTitle("Agregar pedido");/*nombra a la ventana emergente “Agregar pedido” como
                                                título en la barra superior */
        dialog.setResizable(true);

        ButtonType okType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        // --- 1) Selector tipo cliente (Mesa - Cliente - Empresa) ---
        ToggleGroup tgTipoCliente = new ToggleGroup();
        ToggleButton btnMesa = new ToggleButton("Mesa");
        ToggleButton btnCliente = new ToggleButton("Cliente");
        ToggleButton btnEmpresa = new ToggleButton("Empresa");

        btnMesa.setToggleGroup(tgTipoCliente);
        btnCliente.setToggleGroup(tgTipoCliente);
        btnEmpresa.setToggleGroup(tgTipoCliente);

        // Por defecto: Cliente seleccionado (centro)
        btnCliente.setSelected(true);

        // Guardamos el valor de dominio en userData para leerlo fácil más adelante
        btnMesa.setUserData(TipoCliente.MESA);
        btnCliente.setUserData(TipoCliente.CLIENTE);
        btnEmpresa.setUserData(TipoCliente.EMPRESA);

        HBox selectorTipoCliente = new HBox(6, btnMesa, btnCliente, btnEmpresa);
        selectorTipoCliente.setAlignment(Pos.CENTER_LEFT);

        // (Opcional) estilos de “segmentado”
        btnMesa.getStyleClass().add("segmented-left");
        btnCliente.getStyleClass().add("segmented-center");
        btnEmpresa.getStyleClass().add("segmented-right");

        // --- 2) Combo de clientes (tu autocompletar por texto se mantiene)
        FilteredList<String> clientesFiltrados = new FilteredList<>(clientes, s -> true);
        ComboBox<String> cbCliente = crearComboClientes(clientesFiltrados);

        // --- 3) CARGA INICIAL según el tipo seleccionado (por defecto CLIENTE) ---
        if (tgTipoCliente.getSelectedToggle() != null && clientesService != null) {
            var tipo = (TipoCliente) tgTipoCliente.getSelectedToggle().getUserData();
            cbCliente.setDisable(true);

            new Thread(() -> {
                java.util.List<String> nombres;
                try {
                    // Consulta al backend (ya ordena y de-duplica en tu servicio)
                    nombres = clientesService.obtenerNombresPorTipo(tipo);
                } catch (Exception ex) {
                    System.err.println("Carga inicial nombres por tipo: " + ex.getMessage());
                    nombres = java.util.List.of(); // fallback
                }
                final java.util.List<String> nombresFinal = nombres;

                Platform.runLater(() -> {
                    System.out.println("[Agregar] " + tipo + " => " + nombresFinal.size());
                    // Reemplaza la lista base: desde acá el FilteredList filtra por texto como siempre
                    clientes.setAll(nombresFinal);

                    // Reaplico tu predicate de texto actual (autocompletar)
                    String txt = cbCliente.getEditor().getText();
                    String lower = (txt == null ? "" : txt.trim().toLowerCase());
                    clientesFiltrados.setPredicate(s -> s != null && (lower.isEmpty() || s.toLowerCase().contains(lower)));

                    cbCliente.setDisable(false);
                });
            }, "cargar-clientes-inicial").start();
        }

        // --- 4) RECARGA al cambiar el TipoCliente (Mesa/Cliente/Empresa) ---
        tgTipoCliente.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null || clientesService == null) {
                return;
            }

            var tipo = (TipoCliente) b.getUserData();
            cbCliente.setDisable(true);

            new Thread(() -> {
                java.util.List<String> nombres;
                try {
                    nombres = clientesService.obtenerNombresPorTipo(tipo);
                } catch (Exception ex) {
                    System.err.println("Carga nombres por tipo: " + ex.getMessage());
                    nombres = java.util.List.of(); // fallback
                }
                final java.util.List<String> nombresFinal = nombres;

                Platform.runLater(() -> {
                    String sel = cbCliente.getValue();

                    clientes.setAll(nombresFinal);

                    String txt = cbCliente.getEditor().getText();
                    String lower = (txt == null ? "" : txt.trim().toLowerCase());
                    clientesFiltrados.setPredicate(s -> s != null && (lower.isEmpty() || s.toLowerCase().contains(lower)));

                    // Si lo elegido dejó de existir para el nuevo tipo, limpiamos
                    if (sel != null && !nombresFinal.contains(sel)) {
                        cbCliente.setValue(null);
                        cbCliente.getEditor().clear();
                    }

                    cbCliente.setDisable(false);
                });
            }, "cargar-clientes-por-tipo").start();
        });

        // --- 5) Productos (líneas dinámicas) ---
        VBox contLineas = new VBox(6);
        contLineas.setPadding(new Insets(6));
        Button btnAgregarLinea = new Button("+ Producto");
        btnAgregarLinea.getStyleClass().add("btn-primary");
        btnAgregarLinea.setOnAction(e -> contLineas.getChildren().add(crearLineaProducto(contLineas)));
        contLineas.getChildren().add(crearLineaProducto(contLineas)); // al menos una línea inicial

        // --- 6) Estado y observaciones ---
        ComboBox<TipoDePago> cbEstado = crearComboEstado();
        TextField tfObs = TextFieldObservaciones();

        // --- 7) Layout ---
        GridPane grid = buildFormularioPedido(cbCliente, contLineas, btnAgregarLinea, cbEstado, tfObs, selectorTipoCliente);

        // --- 8) Validación del botón OK ---
        HBox fila0 = (HBox) contLineas.getChildren().get(0);
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> cbProd0 = (ComboBox<ProductosService.ProductoItem>) fila0.getChildren().get(0);
        TextField tfCant0 = (TextField) fila0.getChildren().get(1);

        Node okBtn = dialog.getDialogPane().lookupButton(okType);
        okBtn.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> BotonAgregarInhabilitado(cbCliente, contLineas)
                        || tgTipoCliente.getSelectedToggle() == null,
                        cbCliente.getEditor().textProperty(),
                        contLineas.getChildren(),
                        cbProd0.valueProperty(),
                        tfCant0.textProperty(),
                        tgTipoCliente.selectedToggleProperty()
                )
        );

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dialog.getDialogPane().setContent(sp);

        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(600, 500);

        dialog.setResultConverter(btn -> {
            if (btn == okType) {
                if (tgTipoCliente.getSelectedToggle() == null) {
                    return null;
                }
                // Guardar el tipo
                this.tipoSeleccionado = (TipoCliente) tgTipoCliente.getSelectedToggle().getUserData();
                // Construir la venta y devolver el nombre
                return ConstruirVentaDirectoEnRequest(cbCliente, cbEstado, tfObs, contLineas);
            }
            return null;
        });

        return dialog;
    }

    private ComboBox<String> crearComboClientes(FilteredList<String> clientesFiltrados) {
        ComboBox<String> cbCliente = new ComboBox<>(clientesFiltrados);/*Hacé una cajita para elegir clientes, 
                                                                   y llenala con los papelitos que están
                                                                   en la bolsa clientesFiltrados */

        cbCliente.setEditable(true);/*permite escribir para filtrarclientes, por alguna razon si quito
                                           esto si se puede seleccionar un cliente */
        cbCliente.setPromptText("Nombre (cliente/mesa/empresa)");

        AtomicBoolean actualizandoEditor = new AtomicBoolean(false);

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
                cbCliente.show();/* Si el ComboBox NO está abierto y el usuario escribió algo, entonces
                                 abrilo */
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
                // Mostrar Todo al abrir (opcional). Si preferís, podés quitar esta línea también.
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
        ComboBox<TipoDePago> cbEstado = new ComboBox<>();/*crea un ComboBox vacío que podrá contener
                                                         valores del enum TipoDePago */
        cbEstado.getItems().setAll(TipoDePago.values());/*Carga al ComboBox todas las opciones posibles de
                                                        tipo de pago para que el usuario pueda elegir
                                                        cualquiera de ellas*/
        cbEstado.setValue(TipoDePago.DEBE);/*establece que por defecto el valor seleccionado en el
                                           ComboBox sea DEBE, es decir, que el pedido recién creado esté
                                           marcado como pendiente de pago hasta que se cambie a otro
                                           estado*/
        return cbEstado;/*retorna el cbestado*/
    }

    private TextField TextFieldObservaciones() {
        TextField inputObservaciones = new TextField();/*crea un objeto visual en el que se escribe */
        inputObservaciones.setPromptText("Observaciones (opcional)");/*escribe dentro de el objeto
                                                                        el texto observaciones (opcional)
                                                                     en color gris claro como sugerencia y
                                                                 se elimina cuando el usuario escribe algo */
        return inputObservaciones;/*retorna inputobservaciones */
    }

    private GridPane buildFormularioPedido(ComboBox<String> cbCliente, VBox ListaDeProductos, Button btnAñadirProducto,
            ComboBox<TipoDePago> cbEstado, TextField inputObservaciones, Node selectorTipoCliente) {

        GridPane grid = new GridPane();/*Crea un contenedor en forma de grilla para colocar las etiquetas
                                       y los campos a rellenar del formulario */

        grid.setHgap(15);/*establece que haya 15 pixeles de separación horizontal entre la etiqueta
                                y el campo a rellenar y la respuesta */

        grid.setVgap(15);/*establece que haya 10 pixeles de separación vertical entre las etiquetas
                                y los campos a rellenar */

        grid.setPadding(new Insets(10));/*establece que hay un margen 10px alrededor
                                                            de dialog para que se vea estetico*/

        int r = 1;/*esta variable r es para controlar la fila en la que se va a colocar cada elemento del
                  formulario, y se va incrementando cada vez que se agrega un nuevo elemento para que no
                  se sobrepongan */

        grid.add(new Label("Tipo de cliente:"), 0, r);/*Esta línea agrega la etiqueta
                                                                        “Tipo de cliente:” en la columna
                                                                        0 y la fila r del GridPane */
        grid.add(selectorTipoCliente, 1, r++);/*Esta línea agrega el selector de tipo de
                                                           cliente (que es un HBox con los botones) en la
                                                           columna 1 y la fila r++*/

        grid.add(new Label("Nombre:"), 0, r);/*Esta línea agrega la etiqueta “Nombre:” 
                                                               en la columna 0 y la fila r del GridPane */

        grid.add(cbCliente, 1, r++);/*agrega el ComboBox del cliente(la parte donde escribimos
                                                el nombre del cliente) en columna 1 y fila r*/

        grid.add(new Label("Productos:"), 0, r);/*añade la etiqueta productos*/

        VBox productosBox = new VBox(6, ListaDeProductos, btnAñadirProducto);/*Crea un contenedor
                                                                                   vertical (VBox) con 6
                                                                                   píxeles de espacio entre
                                                                                   cada producto y el 
                                                                                   botón btnAñadirProducto*/

        grid.add(productosBox, 1, r++);/*Esta línea coloca el contenedor productosBox en la
                                                    columna 1 y fila r del GridPane, y después aumenta r
                                                    para pasar a la siguiente fila */

        grid.add(new Label("Estado:"), 0, r);/*añade la etiqueta estado */

        grid.add(cbEstado, 1, r++);/*añade el selector de tipodepago o el estado del pago */

        grid.add(new Label("Observaciones:"), 0, r);/*añade la etiqueta observaciones */
        grid.add(inputObservaciones, 1, r++);/*añade el campo de texto para las observaciones */

        return grid;/*devuelve el GridPane completo con todos los elementos del formulario ya organizados
                     en filas y columnas */
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

        final AtomicBoolean actualizandoProd = new AtomicBoolean(false);

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
        Button btnDelete = new Button("✕");
        btnDelete.getStyleClass().add("btn-danger");

        HBox fila = new HBox(6, cbProd, tfCant, btnDelete);
        fila.setAlignment(Pos.CENTER_LEFT);
        btnDelete.setOnAction(e -> contLineas.getChildren().remove(fila));

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
                // Mostrar Todo cuando no hay texto
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

    private boolean BotonAgregarInhabilitado(ComboBox<String> cbCliente, VBox contLineas) {
        
        String nombre = cbCliente.getEditor().getText();/*Guarda en la variable nombre el texto que el
                                                         usuario escribió en el ComboBox cbCliente */
        boolean nombreVacio = (nombre == null || nombre.isBlank()) && cbCliente.getValue() == null;/*guarda en nombrevacio si el nombre no
                                                                                                   tiene valor o si esta en blanco y tambien
                                                                                                   si cbcliente carece de valor */
        if (nombreVacio) {/*si nombre vacio es true retorna true, ya que este metodo es para verificar si es invalido */
            return true;
        }
        if (contLineas.getChildren().isEmpty()) {/*No hay filas → deshabilitado */
            return true;
        }

        for (var n : contLineas.getChildren()) {/*recorre todas las filas si encuentra un producto valido
                                               con una cantidad valida almenos 1 ya lo toma como valido
                                               y retorna false*/
            if (n instanceof HBox fila && ValidarFichaPedido(fila)) {
                return false;/*el false habilita el boton agregar pporque almenos encontro una ficha
                               valida*/
            }
        }
        return true;
        /* si retorna true es que no encontro ninguno y queda inhabilitado el boton agregar */
    }

    private boolean ValidarFichaPedido(HBox fila) {
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> ComboProductos/*Esta es la variable que contiene todos los
                                                              productos que el usuario puede elegir */
                = (ComboBox<ProductosService.ProductoItem>) fila.getChildren().get(0);/*ComboProductos contiene el primer elemento
                                                                                             del HBox, que es un ComboBox, y esa línea lo
                                                                                            convierte (cast) al tipo ComboBox<ProductosService.ProductoItem>
                                                                                            para poder usarlo como un ComboBox de productos */
        TextField Cant = (TextField) fila.getChildren().get(1);/* se le asigna a cant el segundo
                                                                     elemento que es la cantidad*/

        if (ComboProductos.getValue() == null) {/*Revisa si no hay ningún producto seleccionado en el
                                                ComboBox; si no hay nada elegido, devuelve false porque
                                                la línea es inválida */
            return false;
        }
        if (Cant.getText() == null || Cant.getText().isBlank()) {/*revisa si el campo cantidad esta vacio
                                                                 o en blanco y retorna false si esto es
                                                                 asi*/
            return false;
        }
        try {

            if (Cant.getText().isBlank()) {
                return false;
            }
            return Integer.parseInt(Cant.getText()) >= 1;

        } catch (NumberFormatException ignore) {
            return false;/*si el usuario puso un valor que no es un numero */
        }
    }

    private String ConstruirVentaDirectoEnRequest(ComboBox<String> cbCliente, ComboBox<TipoDePago> cbEstado,
            TextField tfObs, VBox contLineas) {

        // 1) Nombre (cliente/mesa/empresa)
        String nombre = cbCliente.getEditor().getText();
        if (nombre == null || nombre.isBlank()) {
            nombre = cbCliente.getValue();
        }
        String nombreLimpio = (nombre == null ? "" : nombre.trim());

        // 2) Estado y observaciones -> directo a VentaRequest
        venta.setEstado(cbEstado.getValue());

        venta.setObservaciones(tfObs.getText() == null ? "" : tfObs.getText().trim());

        // 3) Asegurar listas y limpiarlas
        if (venta.getIdProductos() == null) {
            venta.setIdProductos(new java.util.ArrayList<>());
        } else {
            venta.getIdProductos().clear();
        }
        if (venta.getCantidades() == null) {
            venta.setCantidades(new java.util.ArrayList<>());
        } else {
            venta.getCantidades().clear();
        }

        // 4) Volcar cada fila válida usando tu FichaPedido(...)
        for (var n : contLineas.getChildren()) {
            if (n instanceof HBox fila) {
                FichaPedido(fila).ifPresent(linea -> {
                    venta.getIdProductos().add(linea.idProducto());
                    venta.getCantidades().add(linea.cantidad());
                });
            }
        }
        // 5) El diálogo devuelve SOLO el nombre; Ventas resuelve idCliente y hace el POST
        return nombreLimpio;
    }

    private Optional<Formulario> FichaPedido(HBox fila) {
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> ComboProducto /*comboproducto es la lista entera de todos
                                                               los productos disponibles para elegir */
                = (ComboBox<ProductosService.ProductoItem>) fila.getChildren().get(0);/*Esa línea agarra el primer elemento del HBox
                                                                                             (que es un ComboBox de productos) y lo convierte
                                                                                             al tipo correcto para poder usarlo */
        TextField Cant = (TextField) fila.getChildren().get(1);/*obtiene la cantidad que es el
                                                                     segundo elemento de fila y es un
                                                                     textfield, un texto visual */

        var ProdElegido = ComboProducto.getValue();/*se le asigna el producto que eligio el usuario de
                                                   toda la lista de productos de comboproducto*/
        if (ProdElegido == null) {/*si el usuario no eligio ningun producto y por lo tanto queda null */
            return Optional.empty();/*Devuelve un Optional vacío para indicar que
                                      no se construye ninguna LineaPedido */
        }
        int CantElegida = Integer.parseInt(Cant.getText());/*convierte la cantidad que escribio el
                                                               usuario de texto a un numero entero y lo
                                                               guarda en CantElegida */
        if (CantElegida >= 1) {/*si la cantidad elegida es mayor o igual a 1 */
            return Optional.of(new Formulario(ProdElegido.id(), CantElegida));/*Devuelve un Optional
                                                                                   con la línea de pedido
                                                                                   construida */
        }
        return Optional.empty();/*sino devuelve un Optional vacío para indicar que no se construye ninguna
                                LineaPedido */
    }
}
