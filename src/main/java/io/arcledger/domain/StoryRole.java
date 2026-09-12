package io.arcledger.domain;

public enum StoryRole {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canEdit() { return this == OWNER || this == EDITOR; }
    public boolean canManage() { return this == OWNER; }
}
