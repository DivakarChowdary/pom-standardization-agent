package com.divakarchowdary.pom.git;

import com.divakarchowdary.pom.model.PomUpdaterProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class GitHelper {

    private static final String REMOTE_REF_PREFIX = "refs/remotes/origin/";
    private static final String FETCH_REFSPEC = "+refs/heads/:refs/remotes/origin/";

    public CredentialsProvider credentials(PomUpdaterProperties props) {
        return new UsernamePasswordCredentialsProvider(props.getGithubUser(), props.getGithubToken());
    }

    public void fetchAll(Git git, PomUpdaterProperties props) throws GitAPIException {
        log.debug("Fetching all remote refs");
        git.fetch()
                .setCredentialsProvider(credentials(props))
                .setRefSpecs(FETCH_REFSPEC)
                .call();
    }

    public Git cloneOrPull(PomUpdaterProperties props, PomUpdaterProperties.Repo repo,
                           File repoDir) throws GitAPIException, IOException {

        CredentialsProvider cp = credentials(props);
        String repoUrl = props.getGithubBaseUrl() + props.getOwner() + "/" + repo.getName() + ".git";

        if (!repoDir.exists()) {
            log.info("Cloning: {}", repoUrl);
            return Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .setCredentialsProvider(cp)
                    .call();
        }

        Git git = Git.open(repoDir);
        try {
            log.info("Fetching latest for: {}", repo.getName());
            fetchAll(git, props);
            return git;
        } catch (Exception e) {
            git.close();
            throw e;
        }
    }

    public void checkoutBranch(Git git, PomUpdaterProperties props, String branch)
            throws GitAPIException, IOException {

        Ref local = git.getRepository().findRef(branch);
        Ref remote = git.getRepository().findRef(REMOTE_REF_PREFIX + branch);

        if (local != null) {
            git.checkout().setName(branch).call();
            if (remote != null) {
                git.pull().setCredentialsProvider(credentials(props)).call();
            }
            return;
        }

        if (remote != null) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint("origin/" + branch)
                    .call();
            return;
        }

        throw new RefNotFoundException("Branch " + branch + " does not exist locally or remotely");
    }

    public void createAndCheckoutBranch(Git git, String branch, String startPoint)
            throws GitAPIException, IOException {

        Ref existing = git.getRepository().findRef(branch);
        if (existing != null) {
            log.info("Branch {} already exists — recreating from {}", branch, startPoint);
            git.checkout().setName(startPoint).call();
            git.branchDelete().setBranchNames(branch).setForce(true).call();
        }

        git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .setStartPoint(startPoint)
                .call();

        log.info("Created branch: {} from {}", branch, startPoint);
    }

    public void commitAndPush(Git git, PomUpdaterProperties props, String message)
            throws GitAPIException, IOException {

        git.add().addFilepattern("pom.xml").call();

        Status status = git.status().call();
        if (status.isClean()) {
            log.info("No changes to commit in {}", git.getRepository().getDirectory());
            return;
        }

        git.commit().setMessage(message).call();

        String currentBranch = git.getRepository().getBranch();
        git.push()
                .setCredentialsProvider(credentials(props))
                .setRefSpecs(new RefSpec(currentBranch + ":" + currentBranch))
                .setForce(true)
                .call();

        log.info("Committed and pushed: {}", message);
    }
}
