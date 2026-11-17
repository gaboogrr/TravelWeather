package com.example.TravelWeather.controller;

import com.example.TravelWeather.model.Notificacion;
import com.example.TravelWeather.repository.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@CrossOrigin(origins = "*")
public class NotificacionController {

    @Autowired private NotificacionRepository notificacionRepository;

    @GetMapping
    public List<Notificacion> obtenerTodas() {
        return notificacionRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> crearNotificacion(@RequestBody Notificacion notificacion) {
        return ResponseEntity.ok(notificacionRepository.save(notificacion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarNotificacion(@PathVariable Integer id, @RequestBody Notificacion detalles) {
        return notificacionRepository.findById(id).map(n -> {
            n.setMensaje(detalles.getMensaje());
            n.setLeida(detalles.isLeida());
            return ResponseEntity.ok(notificacionRepository.save(n));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarNotificacion(@PathVariable Integer id) {
        if (!notificacionRepository.existsById(id)) return ResponseEntity.notFound().build();
        notificacionRepository.deleteById(id);
        return ResponseEntity.ok("Notificación eliminada.");
    }
}