package com.uade.tpo.demo.entity.dto;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

public class VentaDTO {
    private LocalDateTime fecha;
    private String dia;  
    private String nombreCliente;
    private String descripcion;
    private Double monto;

    public VentaDTO() {
    }

    // Constructor actualizado
    public VentaDTO(LocalDateTime fecha, String dia, String nombreCliente, String descripcion, Double monto) {
        this.fecha = fecha;
        this.dia = dia;
        this.nombreCliente = nombreCliente;
        this.descripcion = descripcion;
        this.monto = monto;
    }

    // Constructor alternativo que calcula el día automáticamente
    public VentaDTO(LocalDateTime fecha, String nombreCliente, String descripcion, Double monto) {
        this.fecha = fecha;
        this.dia = fecha.getDayOfWeek()
                       .getDisplayName(TextStyle.FULL, 
                       new Locale("es", "ES"));
        this.nombreCliente = nombreCliente;
        this.descripcion = descripcion;
        this.monto = monto;
    }

    
    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    
    public String getDia() {
        return dia;
    }

    public void setDia(String dia) {
        this.dia = dia;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }
}