package com.example.TravelWeather.repository;

import com.example.TravelWeather.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {
    List<Notificacion> findByIdUsuario(Integer idUsuario);
}