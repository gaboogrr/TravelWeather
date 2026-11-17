package com.example.TravelWeather.repository;

import com.example.TravelWeather.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
}