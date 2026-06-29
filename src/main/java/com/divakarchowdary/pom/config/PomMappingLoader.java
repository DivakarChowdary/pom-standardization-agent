package com.divakarchowdary.pom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.divakarchowdary.pom.exception.PomMappingLoadException;
import com.divakarchowdary.pom.model.PomMappingConfig;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PomMappingLoader {

    private final PomUpdaterProperties props;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper;

    @Getter
    private PomMappingConfig mapping;

    public void load() {
        String path = "classpath:" + props.getMappingFile();
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalArgumentException("Mapping file not found: " + path);
            }
            mapping = mapper.readValue(resource.getInputStream(), PomMappingConfig.class);
            log.info("Loaded POM mapping file: {}", props.getMappingFile());
            log.info("  Dependencies mapped: {}", mapping.getDependencyPropertyMap().size());
            log.info("  Plugins mapped     : {}", mapping.getPluginPropertyMap().size());
        } catch (Exception e) {
            throw new PomMappingLoadException("Failed to load mapping file: " + path, e);
        }
    }
}
