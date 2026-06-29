package com.divakarchowdary.pom.service;

import com.divakarchowdary.pom.config.PomMappingLoader;
import com.divakarchowdary.pom.git.GitHelper;
import com.divakarchowdary.pom.git.GithubPRClient;
import com.divakarchowdary.pom.maven.MavenRunner;
import com.divakarchowdary.pom.maven.PomWalker;
import com.divakarchowdary.pom.model.PRInfo;
import com.divakarchowdary.pom.model.PomMappingConfig;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.model.RepoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoProcessor {

    private final GithubPRClient prClient;
    private final PomMappingLoader mappingLoader;
    private final GitHelper gitHelper;
    private final MavenRunner mavenRunner;

    public RepoResult process(PomUpdaterProperties props, PomUpdaterProperties.Repo repo) {
        File repoDir = new File(props.getWorkDir(), repo.getName());
        cleanRepoDir(repoDir);

        log.info("------------------------------------------------------------");
        log.info("Processing: {}", repo.getName());
        log.info("------------------------------------------------------------");

        try (Git git = gitHelper.cloneOrPull(props, repo, repoDir)) {

            String baseBranch = props.getBaseBranch();
            String featureBranch = props.getFeatureBranch();

            gitHelper.checkoutBranch(git, props, baseBranch);
            gitHelper.createAndCheckoutBranch(git, featureBranch, baseBranch);

            File pomFile = new File(repoDir, "pom.xml");
            Map<String, String> versionMap = buildVersionMap(props);
            PomMappingConfig mapping = mappingLoader.getMapping();

            PomWalker.UpdateResult update = PomWalker.updateAllProperties(
                    pomFile,
                    versionMap,
                    mapping.getDependencyPropertyMap(),
                    mapping.getPluginPropertyMap()
            );

            if (!update.isChanged()) {
                log.info("{}: no POM changes needed", repo.getName());
                return RepoResult.noChanges(repo);
            }

            // Run Maven build to verify changes compile
            boolean buildOk = mavenRunner.run(repoDir, props.getMavenGoals(), props.getMavenHome());
            if (!buildOk) {
                return RepoResult.failed(repo, "Maven build failed after POM update");
            }

            gitHelper.commitAndPush(git, props, buildCommitMessage(props));

            PRInfo existing = prClient.findExistingPR(
                    props.getGithubApiBaseUrl(), props.getOwner(),
                    repo.getName(), featureBranch, props.getGithubToken());

            if (existing != null) {
                log.info("{}: PR already exists — {}", repo.getName(), existing.getPrUrl());
                return RepoResult.success(repo, existing.getPrUrl());
            }

            PRInfo pr = prClient.createPullRequest(
                    props.getGithubApiBaseUrl(), props.getOwner(), repo.getName(),
                    featureBranch, baseBranch,
                    buildPrTitle(props),
                    buildPrBody(props, update),
                    props.getGithubToken()
            );

            List<String> reviewers = props.getReviewerUsernames().stream()
                    .filter(r -> !r.equalsIgnoreCase(pr.getAuthor()))
                    .toList();

            if (!reviewers.isEmpty()) {
                prClient.assignReviewers(
                        props.getGithubApiBaseUrl(), props.getOwner(),
                        repo.getName(), pr.getPrNumber(), reviewers, props.getGithubToken());
            }

            log.info("{}: PR created — {}", repo.getName(), pr.getPrUrl());
            return RepoResult.success(repo, pr.getPrUrl());

        } catch (Exception e) {
            log.error("Failed to process {}: {}", repo.getName(), e.getMessage(), e);
            return RepoResult.failed(repo, e.getMessage());
        }
    }

    private Map<String, String> buildVersionMap(PomUpdaterProperties props) {
        Map<String, String> versionMap = new LinkedHashMap<>(props.getVersions());
        if (versionMap.containsKey("spring.boot.version")) {
            versionMap.put("parent", versionMap.remove("spring.boot.version"));
        }
        return versionMap;
    }

    private String buildCommitMessage(PomUpdaterProperties props) {
        return String.format("[%s] Standardize POM versions — automated update", props.getProfile().toUpperCase());
    }

    private String buildPrTitle(PomUpdaterProperties props) {
        return String.format("[%s] Standardize POM versions", props.getProfile().toUpperCase());
    }

    private String buildPrBody(PomUpdaterProperties props, PomWalker.UpdateResult update) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Automated POM Standardization\n\n");
        sb.append("Profile: ").append(props.getProfile().toUpperCase()).append("\n\n");
        sb.append("Version Changes\n\n");
        sb.append("| Property | Old | New |\n");
        sb.append("|----------|-----|-----|\n");
        update.getUpdated().forEach((key, change) ->
                sb.append("| `").append(key).append("` | `")
                  .append(change.getOldVersion()).append("` | `")
                  .append(change.getNewVersion()).append("` |\n")
        );
        sb.append("\n_Generated by POM Standardization Agent_");
        return sb.toString();
    }

    private void cleanRepoDir(File repoDir) {
        if (repoDir.exists()) {
            FileUtils.deleteQuietly(repoDir);
            log.debug("Cleared repo directory: {}", repoDir.getName());
        }
    }
}
