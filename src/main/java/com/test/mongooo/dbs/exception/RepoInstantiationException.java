package com.bank.dbs.exception;

/** 500 REPO_ERROR — cannot instantiate storage backend implementation. */
public class RepoInstantiationException extends RuntimeException {
    public RepoInstantiationException(String repoId, Throwable cause) {
        super("Could not instantiate storage repo implementation for repoId=" + repoId, cause);
    }
}
