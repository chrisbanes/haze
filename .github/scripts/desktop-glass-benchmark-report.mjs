// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

const marker = '<!-- desktop-glass-benchmark -->'
const scenarios = new Map([
  ['pointer_sweep', 'Pointer sweep'],
  ['playground_drag', 'Playground drag'],
])

export function validateReport(report, expectedSha) {
  check(report && typeof report === 'object', 'report')
  check(report.schemaVersion === 1, 'schema version')
  check(report.suiteId === 'glass', 'suite')
  check(/^[0-9a-f]{40}$/.test(expectedSha) && report.commitSha === expectedSha, 'commit')
  check(Array.isArray(report.scenarios) && report.scenarios.length === scenarios.size, 'scenarios')

  const results = new Map()
  for (const scenario of report.scenarios) {
    check(scenario && scenarios.has(scenario.id) && !results.has(scenario.id), 'scenarios')
    const environment = scenario.environment
    check(environment?.renderApi === 'METAL', 'METAL')
    check(environment.framebufferWidth === 1280 && environment.framebufferHeight === 720, 'framebuffer')
    results.set(scenario.id, {
      renderDuration: metric(scenario.renderDuration),
      callbackInterval: metric(scenario.callbackInterval),
    })
  }
  return results
}

export function renderComment(results, runUrl) {
  const rows = []
  for (const [id, label] of scenarios) {
    const result = results.get(id)
    check(result, 'scenarios')
    rows.push(row(label, 'Render duration', result.renderDuration))
    rows.push(row(label, 'Callback interval', result.callbackInterval))
  }
  return `${marker}
### Desktop Glass benchmark

| Scenario | Metric | p50 | p95 | max | Samples |
| --- | --- | ---: | ---: | ---: | ---: |
${rows.join('\n')}

Informational measurements only; they do not gate this pull request. [Workflow run](${runUrl})
`
}

export function selectMarkerComment(comments) {
  const existing = comments.find(comment =>
    comment.user?.type === 'Bot' && comment.body?.includes(marker),
  )
  return existing
    ? { operation: 'update', commentId: existing.id }
    : { operation: 'create' }
}

function metric(value) {
  check(value && typeof value === 'object', 'metric')
  const numbers = ['sampleCount', 'p50Nanos', 'p95Nanos', 'maxNanos']
  check(numbers.every(name => Number.isSafeInteger(value[name]) && value[name] >= 0), 'metric')
  check(value.sampleCount > 0, 'metric')
  check(value.p50Nanos <= value.p95Nanos && value.p95Nanos <= value.maxNanos, 'metric')
  return Object.fromEntries(numbers.map(name => [name, value[name]]))
}

function row(scenario, name, value) {
  return `| ${scenario} | ${name} | ${millis(value.p50Nanos)} | ` +
    `${millis(value.p95Nanos)} | ${millis(value.maxNanos)} | ${value.sampleCount} |`
}

function millis(nanos) {
  return `${(nanos / 1_000_000).toFixed(2)} ms`
}

function check(condition, name) {
  if (!condition) throw new Error(`Invalid benchmark ${name}`)
}
