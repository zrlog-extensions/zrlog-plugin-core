# Plugin Core Architecture

本文记录当前 `zrlog-plugin-core` 的包边界。目标是先按两个服务面向整理：

1. `web` 负责 HTTP/UI/MVC。
2. `runtime` 负责插件交互和运行态能力。

## Package Boundaries

```mermaid
flowchart TD
    Application["Application<br/>thin JVM main"]
    Startup["ApplicationStartup<br/>startup sequence"]
    StartupOptions["ApplicationStartupOptions<br/>args -> immutable startup values"]
    Environment["ApplicationEnvironment<br/>logging + FaaS + run mode"]
    Servers["ApplicationServers<br/>runtime server + HTTP server"]
    Browser[Browser / Admin UI]
    PluginProcess[Plugin Process]

    subgraph Web["web: HTTP / MVC"]
        HttpServer["PluginHttpServer<br/>HTTP server lifecycle"]
        WebConfig["web.config<br/>HTTP server routes + interceptors"]
        WebController["web.controller<br/>页面和 REST Controller"]
        WebHandler["web.handler<br/>插件页面 HTTP proxy"]
        WebUtil["web.util<br/>HTTP response helpers"]
    end

    subgraph Runtime["runtime: plugin runtime service"]
        RuntimeServer["PluginRuntimeServer<br/>NIO + runtime workers"]
        RuntimeFeature["runtime capability / scheduler / notification / event / service / state"]
        RuntimeServices["PluginRuntimeServices<br/>startup wiring result"]
        RuntimeBridge["PluginRuntimeBridge<br/>framework bridge"]

        subgraph RuntimePlugin["runtime.plugin"]
            Transport["transport<br/>socket server + action handler"]
            PluginConfig["config<br/>plugin runtime paths + host connection values"]
            DataSource["PluginDataSourceInitializer<br/>DAO datasource bootstrap"]
            Bootstrap["bootstrap<br/>startup + metadata + artifact reconcile"]
            Lifecycle["lifecycle<br/>register / unregister / stop / delete"]
            Process["process<br/>local OS process"]
            Session["session<br/>IOSession registry"]
            Artifact["artifact<br/>plugin file lookup / download"]
        end
    end

    subgraph Infra["shared infrastructure"]
        DAO["dao"]
        Store["runtime.store"]
        Model["model / vo"]
    end

    Application --> Startup
    Startup --> Environment
    Startup --> StartupOptions
    Startup --> DataSource
    Startup --> RuntimeServices
    Startup --> Servers
    Servers --> RuntimeServices
    Servers --> RuntimeServer
    Servers --> HttpServer
    HttpServer --> WebConfig
    WebConfig --> RuntimeBridge
    RuntimeBridge --> RuntimeServices
    Browser --> WebController
    Browser --> WebHandler
    WebController --> RuntimeFeature
    WebController --> RuntimeBridge
    WebController --> WebUtil
    WebHandler --> Session
    WebHandler --> RuntimeFeature

    RuntimeServer --> Transport
    RuntimeServer --> Bootstrap
    RuntimeServer --> RuntimeFeature
    RuntimeFeature --> RuntimeBridge
    RuntimeFeature --> Session
    RuntimeFeature --> Bootstrap

    RuntimeServices --> Bootstrap
    RuntimeServices --> Lifecycle
    RuntimeServices --> Process
    RuntimeServices --> Session
    RuntimeServices --> Artifact
    RuntimeServices --> PluginConfig

    Bootstrap --> Artifact
    Bootstrap --> PluginConfig
    Bootstrap --> Process
    Bootstrap --> Session
    Transport --> PluginConfig
    Lifecycle --> Process
    Lifecycle --> Session
    Process --> PluginConfig
    Process --> Artifact
    Process --> Session
    Transport --> Lifecycle
    Transport --> RuntimeFeature
    Transport <--> PluginProcess

    WebController --> DAO
    RuntimeFeature --> Store
    RuntimeFeature --> DAO
    RuntimePlugin --> DAO
    DAO --> Model
```

## Dependency Rules

