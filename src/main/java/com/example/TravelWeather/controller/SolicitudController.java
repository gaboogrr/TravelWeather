package com.example.TravelWeather.controller;

import com.example.TravelWeather.model.Solicitud;
import com.example.TravelWeather.repository.SolicitudRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes")
@CrossOrigin(origins = "*")
public class SolicitudController {

    @Autowired private SolicitudRepository solicitudRepository;

    @GetMapping
    public List<Solicitud> obtenerTodas() {
        return solicitudRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> crearSolicitud(@RequestBody Solicitud solicitud) {
        solicitud.setEstado("PENDIENTE");
        return ResponseEntity.ok(solicitudRepository.save(solicitud));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarSolicitud(@PathVariable Integer id, @RequestBody Solicitud detalles) {
        return solicitudRepository.findById(id).map(s -> {
            s.setEstado(detalles.getEstado());
            s.setTipo(detalles.getTipo());
            return ResponseEntity.ok(solicitudRepository.save(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarSolicitud(@PathVariable Integer id) {
        if (!solicitudRepository.existsById(id)) return ResponseEntity.notFound().build();
        solicitudRepository.deleteById(id);
        return ResponseEntity.ok("Solicitud eliminada.");
    }
}