# Markdown Viewer Plugin · 功能与架构设计

> 面向开发者和代码阅读者的架构说明。读完这份文档之后，你应该能回答：
> - 插件为用户提供了哪些能力？
> - 从 HTTP 请求进来到 HTML 吐出去，经过了哪些组件？
> - 如果我要加一个新入口 / 新扩展，代码应该落在哪里？

---

## 1. 插件定位

| 维度 | 说明 |
|---|---|
| **一句话** | 把 Jenkins 构建归档中的 `.md` 文件在 **构建页 / Job 页 / 全局看板** 上直接 **在线渲染** 出来。 |
| **零配置** | 不需要新增构建步骤、不需要把 Markdown 转成 HTML 再归档，只要 `archiveArtifacts '**/*.md'` 就行。 |
| **自包含** | 前端资源 (`github-markdown-css` / `highlight.js` / `mermaid`) 全部打进 `hpi`，离线环境也能工作。 |
| **Jenkins 兼容** | 基线 LTS **2.426.x**（JDK 17 + Maven 3.9+）。 |

---

## 2. 功能矩阵

| 分类 | 能力 | 入口 / 机制 |
|---|---|---|
| **入口** | 构建页左侧栏"归档 Markdown" | `RunMarkdownAction` |
| | Job 页左侧栏"归档 Markdown" | `JobMarkdownAction` |
| | Job 主面板（"上次成功的成品"下方）蓝框汇总 | `JobMarkdownAction/jobMain.jelly` + `place.js` |
| | Jenkins 顶层菜单"归档 Markdown"全局看板 | `MarkdownDashboardAction`（`RootAction`） |
| **渲染** | CommonMark 解析 | flexmark-java |
| | GFM 扩展：表格、任务列表、删除线、自动链接、锚点、emoji、目录（TOC） | flexmark 扩展模块 |
| | 代码块语法高亮 | 前端 `highlight.js`（自动探测语言）|
| | Mermaid 流程图 / 时序图 | 前端 `mermaid.min.js`（识别 <code>```mermaid</code>） |
| | 自动目录（TOC）侧边栏 | `app.js` 扫描 `h2/h3/h4`，浮在正文右侧 |
| | GitHub 风格样式 | `github-markdown-css` |
| | 内部 `.md` 链接自动重写成插件渲染链接 | `app.js` 中的 link-rewriter |
| **安全** | 路径穿越防御（Canonical 路径校验） | `SafePath` |
| | HTML XSS 过滤（白名单策略） | OWASP Java HTML Sanitizer |
| | 单文件大小上限（默认 2 MiB） | `MarkdownRenderer.maxBytes()` |
| | 权限校验（`Item.READ`） | 每个 Action 的 `doShow` 开头 |
| **性能** | 渲染结果 LRU 缓存（默认 256 条） | Caffeine；key = `runId + path + mtime` |
| **i18n** | 中英双语（Java `Messages_*.properties` + Jelly class-scoped `.properties`） | `Messages.java` 代码生成 |

---

## 3. 整体架构

### 3.1 分层视图

```
┌────────────────────────────────────────────────────────────────────┐
│   浏览器 / 用户                                                     │
│   ├── 左侧栏点 "归档 Markdown"    (sidebar Action)                  │
│   ├── Job 主页"上次成功的成品"下方蓝框  (jobMain.jelly + place.js)  │
│   └── 顶层菜单 "归档 Markdown"    (RootAction)                      │
└──────────────────────────┬─────────────────────────────────────────┘
                           │   HTTP
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│   Jenkins Core (Stapler 路由 + Jelly 渲染)                          │
│                                                                    │
│   TransientActionFactory                                           │
│     ├── RunMarkdownActionFactory  → per-Run Action 注入            │
│     └── JobMarkdownActionFactory  → per-Job Action 注入            │
│                                                                    │
│   @Extension RootAction                                            │
│     └── MarkdownDashboardAction   → 顶层菜单                       │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│   插件业务层                                                        │
│                                                                    │
│   ┌────────────────┐   ┌────────────────┐   ┌──────────────────┐   │
│   │ RunMarkdown-   │   │ JobMarkdown-   │   │ MarkdownDash-    │   │
│   │ Action         │   │ Action         │   │ boardAction      │   │
│   │ (per build)    │   │ (per job)      │   │ (global)         │   │
│   │ doShow/doRaw   │   │ doShow         │   │ Entry 列表       │   │
│   └────────┬───────┘   └────────┬───────┘   └────────┬─────────┘   │
│            │                    │                    │             │
│            └─────────┬──────────┴──────────┬─────────┘             │
│                      ▼                     ▼                       │
│              ┌───────────────┐      ┌──────────────┐               │
│              │ SafePath      │      │ Markdown-    │               │
│              │ (路径安全)     │      │ Renderer     │               │
│              │ listFiles/    │      │ (渲染核心)    │               │
│              │ resolveInside │      │ + 缓存        │               │
│              └───────┬───────┘      └──────┬───────┘               │
│                      ▼                     ▼                       │
│          ┌──────────────────────┐    ┌─────────────────────┐       │
│          │ $JENKINS/jobs/.../   │    │ flexmark-java       │       │
│          │   builds/N/archive/  │    │   ↓ raw HTML        │       │
│          │   **/*.md            │    │ OWASP Sanitizer     │       │
│          │                      │    │   ↓ safe HTML       │       │
│          │                      │    │ Caffeine LRU cache  │       │
│          └──────────────────────┘    └─────────────────────┘       │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 请求时序（以 Job 主面板点击 `ConfigDiff.md` 为例）

```
用户点 "ConfigDiff.md" 链接
  │
  │  GET /job/X/markdown/show?path=ConfigDiff.md
  ▼
Stapler 路由
  │
  │  URL "markdown" → JobMarkdownAction.getUrlName()
  │  方法 doShow(req, rsp, path)
  ▼
JobMarkdownAction.doShow
  │
  ├─ 1. checkPermission(Item.READ)                      ← 权限关
  ├─ 2. getReferenceBuild() → lastSuccessful | last     ← 选构建
  ├─ 3. SafePath.resolveInside(archive, path)           ← 路径安全关
  │     ↳ canonical 路径必须仍在 archive/ 内部，否则 404
  ├─ 4. 文件大小 > cap ? → 413 Payload Too Large
  ├─ 5. 读文件 (UTF-8)
  ├─ 6. MarkdownRenderer.render(cacheKey, md)
  │     ├─ cacheKey = runId + "|" + path + "|" + mtime
  │     ├─ 命中缓存 → 直接返回安全 HTML
  │     └─ 未命中 → flexmark 解析 → OWASP sanitize → 写缓存
  ├─ 7. req.setAttribute("html", html)
  └─ 8. forward → show.jelly
         ↳ Jelly 把 HTML 塞进 <l:layout>
         ↳ 注入 app.css / app.js / github-markdown.min.css / highlight.js / mermaid
         ↳ 浏览器收到 HTML 后再做：TOC 生成、代码高亮、Mermaid 渲染、内部链接改写
```

---

## 4. 渲染管线（核心）

```
.md 文本
   │
   │  ① flexmark Parser (with extensions)
   │     Tables · TaskList · Strikethrough · Autolink
   │     AnchorLink · TOC · Emoji
   ▼
Node (AST)
   │
   │  ② flexmark HtmlRenderer
   │     SOFT_BREAK → "<br/>"
   ▼
raw HTML (可能含 XSS payload)
   │
   │  ③ OWASP PolicyFactory.sanitize()
   │     白名单：FORMATTING + BLOCKS + LINKS + TABLES
   │            + IMAGES + STYLES
   │            + 自定义 (hr/pre/code/del/s/input, class 全局,
   │                     id 锚点, target+rel 外链, type/checked 任务列表)
   │     协议白名单：http/https/mailto 等标准 URL
   ▼
safe HTML
   │
   │  ④ Caffeine.put(cacheKey, safeHtml)    （256 条 LRU）
   ▼
返回给 Jelly 层
   │
   │  ⑤ show.jelly 直接 HTML 嵌入 <l:main-panel>
   ▼
浏览器收到后：
   ⑥ highlight.js        → 代码块高亮
   ⑦ mermaid.initialize  → 图表渲染
   ⑧ app.js.buildToc     → 扫描 h2/h3 构建右侧目录
   ⑨ app.js.rewriteLinks → 把 `<a href="other.md">` 改成插件渲染链接
```

**缓存键为什么包含 `mtime`？** 归档产物理论上是不可变的，但 `archiveArtifacts` 支持覆盖前一次归档。加入 `file.lastModified()` 后，一旦源文件被覆盖，缓存 key 自然失效，不会吐出旧版 HTML。

---

## 5. 三个入口详解

### 5.1 RunMarkdownAction（构建级）

| 项 | 值 |
|---|---|
| **类型** | `RunAction2`（Jenkins 标准的 per-Run Action）|
| **注册** | `RunMarkdownActionFactory extends TransientActionFactory<Run>` |
| **URL** | `/job/X/<N>/markdown/` |
| **显示条件** | `getIconFileName()` 中判断 `hasAnyMarkdown()`，没有 `.md` 归档时返回 `null` 使侧栏条目消失 |
| **Endpoints** | `doShow(path)`：渲染页 · `doRaw(path)`：原文下载（`text/markdown`）|
| **Jelly** | `index.jelly`（文件列表）· `show.jelly`（渲染页） |

### 5.2 JobMarkdownAction（Job 级）

| 项 | 值 |
|---|---|
| **类型** | `Action`（普通 Action） |
| **注册** | `JobMarkdownActionFactory extends TransientActionFactory<Job>` |
| **URL** | `/job/X/markdown/` |
| **参考构建选择** | `getReferenceBuild()` = `lastSuccessfulBuild` **优先**，否则 `lastBuild`，都没有就空 |
| **额外位置** | 除了左栏入口外，还有 `jobMain.jelly` 把汇总框渲染到 Job 主页面 |

#### 5.2.1 `jobMain.jelly` 如何定位到"上次成功的成品"下方

`WorkflowJob/main.jelly` 和 `Job/main.jelly` 都会做：

```xml
<j:forEach var="a" items="${it.allActions}">
    <st:include page="jobMain.jelly" it="${a}" optional="true"/>
</j:forEach>
```

这个 `forEach` 按 action 注册顺序渲染，Pipeline Stage View 先于我们 → 我们默认会被顶到阶段视图下方。为此：

1. `jobMain.jelly` 自己渲染一个带 `id="markdown-viewer-job-summary"` 的 `<div>`。
2. 用 `<st:adjunct includes="io.jenkins.plugins.markdownviewer.JobMarkdownAction.place"/>` 加载 `place.js`。
3. `place.js` **只用 id 查到自己那个 div**，找到 "last successful artifacts" 表格（`a[href*="lastSuccessfulBuild/artifact/"]` 向上回溯到 `<table>`），`insertBefore(box, target.nextSibling)` 把自己搬上去。

**为什么用 adjunct 而不是 `<script>` 内联？**
Jelly 的 `escape-by-default='true'` 会把内联脚本里的 `&&` 转义成 `&amp;&amp;`，导致 JS 语法错误。用 adjunct 走静态文件就绕过这个管线，同时还能享受 Jenkins 内置的静态资源缓存（URL 带 `/adjuncts/<hash>/`，指纹化）。

### 5.3 MarkdownDashboardAction（全局）

| 项 | 值 |
|---|---|
| **类型** | `@Extension RootAction`（顶级菜单）|
| **URL** | `/jenkins/markdown/` |
| **数据来源** | `Jenkins.get().getAllItems(Job.class)`，遍历所有 Job，过滤 `Item.READ`，取每个 Job 的 last successful build 的 `.md` 归档 |
| **Jelly** | `MarkdownDashboardAction/index.jelly` 直接输出 `Entry` 列表 |

---

## 6. 安全模型

### 6.1 路径穿越

`SafePath.resolveInside(root, relative)`：
1. 拒绝空字符串、以 `/` 开头、包含 `\0` 的输入。
2. `File.getCanonicalFile()` 解析符号链接/`../`。
3. 验证规范化后的路径以 `canonicalRoot + File.separator` 开头（严格前缀匹配，不会被 `/tmp/archive2` 伪造）。
4. 要求最终是 **正规文件**（排除目录/设备）。

**测试覆盖**：`SafePathTest` 包含 `../../../etc/passwd`、绝对路径、符号链接逃逸等 case。

### 6.2 HTML 过滤

flexmark 输出的 HTML **仍可能包含**用户注入的 `<script>`（因为 Markdown 语法允许嵌入 raw HTML）。所以过 OWASP 白名单是**必须**的：

```java
Sanitizers.FORMATTING
  .and(Sanitizers.BLOCKS)
  .and(Sanitizers.LINKS)
  .and(Sanitizers.TABLES)
  .and(Sanitizers.IMAGES)
  .and(Sanitizers.STYLES)
  .and(new HtmlPolicyBuilder()
        .allowElements("hr", "pre", "code", "del", "s", "input")
        .allowAttributes("class").globally()
        .allowAttributes("id").onElements("h1".."h6", "a", "div")
        .allowAttributes("type", "checked", "disabled").onElements("input")
        .allowAttributes("start", "reversed", "type").onElements("ol")
        .allowAttributes("align").onElements("th", "td")
        .allowAttributes("target", "rel").onElements("a")
        .allowStandardUrlProtocols()
        .toFactory())
```

**关键点**：
- `<script>` / `<iframe>` / 事件处理器（`onclick=` 等）全部被剥离。
- URL 协议白名单，避免 `javascript:` / `data:` 之类的 payload。
- Dependabot 已推进到 `owasp-java-html-sanitizer 20260101.1`（修复 CVE-2025-66021）。

### 6.3 权限

| 操作 | 检查 |
|---|---|
| 构建页查看 `.md` (`RunMarkdownAction.doShow`) | `run.getParent().checkPermission(Item.READ)` |
| Job 页查看 `.md` (`JobMarkdownAction.doShow`) | `job.checkPermission(Item.READ)` |
| Dashboard 聚合 | 遍历时对每个 job 做 `hasPermission(Item.READ)` 过滤 |

**为何用 `Item.READ` 而不是 `Run.ARTIFACTS`？** 项目最初用的是 `Run.ARTIFACTS`，导致开发者/观察者权限被 403。文档内容属于项目可见性范畴，与用户能否看到 Job 本身一致更合理。

### 6.4 大小上限

默认 2 MiB，可通过系统属性覆写：

```
-Dio.jenkins.plugins.markdownviewer.maxFileBytes=5242880   # 5 MiB
```

超限时返回 HTTP 413，不会触发渲染。

---

## 7. 性能与缓存

| 参数 | 默认 | 调整 |
|---|---|---|
| `io.jenkins.plugins.markdownviewer.cacheSize` | 256 条 | 根据 Jenkins 规模调整；每条大约 `<10 KB` HTML |
| `io.jenkins.plugins.markdownviewer.maxFileBytes` | 2 MiB | 大文档场景下调高 |

**Caffeine** 采用 W-TinyLFU，LRU+LFU 混合，命中率显著优于纯 LRU。缓存 key 设计保证：

- 同一构建同一文件多人访问 → 一次解析
- 归档被覆盖（mtime 变化）→ 自动失效
- 构建被删除 → GC 正常清理（弱引用由 Caffeine 管理）

---

## 8. 前端资源管理

**没有用 WebJars**（依赖解析经常炸）。改用 `download-maven-plugin` 在 `process-resources` 阶段直接从 jsDelivr 拉：

```
target/<finalName>/vendor/
├── github-markdown-css/github-markdown.min.css
├── highlightjs/highlight.min.js + styles/github.min.css + languages/*.js
└── mermaid/mermaid.min.js
```

这些最终打进 `hpi` 的 `/vendor/...` 路径，Jelly 通过 `${rootURL}/plugin/markdown-viewer/vendor/...` 引用。

**好处**：
- 离线可用
- 版本锁定在 `pom.xml`，构建确定性
- 跳过 WebJar 传递依赖地狱

---

## 9. 国际化（i18n）

Jenkins 里 i18n 有两条路子，本插件都用了：

| 途径 | 文件 | 使用者 |
|---|---|---|
| **Java 代码生成** | `Messages.properties` + `Messages_zh_CN.properties` → 编译期生成 `Messages.java` 静态方法 | Java 代码（`Messages.JobMarkdownAction_DisplayName()`）|
| **Jelly resource bundle** | `<ActionClass>.properties` + `<ActionClass>_zh_CN.properties`（与 Jelly 视图同 package）| Jelly 视图 `${%Key}` |

**一个坑**：当 Jelly 是通过 `<st:include page="..." it="${action}"/>` 从别的 package 注入时（例如 `WorkflowJob/main.jelly` 调我们 `jobMain.jelly`），`${%Key}` 有时解析不到。解决办法是**改走 Java**：`getLatestBuildLabel() { return Messages.Common_LatestBuild(); }`，Jelly 里用 `${it.latestBuildLabel}` 即可。

---

## 10. 工程化

| 事项 | 做法 |
|---|---|
| **构建** | Maven；父 POM `org.jenkins-ci.plugins:plugin:4.81`；`<version>1.0.x-SNAPSHOT</version>` |
| **单元测试** | `MarkdownRendererTest`（渲染正确性 + XSS 过滤）· `SafePathTest`（路径穿越） |
| **集成测试** | `RunMarkdownActionIntegrationTest` 用 `JenkinsRule` 起真实 Jenkins，模拟 `archiveArtifacts` + HTTP 调用 |
| **静态扫描** | SpotBugs（父 POM 自带）、Dependabot（GitHub Actions 启用） |
| **CI** | `.github/workflows/ci.yml`：`mvn -B -ntp verify` |
| **ci.jenkins.io 兼容** | `Jenkinsfile` 调 `buildPlugin`（Jenkins 共享库） |
| **版本可见性** | `pom.xml` 中 `<revision>1.0.3</revision>`，每次迭代 +1，Jenkins 插件管理页能清楚区分新旧 |

---

## 11. 扩展点速查

| 目的 | Jenkins 扩展点 | 本插件中的实现 |
|---|---|---|
| 给每个构建加侧栏入口 | `TransientActionFactory<Run>` + `RunAction2` | `RunMarkdownActionFactory` + `RunMarkdownAction` |
| 给每个 Job 加侧栏入口 | `TransientActionFactory<Job>` + `Action` | `JobMarkdownActionFactory` + `JobMarkdownAction` |
| Job 主页面内嵌内容 | `<Action>/jobMain.jelly` | `JobMarkdownAction/jobMain.jelly` |
| 顶层菜单入口 | `@Extension RootAction` | `MarkdownDashboardAction` |
| 插件静态 JS/CSS 注入 | `<st:adjunct includes="..."/>` | `JobMarkdownAction/place.js` |

> 未使用：`PageDecorator`（曾用于全局改写所有页面的 `<a>`，会干扰 Blue Ocean / Stage View 等其他插件；已删除，改为仅在 Job 主面板内自作自收的 adjunct 方案。）

---

## 12. 文件索引

### Java 源码
```
src/main/java/io/jenkins/plugins/markdownviewer/
├── package-info.java
├── MarkdownRenderer.java          ← 渲染核心 + 缓存
├── SafePath.java                  ← 路径安全 + 文件遍历
├── RunMarkdownAction.java         ← 构建级 Action
├── RunMarkdownActionFactory.java
├── JobMarkdownAction.java         ← Job 级 Action
├── JobMarkdownActionFactory.java
└── MarkdownDashboardAction.java   ← 全局 RootAction
```

### Jelly 视图 + adjunct JS
```
src/main/resources/io/jenkins/plugins/markdownviewer/
├── RunMarkdownAction/
│   ├── index.jelly                ← 文件列表
│   └── show.jelly                 ← 单文件渲染
├── JobMarkdownAction/
│   ├── index.jelly                ← 文件列表
│   ├── show.jelly                 ← 单文件渲染
│   ├── jobMain.jelly              ← Job 主页红框内容
│   └── place.js                   ← adjunct：把 jobMain 搬到红框位
├── MarkdownDashboardAction/
│   └── index.jelly                ← 全局看板
├── Messages.properties            ← Java 层 i18n
├── Messages_zh_CN.properties
├── <ActionClass>.properties       ← Jelly 层 i18n（3 × 2 = 6 个文件）
└── <ActionClass>_zh_CN.properties
```

### 前端静态资源
```
src/main/webapp/
├── css/app.css                    ← 自定义样式
└── js/app.js                      ← 渲染后增强：TOC、highlight、mermaid、link-rewrite
```

### 测试
```
src/test/java/io/jenkins/plugins/markdownviewer/
├── MarkdownRendererTest.java
├── SafePathTest.java
└── RunMarkdownActionIntegrationTest.java
```

### 工程化
```
pom.xml                 ← 依赖、版本、download-maven-plugin
Jenkinsfile             ← buildPlugin
.github/workflows/ci.yml
README.md · CONTRIBUTING.md · LICENSE
```

---

## 13. 历史决策记录

| 问题 | 决定 | 理由 |
|---|---|---|
| WebJars 传递依赖冲突 | 改用 `download-maven-plugin` 直拉 CDN | WebJar 依赖树复杂且版本对不上 |
| 构建时 `flexmark-ext-gfm-tables` 找不到 | 改用 `flexmark-ext-tables`（0.64.8） | artifact 名称在该版本里变了 |
| Jelly `${%Key}` 在 `<st:include>` 下失效 | 改用 Java `getter` 返回 `Messages.*()` | bundle 作用域问题，Java 方法 100% 靠谱 |
| 全局 `PageDecorator` 改写 artifact 链接 | **放弃** | 可能干扰 Blue Ocean / Stage View，风险高 |
| jobMain.jelly 被顶到阶段视图下方 | 加 adjunct JS 自己搬上去 | 只操作自己 id，零副作用 |
| `Implementation-Build` 靠 git SHA，未 commit 时改动看不出来 | `<revision>` 每次手动升 | Jenkins 插件管理页版本字段可辨识 |
| 权限用 `Run.ARTIFACTS` 导致 403 | 改 `Item.READ` | 文档与项目可见性对齐更合理 |
| Dependabot 报 `owasp-java-html-sanitizer` XSS | 升到 `20260101.1` | 修复 CVE-2025-66021 |

---

_最后更新：2026-04-21（插件版本 1.0.3-SNAPSHOT）_
