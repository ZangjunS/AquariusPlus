// Jenkins 首次启动：创建 ds-cicd 任务（Freestyle + Execute shell，仅用 Jenkins 核心功能，不依赖插件）
// 如需 Pipeline 版，安装 workflow-aggregator 插件后把 job 换成 CpsFlowDefinition（脚本见 infra/jenkins/Jenkinsfile）
import jenkins.model.Jenkins
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.model.BooleanParameterDefinition
import hudson.tasks.Shell

def j = Jenkins.get()

// 本地单机环境：关闭 CSRF crumb 校验，便于用 REST/curl 直接触发构建
try {
    j.setCrumbIssuer(null)
    println "[init] 已关闭 CSRF crumb 校验（适合本机/内网）"
} catch (Throwable t) {
    println "[init] 关闭 crumb 失败（可忽略）：${t.message}"
}

def jobName = 'ds-cicd'
// 入口用 bootstrap：先从 GitHub 拉代码，再执行仓库里的 ci.sh
def shell = 'bash /src/infra/jenkins/bootstrap.sh'

def configure = { FreeStyleProject job ->
    job.description = 'DS 微服务：GitHub 拉取 → Maven 构建 → 推送私有仓库 → K8s 滚动发布 → 访问验证'
    job.setConcurrentBuild(false)
    job.buildersList.clear()
    job.buildersList.add(new Shell(shell))
    job.removeProperty(ParametersDefinitionProperty)

    def mods = [
        'ds-auth'   : 'BUILD_AUTH',
        'ds-user'   : 'BUILD_USER',
        'ds-product': 'BUILD_PRODUCT',
        'ds-gateway': 'BUILD_GATEWAY',
        'ds-web'    : 'BUILD_WEB'
    ]
    def restarts = [
        'ds-auth'   : 'RS_AUTH',
        'ds-user'   : 'RS_USER',
        'ds-product': 'RS_PRODUCT',
        'ds-gateway': 'RS_GATEWAY',
        'ds-web'    : 'RS_WEB'
    ]

    def params = []
    params << new StringParameterDefinition('TAG', '', '镜像 tag；留空自动用 build-<构建号>')
    params << new BooleanParameterDefinition('SKIP_BUILD', false, '跳过 Maven/镜像构建/推送，直接用已有 TAG 发布')
    params << new BooleanParameterDefinition('BUILD_ALL', false, '【一键全选】勾上=构建全部 5 个模块（优先级高于下面单独勾选）')
    mods.each { name, var ->
        params << new BooleanParameterDefinition(var, false, "构建 ${name}（默认不勾）")
    }
    params << new BooleanParameterDefinition('RS_NONE', false, '【一键取消】勾上=构建完成后不重启任何服务（优先级高于下面单独勾选）')
    restarts.each { name, var ->
        params << new BooleanParameterDefinition(var, true, "构建完成后自动重启 ${name}（默认勾选）")
    }

    job.addProperty(new ParametersDefinitionProperty(params.toArray(new hudson.model.ParameterDefinition[0])))
    job.save()
}

def existing = j.getItem(jobName)
if (existing == null) {
    def job = j.createProject(FreeStyleProject, jobName)
    configure(job)
    println "[init] 已创建任务 ${jobName}（TAG / SKIP_BUILD / BUILD_* 勾选 / RS_* 重启）"
} else {
    configure(existing)
    println "[init] 已刷新任务 ${jobName}（TAG / SKIP_BUILD / BUILD_* 勾选 / RS_* 重启）"
}
