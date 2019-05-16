@echo off

setlocal
 
set CLASSPATH=.;..\target\classes;..\target\test-classes;^
D:\project\coroutine\coroutines\1.4.2\lib\*;

java -javaagent:..\lib\java-agent-1.4.2.jar %*

endlocal
