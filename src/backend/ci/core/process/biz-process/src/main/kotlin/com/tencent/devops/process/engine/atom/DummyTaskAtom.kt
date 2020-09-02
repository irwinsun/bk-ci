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

package com.tencent.devops.process.engine.atom

import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.process.engine.common.BS_ATOM_LOOP_TIMES
import com.tencent.devops.process.engine.common.BS_ATOM_STATUS_REFRESH_DELAY_MILLS
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import org.apache.commons.lang3.math.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 本原子辅助构建机/无编译环境上运行的插件，在服务器端作为一个辅助中断/超时/离线/的插件状态切换控制原子，防止出现构建环境与服务器状态失联过久
 */
@Component
class DummyTaskAtom(
    private val buildLogPrinter: BuildLogPrinter
) : IAtomTask<MutableMap<String, Any>> {

    override fun getParamElement(task: PipelineBuildTask): MutableMap<String, Any> {
        return task.taskParams
    }

    override fun execute(
        task: PipelineBuildTask,
        param: MutableMap<String, Any>,
        runVariables: Map<String, String>
    ): AtomResponse {

        val taskParams = task.taskParams
        val loopTimeStr = taskParams[BS_ATOM_LOOP_TIMES]?.toString()

        if (loopTimeStr == null) {
            logger.info("[${task.buildId}]|ATOM_LOOP_FIRST|${task.containerType}|${task.taskId}|${task.taskName}|${task.status}|${task.taskParams}")
        } else {
            logger.info("[${task.buildId}]|ATOM_LOOP|${task.containerType}|${task.taskId}|${task.taskName}|${task.status}|$loopTimeStr")
        }

        // 检查构建机/环境在线状态
        if (!VMUtils.isVMOnlineStatus(runVariables[VMUtils.genVMStatusFlag(task.containerId)])) {
            logger.info("[${task.buildId}]|ATOM_LOOP_STOP|${task.containerType}|${task.taskId}|${task.taskName}| agent offline")
            buildLogPrinter.addLine(
                buildId = task.buildId,
                message = "Job(#${task.containerId}) End",
                tag = task.taskId,
                jobId = task.containerHashId,
                executeCount = task.executeCount ?: 1
            )
            return defaultSuccessAtomResponse
        }

        if (!task.status.isFinish()) {
            taskParams[BS_ATOM_STATUS_REFRESH_DELAY_MILLS] = TimeUnit.SECONDS.toMillis(10) // 10秒轮循状态
            taskParams[BS_ATOM_LOOP_TIMES] = if (NumberUtils.isParsable(loopTimeStr)) 1 + loopTimeStr!!.toInt() else 1
        }

        return AtomResponse(task.status)
    }

    override fun tryFinish(
        task: PipelineBuildTask,
        param: MutableMap<String, Any>,
        runVariables: Map<String, String>,
        force: Boolean
    ): AtomResponse {
        return if (force) {
            logger.info("[${task.buildId}]|ATOM_LOOP_TERMINATE|${task.containerType}|${task.taskId}|${task.taskName}|${task.status}")
            buildLogPrinter.addYellowLine(
                buildId = task.buildId,
                message = "Terminate/终止运行",
                tag = task.taskId,
                jobId = task.containerHashId,
                executeCount = task.executeCount ?: 1
            )
            if (BuildStatus.isFinish(task.status)) {
                AtomResponse(
                    buildStatus = task.status,
                    errorType = task.errorType,
                    errorCode = task.errorCode,
                    errorMsg = task.errorMsg
                )
            } else { // 强制终止的设置为失败
                defaultFailAtomResponse
            }
        } else execute(task, param, runVariables)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DummyTaskAtom::class.java)
    }
}
