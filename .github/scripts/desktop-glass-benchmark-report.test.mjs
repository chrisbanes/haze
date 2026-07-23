// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict'
import test from 'node:test'

import {
  renderComment,
  selectMarkerComment,
  validateReport,
} from './desktop-glass-benchmark-report.mjs'

const headSha = 'a'.repeat(40)
const runUrl = 'https://github.com/chrisbanes/haze/actions/runs/123'

test('renders the two head-only scenario summaries', () => {
  const body = renderComment(validateReport(report(), headSha), runUrl)

  assert.match(body, /Pointer sweep/)
  assert.match(body, /Playground drag/)
  assert.match(body, /p50/)
  assert.match(body, /p95/)
  assert.match(body, /max/)
  assert.doesNotMatch(body, /base|delta|noise/i)
})

test('rejects results that are not from the expected Metal run', () => {
  const wrongSha = report()
  wrongSha.commitSha = 'b'.repeat(40)
  assert.throws(() => validateReport(wrongSha, headSha), /commit/)

  const wrongRenderer = report()
  wrongRenderer.scenarios[0].environment.renderApi = 'SOFTWARE'
  assert.throws(() => validateReport(wrongRenderer, headSha), /METAL/)

  const missingScenario = report()
  missingScenario.scenarios.pop()
  assert.throws(() => validateReport(missingScenario, headSha), /scenarios/)
})

test('does not render artifact-controlled strings', () => {
  const hostile = report()
  hostile.unused = '@octocat **hello**'
  hostile.scenarios[0].environment.cpu = '@octocat'

  const body = renderComment(validateReport(hostile, headSha), runUrl)

  assert.doesNotMatch(body, /octocat|hello/)
})

test('updates only the existing bot marker comment', () => {
  assert.deepEqual(
    selectMarkerComment([
      { id: 1, user: { type: 'User' }, body: '<!-- desktop-glass-benchmark -->' },
      { id: 2, user: { type: 'Bot' }, body: '<!-- desktop-glass-benchmark -->' },
    ]),
    { operation: 'update', commentId: 2 },
  )
  assert.deepEqual(selectMarkerComment([]), { operation: 'create' })
})

function report() {
  const environment = {
    osName: 'Mac OS X',
    osVersion: '26',
    architecture: 'aarch64',
    cpu: 'Apple',
    memoryBytes: 1,
    javaVendor: 'Azul',
    javaVersion: '21',
    composeVersion: '1',
    skikoVersion: '1',
    renderApi: 'METAL',
    framebufferWidth: 1280,
    framebufferHeight: 720,
    contentScale: 2,
    refreshRateHz: 120,
    runnerImage: null,
    runnerImageVersion: null,
  }
  const metric = {
    sampleCount: 10,
    p50Nanos: 1_000_000,
    p95Nanos: 2_000_000,
    maxNanos: 3_000_000,
  }
  return {
    schemaVersion: 1,
    suiteId: 'glass',
    commitSha: headSha,
    scenarios: [
      {
        id: 'pointer_sweep',
        environment: structuredClone(environment),
        renderDuration: structuredClone(metric),
        callbackInterval: structuredClone(metric),
      },
      {
        id: 'playground_drag',
        environment: structuredClone(environment),
        renderDuration: structuredClone(metric),
        callbackInterval: structuredClone(metric),
      },
    ],
  }
}
