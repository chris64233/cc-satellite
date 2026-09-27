package com.chris64233.cc.satellite.service.error;

/**
 * 与当前状态冲突（409）：幂等键内容不一致、任务已开始无法取消等。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
