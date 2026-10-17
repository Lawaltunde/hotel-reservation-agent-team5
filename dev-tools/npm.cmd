@ECHO OFF
"%~dp0..\frontend\node\node.exe" "%~dp0..\frontend\node\node_modules\npm\bin\npm-cli.js" %*
EXIT /B %ERRORLEVEL%
