# Markdown Viewer Plugin

[![CI](https://github.com/baicaisir/markdown-viewer-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/baicaisir/markdown-viewer-plugin/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Render archived `.md` files inline on Jenkins **build**, **job**, and **dashboard** pages — no build step, no conversion, no browser extension.

## Features

- Server-side rendering with flexmark-java (CommonMark + GFM)
- Sidebar entry on every **build page** listing all archived `.md` files
- **Job page** entry showing the README from the latest successful build
- Top-level **Markdown Dashboard** aggregating READMEs across all jobs
- GitHub-style CSS, syntax highlighting (highlight.js), Mermaid diagrams, auto-generated TOC
- Safe by default: path-traversal defense, OWASP HTML Sanitizer, 2 MB size cap, permission checks
- Self-contained: all CSS/JS bundled, works fully offline
- Tested with `JenkinsRule` integration tests

## Quick Start

1. Install the **Markdown Viewer** plugin from **Manage Jenkins → Plugins**.
2. In your job, archive any `.md` files:

   ```groovy
   pipeline {
       agent any
       stages {
           stage('Docs') {
               steps {
                   sh 'echo "# Hello\n\n- a\n- b" > README.md'
               }
           }
       }
       post {
           always { archiveArtifacts artifacts: '**/*.md', allowEmptyArchive: true }
       }
   }
   ```

3. Open the build page → click **Markdown Docs** in the sidebar.

## Configuration

No system configuration required. Permissions are enforced via Jenkins' native `Item.READ` and `Run.ARTIFACTS`.

Optional system properties:

| Property | Default | Meaning |
|---|---|---|
| `io.jenkins.plugins.markdownviewer.maxFileBytes` | `2097152` | Per-file size cap in bytes (2 MB) |
| `io.jenkins.plugins.markdownviewer.cacheSize` | `256` | LRU render cache entry count |

## Building from Source

```bash
./mvnw -DskipTests package
# outputs target/markdown-viewer.hpi
```

Local Jenkins for development:

```bash
./mvnw hpi:run
# open http://localhost:8080/jenkins
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE).
