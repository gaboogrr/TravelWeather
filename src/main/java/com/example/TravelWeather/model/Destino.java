package com.example.TravelWeather.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "destino")
public class Destino {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String nombre;
    private String clima;
    
    @Column(name = "temperatura_celsius")
    private Double temperaturaCelsius;
    
    private String recomendacion;

    // ¡La llave foránea! Relación de Muchos a Uno con la tabla Región
    @ManyToOne
    @JoinColumn(name = "region_id")
    private Region region;
}