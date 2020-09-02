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

package com.tencent.devops.process.engine.common

import com.tencent.devops.common.pipeline.NameAndValue
import com.tencent.devops.common.pipeline.pojo.element.ElementAdditionalOptions
import com.tencent.devops.common.pipeline.pojo.element.RunCondition

/**
 *
 * @version 1.0
 */
object VMUtils {

    enum class VMStatus {
        INIT,
        START_FAILED,
        ONLINE,
        HEART_BEAT_TIME_OUT,
        OFFLINE
    }

    fun genStageId(seq: Int) = "stage-$seq"

    fun genStopVMTaskId(seq: Int) = "stopVM-$seq"

    fun genEndPointTaskId(seq: Int) = "end-$seq"

    fun genVMSeq(containerSeq: Int, taskSeq: Int): Int = containerSeq * 1000 + taskSeq

    fun genStartVMTaskId(containerSeq: String) = "startVM-$containerSeq"

    fun genVMTaskOptions(runCondition: RunCondition, customVariables: List<NameAndValue> = emptyList()): ElementAdditionalOptions = ElementAdditionalOptions(
        enable = true,
        continueWhenFailed = false,
        retryWhenFailed = true,
        retryCount = 1, // retry 1 time
        timeout = 10, // minute
        runCondition = runCondition,
        customVariables = customVariables,
        otherTask = null,
        customCondition = null
    )

    fun genVMStatusFlag(vmSeqId: String) = "vm_${vmSeqId}_status"

    fun isVMOnlineStatus(onlineStatusFlag: String?) = onlineStatusFlag == VMStatus.ONLINE.name
}
