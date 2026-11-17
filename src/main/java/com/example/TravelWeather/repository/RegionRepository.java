package com.example.TravelWeather.repository;

import com.example.TravelWeather.model.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Integer> {
}
