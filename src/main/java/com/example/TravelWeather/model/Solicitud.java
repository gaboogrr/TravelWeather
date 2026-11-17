package com.example.TravelWeather.model;

import jakarta.persistence.*;

@Entity
public class Solicitud {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer idUsuario;
    private String tipo; // Ej: Acceso, Rectificacion, Cancelacion, Oposicion
    private String estado; // Ej: PENDIENTE, APROBADA

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}