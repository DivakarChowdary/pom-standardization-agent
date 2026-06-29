package com.divakarchowdary.pom.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class RepoResult {

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED,
        NO_CHANGES
    }

    private final PomUpdaterProperties.Repo repo;
    private Status status = Status.SKIPPED;
    private String prUrl;
    private String errorMessage;

    public static RepoResult success(PomUpdaterProperties.Repo repo, String prUrl) {
        RepoResult r = new RepoResult(repo);
        r.status = Status.SUCCESS;
        r.prUrl = prUrl;
        return r;
    }

    public static RepoResult failed(PomUpdaterProperties.Repo repo, String errorMessage) {
        RepoResult r = new RepoResult(repo);
        r.status = Status.FAILED;
        r.errorMessage = errorMessage;
        return r;
    }

    public static RepoResult noChanges(PomUpdaterProperties.Repo repo) {
        RepoResult r = new RepoResult(repo);
        r.status = Status.NO_CHANGES;
        return r;
    }
}
