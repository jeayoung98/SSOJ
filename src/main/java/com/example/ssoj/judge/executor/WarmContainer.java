package com.example.ssoj.judge.executor;

public class WarmContainer {

    public enum Status {
        IDLE,
        BUSY,
        BROKEN,
        STOPPED
    }

    private final String containerId;
    private final String dockerImage;
    private final int dockerMemoryMb;
    private int useCount;
    private Status status;

    WarmContainer(String containerId, String dockerImage, int dockerMemoryMb) {
        this.containerId = containerId;
        this.dockerImage = dockerImage;
        this.dockerMemoryMb = dockerMemoryMb;
        this.status = Status.IDLE;
    }

    public String containerId() {
        return containerId;
    }

    public String dockerImage() {
        return dockerImage;
    }

    public int dockerMemoryMb() {
        return dockerMemoryMb;
    }

    public synchronized int useCount() {
        return useCount;
    }

    synchronized int incrementUseCount() {
        useCount++;
        return useCount;
    }

    public synchronized Status status() {
        return status;
    }

    synchronized void markIdle() {
        status = Status.IDLE;
    }

    synchronized void markBusy() {
        status = Status.BUSY;
    }

    synchronized void markBroken() {
        status = Status.BROKEN;
    }

    synchronized void markStopped() {
        status = Status.STOPPED;
    }
}
