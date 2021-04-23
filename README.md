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

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)

