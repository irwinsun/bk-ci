package com.tencent.devops.openapi.api.apigw.v3

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_APP_CODE
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_APP_CODE_DEFAULT_VALUE
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.process.pojo.PipelineIdInfo
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

@Tag(name = "OPEN_API_MOVE", description = "OPEN-API-迁移")
@Path("/{apigwType:apigw-user|apigw-app|apigw}/v3/permission/move")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ApigwPermissionMoveResourceV3 {

    @Operation(summary = "获取项目下pipelineId+自增id", tags = ["v3_app_pipeline_id_info", "v3_user_pipeline_id_info"])
    @GET
    @Path("/projects/{projectId}/pipelineIds/list")
    fun getProjectPipelineIds(
        @Parameter(description = "appCode", required = true, example = AUTH_HEADER_DEVOPS_APP_CODE_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_DEVOPS_APP_CODE)
        appCode: String?,
        @Parameter(description = "apigw Type", required = true)
        @PathParam("apigwType")
        apigwType: String?,
        @Parameter(description = "项目Code", required = true)
        @PathParam("projectId")
        projectId: String
    ): Result<List<PipelineIdInfo>>

    @Operation(summary = "关联iam项目", tags = ["v3_app_relation_iam"])
    @PUT
    @Path("/projects/{projectId}/relationProject")
    fun relationProject(
        @Parameter(description = "appCode", required = true, example = AUTH_HEADER_DEVOPS_APP_CODE_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_DEVOPS_APP_CODE)
        appCode: String?,
        @Parameter(description = "apigw Type", required = true)
        @PathParam("apigwType")
        apigwType: String?,
        @Parameter(description = "项目Code", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "iam分级管理员ID", required = true)
        @QueryParam("relationId")
        relationId: String
    ): com.tencent.devops.project.pojo.Result<Boolean>
}
