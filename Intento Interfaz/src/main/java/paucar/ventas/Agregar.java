package paucar.ventas;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uade.tpo.demo.entity.TipoDePago;

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
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import paucar.service.ProductosService;

public class Agregar {

    // ====== DTOs internos ======
    public static class PedidoNuevo {

        public String nombreCliente;
        public java.util.List<Long> idProductos = new java.util.ArrayList<>();
        public java.util.List<Integer> cantidades = new java.util.ArrayList<>();
        public TipoDePago estado;
        public String observaciones;
    }

    public static record Formulario(Long idProducto, Integer cantidad) {

    }

    // ====== Datos de trabajo que vienen de Ventas ======
    private final ObservableList<String> clientes; // lista base
    private final ObservableList<ProductosService.ProductoItem> productos; // lista base

    public Agregar(ObservableList<String> clientes,
            ObservableList<ProductosService.ProductoItem> productos) {
        // Usamos directamente las listas provistas por Ventas
        this.clientes = clientes;
        this.productos = productos;
    }

    /*Muestra el diálogo modal*/
    public Optional<PedidoNuevo> show(Window owner) {
        Dialog<PedidoNuevo> dialog = construirDialogoAgregar();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog.showAndWait();
    }

    private Dialog<PedidoNuevo> construirDialogoAgregar() {
        Dialog<PedidoNuevo> dialog = new Dialog<>();
        dialog.setTitle("Agregar pedido");
        dialog.setResizable(true);

        ButtonType okType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        FilteredList<String> clientesFiltrados = new FilteredList<>(clientes, s -> true);
        ComboBox<String> cbCliente = crearComboClientes(clientesFiltrados);

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
        // --- Validación del botón OK ---
        // Tomar referencias de la fila inicial (la que viene por defecto)
        HBox fila0 = (HBox) contLineas.getChildren().get(0);
        @SuppressWarnings("unchecked")
        ComboBox<ProductosService.ProductoItem> cbProd0
                = (ComboBox<ProductosService.ProductoItem>) fila0.getChildren().get(0);
        TextField tfCant0 = (TextField) fila0.getChildren().get(1);

        Node okBtn = dialog.getDialogPane().lookupButton(okType);
        okBtn.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> BotonAgregarInhabilitado(cbCliente, contLineas),
                        // Dependencias que ya tenías:
                        cbCliente.getEditor().textProperty(),
                        contLineas.getChildren(),
                        // 🔹 NUEVO: observar cambios internos de la fila inicial
                        cbProd0.valueProperty(),
                        tfCant0.textProperty()
                )
        );

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);      // sin scroll horizontal
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);  // aparece scroll vertical si hace falta

        dialog.getDialogPane().setContent(sp);

// Opcional pero recomendado: permitir agrandar el diálogo
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(600, 500);

        // --- ResultConverter (mapea UI -> PedidoNuevo) ---
        dialog.setResultConverter(btn -> {
            if (btn == okType) {
                return construirPedidoDesdeUI(cbCliente, cbEstado, tfObs, contLineas);
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
        //                            esto si se puede seleccionar un cliente */
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

// 2) No hay filas → deshabilitado
        if (contLineas.getChildren().isEmpty()) {
            return true;
        }

        // Debe haber al menos una línea válida
        for (var n : contLineas.getChildren()) {/*recorre todas las filas si encuentra un producto valido
                                               con una cantidad valida almenos 1 ya lo toma como valido
                                               y retorna false*/
            if (n instanceof HBox fila && ValidarFichaPedido(fila)) {
                return false;
                /*  el false habilita el boton agregar pporque almenos encontro una ficha
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
            return Integer.parseInt(Cant.getText()) >= 1;/*retorna true si la cantidad convertida en
                                                         entero es mayor o igual a uno */
        } catch (NumberFormatException ignore) {
            return false;/*si el usuario puso un valor que no es un numero */
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
                FichaPedido(fila).ifPresent(lp -> {
                    p.idProductos.add(lp.idProducto());
                    p.cantidades.add(lp.cantidad());
                });
            }
        }
        return p;
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
