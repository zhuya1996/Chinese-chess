# 构建状态

- job_status: success
- run_id: 38879726947
- run_attempt: 1
- sha: 348cf96a44eeb57466c02cab14b1231aa09fc877
- app_version: 2.3 (14)
- apk_signature_sha256: FC
- ref: refs/heads/master
- time_utc: 2026-10-16T09:21:30Z

## 关键错误行
```
```

## 日志末尾 200 行
```
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.9/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:preBuild UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
> Task :app:javaPreCompileRelease FROM-CACHE
> Task :app:checkReleaseAarMetadata
> Task :app:generateReleaseResValues FROM-CACHE
> Task :app:mapReleaseSourceSetPaths
> Task :app:generateReleaseResources FROM-CACHE
> Task :app:mergeReleaseResources FROM-CACHE
> Task :app:packageReleaseResources FROM-CACHE
> Task :app:parseReleaseLocalResources FROM-CACHE
> Task :app:createReleaseCompatibleScreenManifests
> Task :app:extractDeepLinksRelease FROM-CACHE
> Task :app:processReleaseMainManifest FROM-CACHE
> Task :app:processReleaseManifest FROM-CACHE
> Task :app:processReleaseManifestForPackage FROM-CACHE
> Task :app:processReleaseResources FROM-CACHE

> Task :app:compileReleaseJavaWithJavac
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

> Task :app:extractProguardFiles
> Task :app:generateReleaseLintVitalReportModel
> Task :app:mergeReleaseJniLibFolders
> Task :app:checkReleaseDuplicateClasses
> Task :app:mergeReleaseNativeLibs
> Task :app:dexBuilderRelease
> Task :app:desugarReleaseFileDependencies FROM-CACHE
> Task :app:mergeReleaseStartupProfile
> Task :app:mergeExtDexRelease FROM-CACHE
> Task :app:mergeReleaseArtProfile

> Task :app:stripReleaseDebugSymbols
Unable to strip the following libraries, packaging them as they are: libpikafish.so.

> Task :app:mergeReleaseGlobalSynthetics FROM-CACHE
> Task :app:extractReleaseNativeSymbolTables FROM-CACHE
> Task :app:mergeReleaseNativeDebugMetadata NO-SOURCE
> Task :app:mergeReleaseShaders
> Task :app:compileReleaseShaders NO-SOURCE
> Task :app:generateReleaseAssets UP-TO-DATE
> Task :app:mergeReleaseAssets
> Task :app:extractReleaseVersionControlInfo
> Task :app:processReleaseJavaRes NO-SOURCE
> Task :app:mergeDexRelease
> Task :app:compileReleaseArtProfile
> Task :app:mergeReleaseJavaResource
> Task :app:collectReleaseDependencies
> Task :app:optimizeReleaseResources
> Task :app:sdkReleaseDependencyData
> Task :app:validateSigningRelease
> Task :app:writeReleaseAppMetadata
> Task :app:writeReleaseSigningConfigVersions
> Task :app:compressReleaseAssets
> Task :app:packageRelease
> Task :app:createReleaseApkListingFileRedirect
> Task :app:lintVitalAnalyzeRelease
> Task :app:lintVitalReportRelease
> Task :app:lintVitalRelease
> Task :app:assembleRelease
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_3-1792142432202.json

BUILD SUCCESSFUL in 25s
46 actionable tasks: 31 executed, 15 from cache
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.9/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:javaPreCompileDebug FROM-CACHE
> Task :app:generateDebugResValues FROM-CACHE
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources FROM-CACHE
> Task :app:mergeDebugResources FROM-CACHE
> Task :app:packageDebugResources FROM-CACHE
> Task :app:parseDebugLocalResources FROM-CACHE
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug FROM-CACHE
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:processDebugJavaRes NO-SOURCE
> Task :app:processDebugManifestForPackage
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeDebugJavaResource
> Task :app:desugarDebugFileDependencies FROM-CACHE
> Task :app:mergeDebugStartupProfile
> Task :app:mergeExtDexDebug FROM-CACHE
> Task :app:mergeLibDexDebug FROM-CACHE
> Task :app:mergeDebugJniLibFolders
> Task :app:mergeDebugNativeLibs
> Task :app:processDebugResources
> Task :app:validateSigningDebug
> Task :app:compileDebugJavaWithJavac FROM-CACHE
> Task :app:compressDebugAssets
> Task :app:dexBuilderDebug
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions

> Task :app:stripDebugDebugSymbols
Unable to strip the following libraries, packaging them as they are: libpikafish.so.

> Task :app:mergeProjectDexDebug
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1792142456310.json

BUILD SUCCESSFUL in 13s
34 actionable tasks: 23 executed, 11 from cache
```

## Releases 资产实况（用来确认资产有没有真的被替换）
```
tag=v2.3 | assets=xiangqi-overlay-debug.apk 52150143B up=2026-10-16T09:21:28Z ; xiangqi-overlay-release.apk 52064128B up=2026-10-16T09:21:28Z
tag=v2.2 | assets=xiangqi-overlay-debug.apk 51949201B up=2026-10-16T08:16:12Z ; xiangqi-overlay-release.apk 51865901B up=2026-10-16T08:16:12Z
tag=apk-latest | assets=xiangqi-overlay-debug.apk 52150143B up=2026-10-16T09:21:20Z ; xiangqi-overlay-release.apk 52064128B up=2026-10-16T09:21:20Z
tag=v2.1 | assets=xiangqi-overlay-debug.apk 51945369B up=2026-10-14T15:34:44Z ; xiangqi-overlay-release.apk 51863390B up=2026-10-14T15:34:44Z
```

## 关键文件是否存在
```
total 2.3M
-rw-r--r-- 1 runner runner 2.4M Oct 16 09:20 libpikafish.so
total 49M
-rw-r--r-- 1 runner runner 3.1K Oct 16 09:20 ml_selftest.bin
-rw-r--r-- 1 runner runner 201K Oct 16 09:20 ml_weights.bin
-rw-r--r-- 1 runner runner  49M Oct 16 09:20 pikafish.nnue
total 100M
-rw-r--r-- 1 runner runner 50M Oct 16 09:21 xiangqi-overlay-debug.apk
-rw-r--r-- 1 runner runner 50M Oct 16 09:21 xiangqi-overlay-release.apk
```
