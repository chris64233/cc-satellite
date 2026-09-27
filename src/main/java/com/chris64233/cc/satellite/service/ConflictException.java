package com.chris64233.cc.satellite.service;

/** 请求与现有状态冲突（如幂等键被不同内容复用、任务已开始无法取消）。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
