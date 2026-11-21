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
def shell = 'bash /src/infra/jenkins/ci.sh'

def configure = { FreeStyleProject job ->
    job.description = 'DS 微服务：Maven 构建 → 推送私有仓库 → K8s 滚动发布 → 访问验证'
    job.setConcurrentBuild(false)
    job.buildersList.clear()
    job.buildersList.add(new Shell(shell))
    job.removeProperty(ParametersDefinitionProperty)
    job.addProperty(new ParametersDefinitionProperty(
        new StringParameterDefinition('TAG', '', '镜像 tag；留空自动用 build-<构建号>'),
        new BooleanParameterDefinition('SKIP_BUILD', false, '跳过 Maven/镜像构建/推送，直接用已有 TAG 发布')
    ))
    job.save()
}

def existing = j.getItem(jobName)
if (existing == null) {
    def job = j.createProject(FreeStyleProject, jobName)
    configure(job)
    println "[init] 已创建任务 ${jobName}（参数 TAG / SKIP_BUILD）"
} else {
    configure(existing)
    println "[init] 已刷新任务 ${jobName}（参数 TAG / SKIP_BUILD）"
}
