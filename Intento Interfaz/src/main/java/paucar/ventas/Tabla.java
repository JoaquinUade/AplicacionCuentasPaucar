package paucar.ventas;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.uade.tpo.demo.entity.TipoDePago;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class Tabla extends VBox {

    private final TableView<Ventas.Fila> tabla = new TableView<>();
    private final Button btnEliminar = new Button("Eliminar Venta");
    private final Consumer<Ventas.Fila> onEliminar;
    private final NumberFormat moneda;

    public Tabla(ObservableList<Ventas.Fila> items, Locale locale, Consumer<Ventas.Fila> onEliminar) {
        this.moneda = NumberFormat.getCurrencyInstance(locale);
        this.onEliminar = onEliminar;

        setSpacing(8);
        setPadding(new Insets(0));

        tabla.setEditable(true);
        tabla.setItems(items);
        tabla.getColumns().setAll(crearColumnas());

btnEliminar.disableProperty()
    .bind(Bindings.isNull(tabla.getSelectionModel().selectedItemProperty()));

btnEliminar.setOnAction(e -> {
    var sel = tabla.getSelectionModel().getSelectedItem();
    if (sel != null && onEliminar != null) {
        onEliminar.accept(sel); // ✅ avisa a Ventas
    }
});

        VBox.setVgrow(tabla, Priority.ALWAYS);
        getChildren().addAll(tabla, btnEliminar);
    }

    private List<TableColumn<Ventas.Fila, ?>> crearColumnas() {
        return List.of(
            colNombre(),
            colDescripcion(),
            colMonto(),
            colEstado(),
            colObservaciones()
        );
    }

    private TableColumn<Ventas.Fila, String> colNombre() {
        var col = new TableColumn<Ventas.Fila, String>("Nombre");
        col.setCellValueFactory(c -> c.getValue().nombreProperty());
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setOnEditCommit(e -> e.getRowValue().setNombre(e.getNewValue()));
        col.setPrefWidth(200);
        return col;
    }

    private TableColumn<Ventas.Fila, String> colDescripcion() {
        var col = new TableColumn<Ventas.Fila, String>("Descripción");
        col.setCellValueFactory(c -> c.getValue().descripcionProperty());
        col.setCellFactory(tc -> new TableCell<>() {
            private final javafx.scene.text.Text text = new javafx.scene.text.Text();
            {
                text.wrappingWidthProperty().bind(tc.widthProperty().subtract(16));
                setGraphic(text);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    text.setText(null);
                    setTooltip(null);
                    setGraphic(null);
                } else {
                    text.setText(item);
                    setGraphic(text);
                    setTooltip(new Tooltip(item));
                }
            }
        });
        col.setEditable(false);
        col.setPrefWidth(420);
        return col;
    }

    private TableColumn<Ventas.Fila, String> colMonto() {
        var col = new TableColumn<Ventas.Fila, String>("Monto");
        col.setCellValueFactory(c ->
            Bindings.createStringBinding(
                () -> formatMoneda(c.getValue().getMonto()),
                c.getValue().montoProperty()
            )
        );
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setEditable(false);
        col.setPrefWidth(140);
        return col;
    }

    private TableColumn<Ventas.Fila, TipoDePago> colEstado() {
        var col = new TableColumn<Ventas.Fila, TipoDePago>("Estado");
        col.setCellValueFactory(c -> c.getValue().estadoProperty());
        col.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<TipoDePago> combo = new ComboBox<>();
            {
                combo.getItems().setAll(TipoDePago.values());
                combo.valueProperty().addListener((o, a, b) -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        getTableView().getItems().get(getIndex()).setEstado(b);
                    }
                });
            }
            @Override protected void updateItem(TipoDePago item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : combo);
                if (!empty) combo.setValue(item);
            }
        });
        col.setPrefWidth(180);
        return col;
    }

    private TableColumn<Ventas.Fila, String> colObservaciones() {
        var col = new TableColumn<Ventas.Fila, String>("Observaciones");
        col.setCellValueFactory(c -> c.getValue().observacionesProperty());
        col.setCellFactory(tc -> new TableCell<>() {
            private final javafx.scene.text.Text text = new javafx.scene.text.Text();
            {
                text.wrappingWidthProperty().bind(tc.widthProperty().subtract(16));
                setGraphic(text);
                setPrefHeight(Region.USE_COMPUTED_SIZE);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    text.setText(null);
                    setTooltip(null);
                    setGraphic(null);
                } else {
                    text.setText(item);
                    setGraphic(text);
                    setTooltip(new Tooltip(item));
                }
            }
        });
        col.setPrefWidth(179);
        return col;
    }

    private String formatMoneda(BigDecimal v) {
        if (v == null) return "$ 0,00";
        return moneda.format(v);
    }

    public TableView<Ventas.Fila> getTable() {
        return tabla;
    }

    public Node asNode() {
        return this;
    }
}
