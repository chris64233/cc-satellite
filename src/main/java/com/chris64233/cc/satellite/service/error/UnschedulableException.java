package com.chris64233.cc.satellite.service.error;

/**
 * 无法排程（422）：频段不兼容、窗口时长不足或所有天线均无可行位置。
 * 抛出时事务回滚，不会留下任何天线占用。
 */
public class UnschedulableException extends RuntimeException {

    public UnschedulableException(String message) {
        super(message);
    }
}
