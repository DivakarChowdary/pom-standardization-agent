package com.divakarchowdary.pom.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class RepoFileConfig {
    private String owner;
    private List<String> repos = new ArrayList<>();
    private Map<String, String> versions = new HashMap<>();
}
