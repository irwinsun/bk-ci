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

import com.tencent.devops.common.api.util.EnvUtils
import com.tencent.devops.common.pipeline.NameAndValue
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.JobRunCondition
import com.tencent.devops.common.pipeline.enums.StageRunCondition
import com.tencent.devops.common.pipeline.pojo.element.ElementAdditionalOptions
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MAX_COUNT
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MIN_COUNT
import org.slf4j.LoggerFactory

/**
 *
 * @version 1.0
 */
object ControlUtils {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun skipPreTaskNotFail(additionalOptions: ElementAdditionalOptions?, containerFinalStatus: BuildStatus): Boolean {
        if (additionalOptions == null) {
            return false
        }
        return additionalOptions.runCondition == RunCondition.PRE_TASK_FAILED_ONLY && !BuildStatus.isFailure(
            containerFinalStatus
        )
    }

    fun isEnable(additionalOptions: ElementAdditionalOptions?): Boolean {
        if (additionalOptions == null) {
            return true
        }
        return additionalOptions.enable
    }

    fun continueWhenFailure(additionalOptions: ElementAdditionalOptions?): Boolean {
        if (additionalOptions == null) {
            return false
        }
        return additionalOptions.continueWhenFailed
    }

    fun retryWhenFailure(additionalOptions: ElementAdditionalOptions?, retryCount: Int): Boolean {
        if (additionalOptions == null || !isEnable(additionalOptions)) {
            return false
        }
        val retryWhenFailed = additionalOptions.retryWhenFailed

        return if (retryWhenFailed) {
            var settingRetryCount = additionalOptions.retryCount
            if (settingRetryCount > TASK_FAIL_RETRY_MAX_COUNT) {
                settingRetryCount = TASK_FAIL_RETRY_MAX_COUNT
            }
            if (settingRetryCount < TASK_FAIL_RETRY_MIN_COUNT) {
                settingRetryCount = TASK_FAIL_RETRY_MIN_COUNT
            }
            retryCount < settingRetryCount
        } else {
            false
        }
    }

    fun checkAdditionalSkip(
        buildId: String,
        additionalOptions: ElementAdditionalOptions?,
        currentStatus: BuildStatus,
        variables: Map<String, String>,
        continueWhenFailure: Boolean
    ): Boolean {
        // #2375 检查控制流程的执行条件
        if (skipByRunCondition(additionalOptions?.runCondition, currentStatus, continueWhenFailure)) {
            logger.info("[$buildId]|skipCheckRunCondition|condition=${additionalOptions?.runCondition}|continueWhenFailure=$continueWhenFailure|status=$currentStatus|will skip")
            return true
        }
        if (!isEnable(additionalOptions)) {
            return true
        }

        return checkCustomVariableSkip(buildId = buildId, additionalOptions = additionalOptions, variables = variables)
    }

    fun checkCustomVariableSkip(buildId: String, additionalOptions: ElementAdditionalOptions?, variables: Map<String, String>): Boolean {
        // 自定义变量全部满足时不运行
        if (skipWhenCustomVarMatch(additionalOptions)) {
            for (names in additionalOptions!!.customVariables!!) {
                val key = names.key
                val value = EnvUtils.parseEnv(names.value, variables)
                val existValue = variables[key]
                if (value != existValue) {
                    logger.info("buildId=[$buildId]|CUSTOM_VARIABLE_MATCH_NOT_RUN|key=$key|exists=$existValue|expect=$value|origin=${names.value}")
                    return false
                }
            }
            // 所有自定义条件都满足，则跳过
            return true
        }

        // 自定义变量全部满足时运行
        if (notSkipWhenCustomVarMatch(additionalOptions)) {
            for (names in additionalOptions!!.customVariables!!) {
                val key = names.key
                val value = EnvUtils.parseEnv(names.value, variables)
                val existValue = variables[key]
                if (value != existValue) {
                    logger.info("buildId=[$buildId]|CUSTOM_VARIABLE_MATCH|key=$key|exists=$existValue|expect=$value|origin=${names.value}")
                    return true
                }
            }
            // 所有自定义条件都满足，则不能跳过
        }
        return false
    }

    private fun notSkipWhenCustomVarMatch(additionalOptions: ElementAdditionalOptions?) =
        additionalOptions != null && additionalOptions.runCondition == RunCondition.CUSTOM_VARIABLE_MATCH &&
            additionalOptions.customVariables != null && additionalOptions.customVariables!!.isNotEmpty()

    private fun skipWhenCustomVarMatch(additionalOptions: ElementAdditionalOptions?) =
        additionalOptions != null && additionalOptions.runCondition == RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN &&
            additionalOptions.customVariables != null && additionalOptions.customVariables!!.isNotEmpty()

    private fun skipByRunCondition(runCondition: RunCondition?, currentStatus: BuildStatus, continueWhenFailure: Boolean): Boolean {
        return when (runCondition) {
            // #2375 跳过: 只有前面有插件运行失败时才运行 -> 要满足前面没有失败的任务并且没有失败并继续的任务 才会跳过
            RunCondition.PRE_TASK_FAILED_ONLY -> currentStatus.isSuccess() && !continueWhenFailure
            // #2375 跳过: 所有前置插件运行成功时才运行 -> 要满足前面有失败的任务 才会跳过
            RunCondition.PRE_TASK_SUCCESS -> currentStatus.isFailure()
            else -> false
        }
    }

    fun checkJobSkipCondition(
        conditions: List<NameAndValue>,
        variables: Map<String, String>,
        buildId: String,
        runCondition: JobRunCondition
    ): Boolean {
        val skip = when (runCondition) {
            JobRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> true // 条件匹配就跳过
            JobRunCondition.CUSTOM_VARIABLE_MATCH -> false // 条件全匹配就运行
            else -> return false // 其它类型直接返回不跳过
        }
        for (names in conditions) {
            val key = names.key
            val value = names.value
            val existValue = variables[key]
            val env = EnvUtils.parseEnv(value, variables)
            if (env != existValue) {
                logger.info("[$buildId]|JOB_CONDITION|$runCondition|key=$key|actual=$existValue|expect=$value")
                return !skip // 不满足则取反
            }
        }
        return skip
    }

    fun checkStageSkipCondition(
        conditions: List<NameAndValue>,
        variables: Map<String, Any>,
        buildId: String,
        runCondition: StageRunCondition
    ): Boolean {
        val skip = when (runCondition) {
            StageRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> true // 条件匹配就跳过
            StageRunCondition.CUSTOM_VARIABLE_MATCH -> false // 条件全匹配就运行
            else -> return false // 其它类型直接返回不跳过
        }
        for (names in conditions) {
            val key = names.key
            val value = names.value
            val existValue = variables[key]
            if (value != existValue) {
                logger.info("[$buildId]|STAGE_CONDITION|$runCondition|key=$key|actual=$existValue|expect=$value")
                return !skip // 不满足则取反
            }
        }
        return skip
    }
}
