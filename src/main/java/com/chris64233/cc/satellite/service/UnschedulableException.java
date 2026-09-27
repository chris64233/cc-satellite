package com.chris64233.cc.satellite.service;

/** 窗口内找不到满足全部约束的可行位置。 */
public class UnschedulableException extends RuntimeException {
    public UnschedulableException(String message) {
        super(message);
    }
}
