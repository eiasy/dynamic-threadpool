/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.core.executor.web;

import cn.hippo4j.common.model.ThreadPoolBaseInfo;
import cn.hippo4j.common.model.ThreadPoolParameter;
import cn.hippo4j.common.model.ThreadPoolParameterInfo;
import cn.hippo4j.common.model.ThreadPoolRunStateInfo;
import cn.hippo4j.core.executor.state.AbstractThreadPoolRuntime;
import cn.hippo4j.core.toolkit.CalculateUtil;
import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;

import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.hippo4j.common.constant.ChangeThreadPoolConstants.CHANGE_DELIMITER;

/**
 * Tomcat web thread pool handler.
 *
 * @author chen.ma
 * @date 2022/1/19 20:57
 */
@Slf4j
@RequiredArgsConstructor
public class TomcatWebThreadPoolHandler extends AbstractWebThreadPoolService {

    private final AtomicBoolean cacheFlag = new AtomicBoolean(Boolean.FALSE);

    private static String EXCEPTION_MESSAGE;

    private final AbstractThreadPoolRuntime webThreadPoolRunStateHandler;

    @Override
    protected Executor getWebThreadPoolByServer(WebServer webServer) {
        if (cacheFlag.get()) {
            log.warn("Exception getting Tomcat thread pool. Exception message :: {}", EXCEPTION_MESSAGE);
            return null;
        }
        Executor tomcatExecutor = null;
        try {
            tomcatExecutor = ((TomcatWebServer) webServer).getTomcat().getConnector().getProtocolHandler().getExecutor();
        } catch (Exception ex) {
            cacheFlag.set(Boolean.TRUE);
            EXCEPTION_MESSAGE = ex.getMessage();
            log.error("Failed to get Tomcat thread pool. Message :: {}", EXCEPTION_MESSAGE);
        }
        return tomcatExecutor;
    }

