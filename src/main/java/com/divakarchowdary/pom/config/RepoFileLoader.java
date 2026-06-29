package com.divakarchowdary.pom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.divakarchowdary.pom.exception.RepoFileLoadException;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.model.RepoFileConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoFileLoader {

    private final PomUpdaterProperties props;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper;

    public void load() {
        String path = "classpath:" + props.getReposFile();
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalArgumentException("Repo config file not found: " + path);
            }

            RepoFileConfig config = mapper.readValue(resource.getInputStream(), RepoFileConfig.class);

            List<PomUpdaterProperties.Repo> repos = config.getRepos().stream()
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .map(PomUpdaterProperties.Repo::new)
                    .toList();

            props.setOwner(config.getOwner());
            props.setRepos(repos);
            props.setVersions(config.getVersions());

            log.info("Loaded repo config from: {}", path);
            log.info("  Owner  : {}", props.getOwner());
            log.info("  Repos  : {}", repos.size());
            log.info("  Versions: {}", props.getVersions().size());

        } catch (Exception e) {
            throw new RepoFileLoadException("Failed to load repo config: " + path, e);
        }
    }
}
