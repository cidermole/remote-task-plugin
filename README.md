# remote-task

## Introduction

Jenkins plugin to run command directly via ProcessBuilder on remote agents.

## Getting started

Pass arguments as you would to ProcessBuilder.

Example invocation:

```
node('label') {
  remoteTask(['whoami'])
}
```

Supported additional options (compatible to `bat()`):

```
def text = remoteTask(command: ['cmd', '/c', 'set'], returnStdout: true)
def rc = remoteTask(command: ['cmd', '/c', 'set'], returnStatus: true)
```

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

