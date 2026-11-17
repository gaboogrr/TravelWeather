package com.example.TravelWeather.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "region")
public class Region {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String nombre;
    private String continente;
}