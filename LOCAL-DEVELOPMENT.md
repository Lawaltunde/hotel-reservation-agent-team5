# Local development on Windows

This project uses Java 25 and Node.js, so a Python virtual environment is not needed.
Maven downloads the pinned Node/npm versions into `frontend/node`, and npm installs
dependencies into each package's `node_modules`. These folders are ignored by Git.

Install a JDK 25, or extract a portable JDK into `.tools/jdk-25...` at the repository root.
In PowerShell, activate the local tools for the current terminal:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
. .\Activate-Dev.ps1
.\mvnw.cmd -B install -DskipTests
```

The first build installs Maven, Node, npm, frontend dependencies and backend dependencies,
then packages the application. After it finishes, activate again to add the downloaded
Node/npm to PATH:

```powershell
. .\Activate-Dev.ps1
Push-Location frontend
npm.cmd run lint
npm.cmd run typecheck
Pop-Location
Push-Location e2e
npm.cmd ci
npm.cmd run install-browsers
Pop-Location
```

Docker must be installed and running for the local PostGIS database and integration tests.
Run the application with `.\mvnw.cmd -pl backend spring-boot:run` after the first build.
The packaged frontend is served by the backend at http://localhost:8080.
Use `.env.example` as the reference for service credentials; keep secrets out of Git.

Activation only changes the current PowerShell process. Close the terminal to discard it.
