package io.betahub.minecraft.core.api;

/** Talks to BetaHub. Methods block; never call them on the server's main thread. */
public interface IssueSubmitter {

    IssueResult createIssue(NewIssue issue);

    TokenCheck verifyToken();
}
