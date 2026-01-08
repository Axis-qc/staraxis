package com.staraxis.universegen.model;

import java.util.List;

public record StarSystem(String name, double starMassKg, List<Planet> planets) {
}
