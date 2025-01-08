# Introduction
This plugin helps in identifying licenses of all dependencies of a project and then reporting it to a given end point.
# Usage
```
mvn com.sodaru:sos-license-maven-plugin:<version>:check-license -Dlicense.validator.endpoint=<endPoint> -Dlicense.validator.key=<key> -DprojectName=<projectName> -DprojectUrl=<projectUrl>
```
Once this command is run, the end point can return either HTTP 200 indicating a success or another code indicating a failure. If successful, this command exits normally. Else, this command exits exceptionally thus failing the build process.
