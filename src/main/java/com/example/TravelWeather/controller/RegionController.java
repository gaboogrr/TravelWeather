package com.example.TravelWeather.controller;

import com.example.TravelWeather.model.Region;
import com.example.TravelWeather.repository.RegionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regiones")
@CrossOrigin(origins = "*")
public class RegionController {

    @Autowired private RegionRepository regionRepo;

    @GetMapping
    public List<Region> obtenerTodas() { 
        return regionRepo.findAll(); 
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Region region) {
        return ResponseEntity.ok(regionRepo.save(region));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody Region region) {
        if(!regionRepo.existsById(id)) return ResponseEntity.notFound().build();
        region.setId(id);
        return ResponseEntity.ok(regionRepo.save(region));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        if(!regionRepo.existsById(id)) return ResponseEntity.notFound().build();
        
        try {
            regionRepo.deleteById(id);
            return ResponseEntity.ok("Región eliminada exitosamente.");
        } catch (DataIntegrityViolationException e) {
            // VALIDACIÓN: Se activa automáticamente si la región tiene destinos vinculados en PostgreSQL
            return ResponseEntity.status(400).body("Error: No puedes eliminar esta región porque tiene destinos turísticos vinculados a ella.");
        }
    }
}