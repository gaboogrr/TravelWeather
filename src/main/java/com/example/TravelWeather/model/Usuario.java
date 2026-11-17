package com.example.TravelWeather.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String nombre;
    private String email;

    @ManyToOne
    @JoinColumn(name = "rol_id")
    private Rol rol;
}