1. `web` may depend on `runtime`, `dao`, `model`, `vo`, and `util`.
2. `runtime` must not depend on `web`.
3. Plugin socket traffic enters through `runtime.plugin.transport`; it must not enter `web`.
4. Plugin process/session/bootstrap/lifecycle code stays under `runtime.plugin`.
5. Runtime feature packages call plugin code through public runtime-plugin entry points such as `PluginBootstrapService`, `PluginSessions`, and `PluginFiles`.
6. `runtime.plugin.artifact` is file-only; it must not depend on bootstrap or lifecycle.
7. `runtime.plugin.bootstrap` may depend on artifact, process, and session because it coordinates startup.
8. `runtime.plugin.lifecycle` owns cross-cutting stop/register/delete coordination between process and session.
9. HTTP server configuration belongs to `web.config`.
10. Plugin runtime configuration values belong to `runtime.plugin.config` and are assembled into `PluginRuntimeServices` by startup.
11. Data source initialization is explicit startup work and stays in `PluginDataSourceInitializer`; `PluginConfig` is a value object, not an initializer.
12. `Application` stays as the JVM entry. Argument parsing, environment setup, and server orchestration are separate startup classes.
13. `ApplicationServers` starts only the runtime-side plugin server and the web-side HTTP server.
14. Plugin lifecycle state is tied to host connection and routing viability. Capability, scheduler, and default automation failures should stay in their own runtime result or log path.
15. `ApplicationServers` owns service wiring: it decomposes `PluginRuntimeServices` into narrow runtime dependencies for `PluginRuntimeServer`, and passes the same services into `PluginHttpServerConfig` for the web bridge.
16. `PluginRuntimeServer` must not hold the complete runtime services object; it owns only the dependencies needed to start/stop runtime service work.
17. `PluginRuntimeServices` is a startup wiring result, not a general application context or web control API. New code should prefer explicit constructor arguments or `PluginRuntimeBridge`'s narrow methods.

## Main Packages

`com.zrlog.plugincore.server.web.controller`
: Standard MVC controller layer for admin pages and HTTP APIs.

`com.zrlog.plugincore.server.web.config`
: HTTP server routes, static resource mapping, HTTP interceptors, and installation of the runtime bridge used by web controllers.

`com.zrlog.plugincore.server.web.PluginHttpServer`
: HTTP server lifecycle wrapper around `PluginHttpServerConfig` and `WebServerBuilder`.

`com.zrlog.plugincore.server.web.handler`
: Web adapter for plugin-rendered pages. It resolves the target plugin session and proxies HTTP packets, but it does not own plugin startup internals.

`com.zrlog.plugincore.server.web.util`
: Web-only helpers such as in-memory runtime-list pagination into commonDAO `PageData`.

`com.zrlog.plugincore.server.runtime.PluginRuntimeServices`
: Startup wiring result containing plugin runtime services and immutable startup values. This replaces the previous context object and should not be treated as a broad business API.

`com.zrlog.plugincore.server.runtime.PluginRuntimeBridge`
: Narrow framework bridge for code that cannot receive dependencies through constructors. Prefer direct bridge methods such as `pluginBootstrap()` over passing the whole services object.

`com.zrlog.plugincore.server.runtime.plugin.PluginRuntimeServer`
: Runtime-side server lifecycle. It starts the plugin NIO transport and, outside native-agent mode, starts runtime workers. It receives NIO, bootstrap, and scheduler dependencies directly instead of holding the full runtime services object.

`com.zrlog.plugincore.server.runtime.plugin.config`
: Plugin runtime value objects, including plugin paths, FaaS runtime roots, master port, blog runtime, host connection, and explicit data source initialization.

`com.zrlog.plugincore.server.runtime.plugin.transport`
: TCP/socket adapter used by plugin processes to connect back to plugin-core.

`com.zrlog.plugincore.server.runtime.plugin.session`
: In-memory session registry and compatibility facade for existing lookup calls.

`com.zrlog.plugincore.server.runtime.plugin.process`
: Local process launcher, process output, exit watch, process id and runtime instance ids.

`com.zrlog.plugincore.server.runtime.plugin.bootstrap`
: Startup orchestration, metadata collection, installed artifact reconciliation, and async bootstrap.

`com.zrlog.plugincore.server.runtime.plugin.lifecycle`
: Register/unregister/stop/delete coordination across session registry, process runtime, plugin metadata, and runtime references.

`com.zrlog.plugincore.server.runtime.*`
: Runtime features such as capability, scheduler, notification, event, service provider selection, invocation log, state, and KV store access.
