/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.engine.control

import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.engine.atom.AtomResponse
import com.tencent.devops.process.engine.atom.AtomUtils
import com.tencent.devops.process.engine.atom.TaskAtomService
import com.tencent.devops.process.engine.common.BS_ATOM_STATUS_REFRESH_DELAY_MILLS
import com.tencent.devops.process.engine.common.BS_TASK_HOST
import com.tencent.devops.process.engine.control.lock.TaskIdLock
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.engine.pojo.event.PipelineBuildAtomTaskEvent
import com.tencent.devops.process.engine.service.PipelineRuntimeService
import com.tencent.devops.process.pojo.mq.PipelineBuildContainerEvent
import com.tencent.devops.process.service.PipelineTaskService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 任务（最小单元Atom）控制器
 * @version 1.0
 */
@Service
class TaskControl @Autowired constructor(
    private val redisOperation: RedisOperation,
    private val taskAtomService: TaskAtomService,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val pipelineRuntimeService: PipelineRuntimeService,
    private val pipelineTaskService: PipelineTaskService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(event: PipelineBuildAtomTaskEvent): Boolean {
        with(event) {
            val taskIdLock = TaskIdLock(redisOperation, buildId, taskId)
            try {
                taskIdLock.lock()
                execute()
            } finally {
                taskIdLock.unlock()
            }
        }
        return true
    }

    private fun PipelineBuildAtomTaskEvent.execute() {

        val buildInfo = pipelineRuntimeService.getBuildInfo(buildId)

        val buildTask = pipelineRuntimeService.getBuildTask(buildId, taskId)
        // 检查构建状态,防止重复跑
        if (buildInfo == null || buildInfo.status.isFinish() || buildTask == null || buildTask.status.isFinish()) {
            logger.warn("[$buildId]|ATOM_$actionType|taskId=$taskId| status=${buildTask?.status ?: "not exists"}")
            return
        }

        // 构建机的任务不在此运行
        if (taskAtomService.runByVmTask(buildTask)) {
            logger.info("[$buildId]|ATOM|stageId=$stageId|container=$containerId|taskId=$taskId|vm atom will claim by agent")
            return
        }

        buildTask.starter = userId

        if (taskParam.isNotEmpty()) { // 追加事件传递的参数变量值
            buildTask.taskParams.putAll(taskParam)
        }

        logger.info("[$buildId]|[${buildInfo.status}]|ATOM_$actionType|taskId=$taskId|status=${buildTask.status}")
        val buildStatus = when {
            buildTask.status.isReadyToRun() -> { // 准备启动执行
                if (actionType.needRun()) {
                    atomBuildStatus(taskAtomService.start(buildTask))
                } else {// #2400 因任务终止&结束的事件命令而未执行的原子设置为UNEXEC，而不是SKIP
                    pipelineRuntimeService.updateTaskStatus(buildId, taskId, userId, BuildStatus.UNEXEC)
                    BuildStatus.UNEXEC // SKIP 仅当是用户意愿明确正常运行情况要跳过执行的，不影响主流程的才能是SKIP
                }
            }
            buildTask.status.isRunning() -> { // 运行中的，检查是否运行结束，以及决定是否强制终止
                atomBuildStatus(taskAtomService.tryFinish(buildTask, ActionType.isTerminate(actionType)))
            }
            else -> buildTask.status // 其他状态不做动作
        }

        if (buildStatus.isRunning()) { // 仍然在运行中--没有结束的
            loopRunning(buildTask = buildTask, buildStatus = buildStatus)
        } else if (buildStatus.isFinish()) {
            finishTask(buildTask, buildStatus)
        }
    }

    private fun PipelineBuildAtomTaskEvent.finishTask(buildTask: PipelineBuildTask, buildStatus: BuildStatus) {
        if (buildStatus.isFailure()) {
            // #2375 对强制终止/结束动作的插件执行事件，如果插件执行结果是失败的，则忽略掉当前插件的“失败继续”、“失败重试”的设置
            when {
                ActionType.isEnd(actionType) -> {
                    // 记录失败原子
                    pipelineTaskService.createFailElementVar(buildId = buildId, projectId = projectId, pipelineId = pipelineId, taskId = taskId)
                }
                pipelineTaskService.isRetryWhenFail(taskId, buildId) -> { // 如果配置了失败重试，且重试次数上线未达上限，则进行重试
                    logger.info("retry task [$buildId]|ATOM|stageId=$stageId|container=$containerId|taskId=$taskId |vm atom will retry, even the task is failure")
                    pipelineRuntimeService.updateTaskStatus(buildId, taskId, userId, BuildStatus.RETRY)
                    delayMills = AtomUtils.defaultAtomStatusRefreshIntervalMills
                    actionType = ActionType.RETRY
                }
                ControlUtils.continueWhenFailure(buildTask.additionalOptions) -> { // 如果配置了失败继续，则继续下去
                    logger.info("[$buildId]|ATOM|stageId=$stageId|container=$containerId|taskId=$taskId|vm atom will continue, even the task is failure")
                    // 记录失败原子
                    pipelineTaskService.createFailElementVar(buildId = buildId, projectId = projectId, pipelineId = pipelineId, taskId = taskId)
                    actionType = ActionType.START
                }
                else -> { // 如果当前动作不是结束动作并且当前状态失败了就要结束当前容器构建
                    // 记录失败原子
                    pipelineTaskService.createFailElementVar(buildId = buildId, projectId = projectId, pipelineId = pipelineId, taskId = taskId)
                    actionType = ActionType.END
                }
            }
        } else {
            // 清除该原子内的重试记录
            pipelineTaskService.removeRetryCache(buildId = buildId, taskId = taskId)
            // 清理插件错误信息（重试插件成功的情况下）
            pipelineTaskService.removeFailVarWhenSuccess(buildId = buildId, projectId = projectId, pipelineId = pipelineId, taskId = taskId)
        }
        pipelineEventDispatcher.dispatch(
            PipelineBuildContainerEvent(
                source = "taskControl_$buildStatus",
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId,
                buildId = buildId,
                stageId = stageId,
                containerId = containerId,
                containerType = containerType,
                actionType = actionType,
                delayMills = delayMills
            )
        )
    }

    private fun PipelineBuildAtomTaskEvent.loopRunning(buildTask: PipelineBuildTask, buildStatus: BuildStatus) {
        // 如果是要轮循的才需要定时消息轮循
        delayMills =
            if (buildTask.taskParams[BS_ATOM_STATUS_REFRESH_DELAY_MILLS] != null) {
                buildTask.taskParams[BS_ATOM_STATUS_REFRESH_DELAY_MILLS].toString().trim().toInt()
            } else {
                AtomUtils.defaultAtomStatusRefreshIntervalMills
            }
        // 将执行结果参数写回事件消息中，方便再次传递
        taskParam.putAll(buildTask.taskParams)
        actionType = ActionType.REFRESH // 尝试刷新任务状态
        // 特定消费者
        if (buildTask.taskParams[BS_TASK_HOST] != null) {
            routeKeySuffix = buildTask.taskParams[BS_TASK_HOST].toString()
        }

        pipelineEventDispatcher.dispatch(this.copy(source = "taskControl_$buildStatus"))
    }

    private fun atomBuildStatus(response: AtomResponse): BuildStatus {
        return response.buildStatus
    }
}
