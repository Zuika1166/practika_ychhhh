package ru.practice.helpdesk;

public enum Status {
  NEW,
  IN_PROGRESS,
  RESOLVED,
  CANCELLED;

  public boolean isOpen() {
    return this == NEW || this == IN_PROGRESS;
  }

  public boolean allows(Status target) {
    return (this == NEW && (target == IN_PROGRESS || target == CANCELLED))
        || (this == IN_PROGRESS && (target == RESOLVED || target == CANCELLED));
  }
}
