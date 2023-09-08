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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

plugins {
    id("com.namics.oss.gradle.license-enforce-plugin")
}

tasks {

    enforceLicenses {
        allowedCategories = listOf(
            // 以下协议类型为安全的协议类型, 不存在传染性.
            "Apache", "BSD", "LGPL", "MIT", "ISC", "MPL", "GPLv2+CE", "WTFPL", "EPL", "CDDL", "CPL", "CC0"
        )
        allowedDependencies = listOf(
            // 向mysql免费申请MySQL Ready Partner (free) , 可允许绕开GPL协议,可以使用双协议而不冲突
            "com.mysql:mysql-connector-j:*",
            // https://sqlite.org/copyright.html 属于公共领域完全不需要许可,使用上不受任何限制.
            "org.tmatesoft.sqljet:sqljet:1.1.12",
            // https://h2database.com/html/license.html h2 允许集成并分发他们, 使用EPL 1.0 或 MPL 2.0 ,没有传染性
            "com.h2database:h2:1.4.200",
            // https://www.antlr.org/license.html 本身是BSD协议, jar包没带license扫不出来, 单独豁免
            "org.antlr:antlr-runtime:3.4",
            // https://creativecommons.org/licenses/by/2.5/  本身是CC协议, jar包没带license扫不出来, 单独豁免
            "net.jcip:jcip-annotations:1.0"
        )
        dictionaries = listOf("${rootProject.projectDir}/license-additional-dict.yml")
        allowedLicenses = listOf( // license.id
            "Revised BSD",
            "MIT-0", // 比MIT-0 还宽松的协议,允许
            "Elastic License 2.0", // Elastic License v2 只会限制直接提供ES API的云服务,以及破解其商业密钥解锁高级功能
            "JSON",   // JSON BSD类型
            "TMate", // SVN 代码库依赖
            "Perforce Software" // P4代码库开源协议
        )
//        analyseConfigurations = listOf("compile", "api", "implementation")
    }
}
