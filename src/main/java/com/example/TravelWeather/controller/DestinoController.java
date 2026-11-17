package com.example.TravelWeather.controller;

import com.example.TravelWeather.model.Destino;
import com.example.TravelWeather.repository.DestinoRepository;
import com.example.TravelWeather.repository.RegionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinos")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
public class DestinoController {

    @Autowired
    private DestinoRepository destinoRepository;

    @Autowired
    private RegionRepository regionRepository;

    // 1. CONSULTAR (GET) - Abierto para que los usuarios puedan ver la tabla
    @GetMapping
    public List<Destino> obtenerTodos() {
        return destinoRepository.findAll();
    }

    // 2. REGISTRAR (POST) - Protegido (Solo Admin) + Integridad Referencial
    @PostMapping
    public ResponseEntity<?> crearDestino(
            @RequestBody Destino destino,
            @RequestHeader(value = "X-Usuario-Logueado-Id", required = false) Integer rolLogueado) {
        
        // Candado de seguridad
        if (rolLogueado == null || rolLogueado != 1) {
            return ResponseEntity.status(403).body("Acceso Denegado: Solo el Administrador puede crear destinos.");
        }

        if (destino.getRegion() == null || destino.getRegion().getId() == null || !regionRepository.existsById(destino.getRegion().getId())) {
            return ResponseEntity.badRequest().body("Error de integridad: La región asignada no existe en la base de datos.");
        }
        
        Destino nuevoDestino = destinoRepository.save(destino);
        return ResponseEntity.ok(nuevoDestino);
    }

    // 3. ACTUALIZAR (PUT) - Protegido (Solo Admin) + Integridad Referencial
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarDestino(
            @PathVariable Integer id, 
            @RequestBody Destino destinoActualizado,
            @RequestHeader(value = "X-Usuario-Logueado-Id", required = false) Integer rolLogueado) {
        
        // Candado de seguridad
        if (rolLogueado == null || rolLogueado != 1) {
            return ResponseEntity.status(403).body("Acceso Denegado: Solo el Administrador puede editar destinos.");
        }

        if (!destinoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        if (destinoActualizado.getRegion() == null || !regionRepository.existsById(destinoActualizado.getRegion().getId())) {
            return ResponseEntity.badRequest().body("Error de integridad: La región asignada no es válida.");
        }
        
        destinoActualizado.setId(id); 
        return ResponseEntity.ok(destinoRepository.save(destinoActualizado));
    }

    // 4. ELIMINAR (DELETE) - Protegido (Solo Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarDestino(
            @PathVariable Integer id,
            @RequestHeader(value = "X-Usuario-Logueado-Id", required = false) Integer rolLogueado) {
        
        // Candado de seguridad
        if (rolLogueado == null || rolLogueado != 1) {
            return ResponseEntity.status(403).body("Acceso Denegado: Solo el Administrador puede borrar destinos.");
        }

        if (!destinoRepository.existsById(id)) {
            return ResponseEntity.notFound().build(); 
        }
        
        destinoRepository.deleteById(id);
        return ResponseEntity.ok("Registro eliminado correctamente, respetando las reglas de la BD.");
    }
}