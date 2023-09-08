#!/bin/bash

# 扫描工程依赖的第三方开源组件License是否合规, 以下2点不建议进行修改, 要追加License或豁免需要明确不是传染性协议,需要评审
# 1、根据需要修改 buildSrc/src/main/kotlin/plugins/task-enforce-licenses.gradle.kts 豁免相应包
# 2、根据需要追加license字典: license-additional-dict.yml

./gradlew enforceLicenses
