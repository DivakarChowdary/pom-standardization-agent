package com.divakarchowdary.pom.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class PomMappingConfig {
    private Map<String, String> dependencyPropertyMap = new HashMap<>();
    private Map<String, String> pluginPropertyMap = new HashMap<>();
}
