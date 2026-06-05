@echo off

rem -------------------------------------------------------------------------
rem
rem 使用说明：
rem
rem 1: 该脚本用于别的项目时只需要修改 MAIN_CLASS 即可运行
rem
rem 2: JAVA_OPTS 可通过 -D 传入 undertow.port 与 undertow.host 这类参数覆盖
rem    配置文件中的相同值此外还有 undertow.resourcePath, undertow.ioThreads
rem    undertow.workerThreads 共五个参数可通过 -D 进行传入
rem
rem 3: JAVA_OPTS 可传入标准的 java 命令行参数,例如 -Xms256m -Xmx1024m 这类常用参数
rem
rem
rem -------------------------------------------------------------------------

setlocal & pushd


rem 启动入口类，需修改为项目实际入口类
set MAIN_CLASS=cn.aifei.demo.AifeiDemo

rem Java 命令行参数,根据需要开启下面的配置,改成自己需要的,注意等号前后不能有空格
rem set "JAVA_OPTS=-Xms256m -Xmx1024m -Dundertow.port=80 -Dundertow.host=localhost"
rem set "JAVA_OPTS=-Dundertow.port=80 -Dundertow.host=localhost"


if "%1"=="start" goto normal
if "%1"=="stop" goto normal
if "%1"=="restart" goto normal

goto error


:error
echo Usage: aifei.bat start | stop | restart
goto :eof


:normal
if "%1"=="start" goto start
if "%1"=="stop" goto stop
if "%1"=="restart" goto restart
goto :eof


:start
set APP_HOME=%~dp0
cd %APP_HOME%
set CP=%APP_HOME%config;%APP_HOME%lib\*
echo starting aifei undertow
java -Xverify:none %JAVA_OPTS% -cp %CP% %MAIN_CLASS%
goto :eof


:stop
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo stopping aifei undertow
set "MAIN_CLASS=%MAIN_CLASS: =%"
for /f "tokens=1" %%i in ('jps -l ^| findstr /e /c:" %MAIN_CLASS%"') do ( taskkill /F /PID %%i )
goto :eof


:restart
call :stop
call :start
goto :eof

endlocal & popd
pause