    @Override
    public ThreadPoolBaseInfo simpleInfo() {
        ThreadPoolBaseInfo poolBaseInfo = new ThreadPoolBaseInfo();
        int corePoolSize, maximumPoolSize, queueCapacity;
        long keepAliveTime;
        String rejectedExecutionHandlerName;
        BlockingQueue<Runnable> blockingQueue;
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
            corePoolSize = threadPoolExecutor.getCorePoolSize();
            maximumPoolSize = threadPoolExecutor.getMaximumPoolSize();
            keepAliveTime = threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
            blockingQueue = threadPoolExecutor.getQueue();
            int queueSize = blockingQueue.size();
            int remainingCapacity = blockingQueue.remainingCapacity();
            queueCapacity = queueSize + remainingCapacity;
            RejectedExecutionHandler rejectedExecutionHandler = threadPoolExecutor.getRejectedExecutionHandler();
            rejectedExecutionHandlerName = rejectedExecutionHandler.getClass().getSimpleName();
        } else {
            org.apache.tomcat.util.threads.ThreadPoolExecutor tomcatThreadPoolExecutor = (org.apache.tomcat.util.threads.ThreadPoolExecutor) executor;
            corePoolSize = tomcatThreadPoolExecutor.getCorePoolSize();
            maximumPoolSize = tomcatThreadPoolExecutor.getMaximumPoolSize();
            keepAliveTime = tomcatThreadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
            blockingQueue = tomcatThreadPoolExecutor.getQueue();
            int queueSize = blockingQueue.size();
            int remainingCapacity = blockingQueue.remainingCapacity();
            queueCapacity = queueSize + remainingCapacity;
            rejectedExecutionHandlerName = tomcatThreadPoolExecutor.getRejectedExecutionHandler().getClass().getSimpleName();
        }
        poolBaseInfo.setCoreSize(corePoolSize);
        poolBaseInfo.setMaximumSize(maximumPoolSize);
        poolBaseInfo.setKeepAliveTime(keepAliveTime);
        poolBaseInfo.setQueueType(blockingQueue.getClass().getSimpleName());
        poolBaseInfo.setQueueCapacity(queueCapacity);
        poolBaseInfo.setRejectedName(rejectedExecutionHandlerName);
        return poolBaseInfo;
    }

    @Override
    public ThreadPoolParameter getWebThreadPoolParameter() {
        ThreadPoolParameterInfo parameterInfo = new ThreadPoolParameterInfo();
        int minThreads, maxThreads;
        long keepAliveTime;
        try {
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tomcatExecutor = (ThreadPoolExecutor) executor;
                minThreads = tomcatExecutor.getCorePoolSize();
                maxThreads = tomcatExecutor.getMaximumPoolSize();
                keepAliveTime = tomcatExecutor.getKeepAliveTime(TimeUnit.SECONDS);
            } else {
                org.apache.tomcat.util.threads.ThreadPoolExecutor tomcatThreadPoolExecutor = (org.apache.tomcat.util.threads.ThreadPoolExecutor) executor;
                minThreads = tomcatThreadPoolExecutor.getCorePoolSize();
                maxThreads = tomcatThreadPoolExecutor.getMaximumPoolSize();
                keepAliveTime = tomcatThreadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
            }
            parameterInfo.setCoreSize(minThreads);
            parameterInfo.setMaxSize(maxThreads);
            parameterInfo.setKeepAliveTime((int) keepAliveTime);
        } catch (Exception ex) {
            log.error("Failed to get the tomcat thread pool parameter.", ex);
        }
        return parameterInfo;
    }

    @Override
    public ThreadPoolRunStateInfo getWebRunStateInfo() {
        if (executor instanceof ThreadPoolExecutor) {
            return webThreadPoolRunStateHandler.getPoolRunState(null, executor);
        }
        ThreadPoolRunStateInfo runStateInfo = new ThreadPoolRunStateInfo();
        org.apache.tomcat.util.threads.ThreadPoolExecutor tomcatThreadPoolExecutor = (org.apache.tomcat.util.threads.ThreadPoolExecutor) executor;
        // 核心线程数
        int corePoolSize = tomcatThreadPoolExecutor.getCorePoolSize();
        // 最大线程数
        int maximumPoolSize = tomcatThreadPoolExecutor.getMaximumPoolSize();
        // 线程池当前线程数 (有锁)
        int poolSize = tomcatThreadPoolExecutor.getPoolSize();
        // 活跃线程数 (有锁)
        int activeCount = tomcatThreadPoolExecutor.getActiveCount();
        // 同时进入池中的最大线程数 (有锁)
        int largestPoolSize = tomcatThreadPoolExecutor.getLargestPoolSize();
        // 线程池中执行任务总数量 (有锁)
        long completedTaskCount = tomcatThreadPoolExecutor.getCompletedTaskCount();
        // 当前负载
        String currentLoad = CalculateUtil.divide(activeCount, maximumPoolSize) + "";
        // 峰值负载
        String peakLoad = CalculateUtil.divide(largestPoolSize, maximumPoolSize) + "";
        BlockingQueue<Runnable> queue = tomcatThreadPoolExecutor.getQueue();
        // 队列元素个数
        int queueSize = queue.size();
        // 队列类型
        String queueType = queue.getClass().getSimpleName();
        // 队列剩余容量
        int remainingCapacity = queue.remainingCapacity();
        // 队列容量
        int queueCapacity = queueSize + remainingCapacity;
        runStateInfo.setCoreSize(corePoolSize);
        runStateInfo.setPoolSize(poolSize);
        runStateInfo.setMaximumSize(maximumPoolSize);
        runStateInfo.setActiveSize(activeCount);
        runStateInfo.setCurrentLoad(currentLoad);
        runStateInfo.setPeakLoad(peakLoad);
        runStateInfo.setQueueType(queueType);
        runStateInfo.setQueueSize(queueSize);
        runStateInfo.setQueueCapacity(queueCapacity);
        runStateInfo.setQueueRemainingCapacity(remainingCapacity);
        runStateInfo.setLargestPoolSize(largestPoolSize);
        runStateInfo.setCompletedTaskCount(completedTaskCount);
        runStateInfo.setClientLastRefreshTime(DateUtil.formatDateTime(new Date()));
        runStateInfo.setTimestamp(System.currentTimeMillis());
        return webThreadPoolRunStateHandler.supplement(runStateInfo);
    }

    @Override
    public void updateWebThreadPool(ThreadPoolParameterInfo threadPoolParameterInfo) {
        int originalCoreSize, originalMaximumPoolSize;
        long originalKeepAliveTime;
        try {
            if (executor instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                originalCoreSize = threadPoolExecutor.getCorePoolSize();
                originalMaximumPoolSize = threadPoolExecutor.getMaximumPoolSize();
                originalKeepAliveTime = threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
                threadPoolExecutor.setCorePoolSize(threadPoolParameterInfo.corePoolSizeAdapt());
                threadPoolExecutor.setMaximumPoolSize(threadPoolParameterInfo.maximumPoolSizeAdapt());
                threadPoolExecutor.setKeepAliveTime(threadPoolParameterInfo.getKeepAliveTime(), TimeUnit.SECONDS);
            } else {
                org.apache.tomcat.util.threads.ThreadPoolExecutor tomcatThreadPoolExecutor = (org.apache.tomcat.util.threads.ThreadPoolExecutor) executor;
                originalCoreSize = tomcatThreadPoolExecutor.getCorePoolSize();
                originalMaximumPoolSize = tomcatThreadPoolExecutor.getMaximumPoolSize();
                originalKeepAliveTime = tomcatThreadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
                tomcatThreadPoolExecutor.setCorePoolSize(threadPoolParameterInfo.corePoolSizeAdapt());
                tomcatThreadPoolExecutor.setMaximumPoolSize(threadPoolParameterInfo.maximumPoolSizeAdapt());
                tomcatThreadPoolExecutor.setKeepAliveTime(threadPoolParameterInfo.getKeepAliveTime(), TimeUnit.SECONDS);
            }
            log.info("[TOMCAT] Changed web thread pool. corePoolSize :: [{}], maximumPoolSize :: [{}], keepAliveTime :: [{}]",
                    String.format(CHANGE_DELIMITER, originalCoreSize, threadPoolParameterInfo.corePoolSizeAdapt()),
                    String.format(CHANGE_DELIMITER, originalMaximumPoolSize, threadPoolParameterInfo.maximumPoolSizeAdapt()),
                    String.format(CHANGE_DELIMITER, originalKeepAliveTime, threadPoolParameterInfo.getKeepAliveTime()));
        } catch (Exception ex) {
            log.error("Failed to modify the Tomcat thread pool parameter.", ex);
        }
    }
}
