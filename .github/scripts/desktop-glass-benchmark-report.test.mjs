// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict'
import test from 'node:test'

import {
  parseArtifactBuffer,
  renderComment,
  validateArtifact,
} from './desktop-glass-benchmark-report.mjs'

const expectedIdentity = {
  repository: 'chrisbanes/haze',
  baseSha: 'a'.repeat(40),
  headSha: 'b'.repeat(40),
}
const runUrl = 'https://github.com/chrisbanes/haze/actions/runs/123456789'

test('renders verified ABBA results with honest observational terminology', () => {
  const body = renderComment(validateArtifact(validArtifact(), expectedIdentity), runUrl)

  assert.match(body, /<!-- desktop-glass-benchmark -->/)
  assert.match(body, /pointer\\_sweep/)
  assert.match(body, /playground\\_drag/)
  assert.match(body, /render callback duration/i)
  assert.match(body, /callback interval/i)
  assert.match(body, /observational/i)
  assert.match(body, /do not gate this pull request/i)
  assert.match(body, /\| base \|/)
  assert.match(body, /\| PR \|/)
  assert.match(body, /\+10\.0%/)
  assert.match(body, /\[Workflow run\]\(https:\/\/github\.com\/chrisbanes\/haze\/actions\/runs\/123456789\)/)
})

test('renders head-only bootstrap results without base rows or a fabricated delta', () => {
  const body = renderComment(
    validateArtifact(validArtifact({ headOnly: true }), expectedIdentity),
    runUrl,
  )

  assert.doesNotMatch(body, /\| base \|/)
  assert.match(body, /\| PR \|/)
  assert.doesNotMatch(body, /not comparable/)
  assert.doesNotMatch(body, /\+10\.0%/)
})

test('labels raw-data-derived noise from either rendered metric', () => {
  const artifact = validArtifact({
    valueFor({ revision, round, order, metric }) {
      if (revision === 'base') return metric === 'render' ? 10_000_000 : 16_000_000
      const high = round === 2 || (round === 1 && order === 2)
      return metric === 'render' ? (high ? 20_000_000 : 10_000_000) : 16_000_000
    },
  })

  const body = renderComment(validateArtifact(artifact, expectedIdentity), runUrl)

  assert.match(body, /noisy/)
})

test('labels a raw protocol mismatch as not comparable', () => {
  const artifact = validArtifact({
    protocols: { pointer_sweep: { base: 1, head: 2 } },
  })

  const body = renderComment(validateArtifact(artifact, expectedIdentity), runUrl)

  assert.match(body, /pointer\\_sweep \| PR .*not comparable/)
})

test('rejects repository, base SHA, and head SHA identity mismatches', () => {
  for (const [field, value, message] of [
    ['repository', 'attacker/fork', /repository/],
    ['baseSha', '0'.repeat(40), /base SHA/],
    ['headSha', '1'.repeat(40), /head SHA/],
  ]) {
    const artifact = validArtifact()
    artifact[field] = value
    assert.throws(() => validateArtifact(artifact, expectedIdentity), message)
  }
})

test('rejects malformed expected identities and uppercase artifact SHAs', () => {
  assert.throws(
    () => validateArtifact(validArtifact(), { ...expectedIdentity, repository: '../..' }),
    /expected repository/,
  )
  assert.throws(
    () => validateArtifact(validArtifact(), { ...expectedIdentity, baseSha: null }),
    /expected base SHA/,
  )
  const artifact = validArtifact()
  artifact.headSha = 'B'.repeat(40)
  assert.throws(() => validateArtifact(artifact, expectedIdentity), /head SHA/)
})

test('rejects unknown keys at every object level', () => {
  const mutations = [
    artifact => { artifact.untrusted = 'text' },
    artifact => { artifact.scenarios[0].untrusted = 'text' },
    artifact => { artifact.scenarios[0].headRender.untrusted = 'text' },
    artifact => { artifact.scenarios[0].blocks[0].untrusted = 'text' },
    artifact => { artifact.scenarios[0].blocks[0].environment.untrusted = 'text' },
    artifact => { artifact.scenarios[0].blocks[0].samples[0].untrusted = 'text' },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact)
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /unexpected keys/)
  }
})

test('rejects non-object containers and non-finite numeric values', () => {
  assert.throws(() => validateArtifact(null, expectedIdentity), /artifact must be an object/)
  const artifact = validArtifact()
  artifact.scenarios[0].headRender.above16MillisPercent = Number.NaN
  assert.throws(() => validateArtifact(artifact, expectedIdentity), /finite/)

  const raw = validArtifact()
  raw.scenarios[0].blocks[0].environment.contentScale = Number.POSITIVE_INFINITY
  assert.throws(() => validateArtifact(raw, expectedIdentity), /finite/)
})

test('checks five MiB before parsing and accepts only Buffer input', () => {
  assert.throws(
    () => parseArtifactBuffer(Buffer.alloc(5 * 1024 * 1024 + 1), expectedIdentity),
    /five MiB/,
  )
  assert.throws(() => parseArtifactBuffer('{}', expectedIdentity), /Buffer/)
})

test('uses fatal UTF-8 decoding and strict JSON parsing', () => {
  assert.throws(
    () => parseArtifactBuffer(Buffer.from([0x7b, 0x22, 0x78, 0x22, 0x3a, 0xff, 0x7d]), expectedIdentity),
    /UTF-8/,
  )
  assert.throws(() => parseArtifactBuffer(Buffer.from('{'), expectedIdentity), /JSON/)
})

test('strict parsing rejects duplicate JSON object keys', () => {
  const json = JSON.stringify(validArtifact()).replace(
    '"schemaVersion":1',
    '"schemaVersion":1,"schemaVersion":1',
  )

  assert.throws(() => parseArtifactBuffer(Buffer.from(json), expectedIdentity), /duplicate JSON key/)
})

test('parses and validates a valid serialized artifact', () => {
  const validated = parseArtifactBuffer(
    Buffer.from(JSON.stringify(validArtifact()), 'utf8'),
    expectedIdentity,
  )

  assert.match(renderComment(validated, runUrl), /Desktop Glass benchmark/)
})

test('rejects more than 100,000 samples across the artifact', () => {
  const artifact = validArtifact({ headOnly: true })
  artifact.scenarios[0].blocks[0].samples = Array.from(
    { length: 100_001 },
    () => ({ renderDurationNanos: 1, callbackIntervalNanos: 1 }),
  )

  assert.throws(() => validateArtifact(artifact, expectedIdentity), /too many samples/)
})

test('requires exactly the two registered complete scenarios', () => {
  const missing = validArtifact()
  missing.scenarios.pop()
  assert.throws(() => validateArtifact(missing, expectedIdentity), /scenario set/)

  const duplicate = validArtifact()
  duplicate.scenarios[1] = structuredClone(duplicate.scenarios[0])
  assert.throws(() => validateArtifact(duplicate, expectedIdentity), /scenario set/)

  const unknown = validArtifact()
  unknown.scenarios[1].id = 'other'
  unknown.scenarios[1].blocks.forEach(block => { block.scenarioId = 'other' })
  assert.throws(() => validateArtifact(unknown, expectedIdentity), /scenario set/)
})

test('requires deterministic scenario and block ordering', () => {
  const scenarios = validArtifact()
  scenarios.scenarios.reverse()
  assert.throws(() => validateArtifact(scenarios, expectedIdentity), /scenario order/)

  const blocks = validArtifact()
  blocks.scenarios[0].blocks.reverse()
  assert.throws(() => validateArtifact(blocks, expectedIdentity), /block order/)
})

test('rejects a missing, duplicate, or altered ABBA slot', () => {
  const mutations = [
    blocks => { blocks.pop() },
    blocks => { blocks[1] = structuredClone(blocks[0]) },
    blocks => { blocks[0].order = 2 },
    blocks => { blocks[0].round = 3 },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact.scenarios[0].blocks)
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /ABBA|slot|round/)
  }
})

test('rejects a missing or altered head-only slot', () => {
  for (const mutate of [
    blocks => { blocks.pop() },
    blocks => { blocks[0].order = 0 },
  ]) {
    const artifact = validArtifact({ headOnly: true })
    mutate(artifact.scenarios[0].blocks)
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /head-only|slot/)
  }
})

test('rejects mixed ABBA and head-only scenario modes', () => {
  const artifact = validArtifact()
  artifact.scenarios[0] = makeScenario('playground_drag', { headOnly: true })

  assert.throws(() => validateArtifact(artifact, expectedIdentity), /same comparison mode/)
})

test('couples blocks to their scenario, suite, schema, and revision', () => {
  const mutations = [
    block => { block.scenarioId = 'pointer_sweep' },
    block => { block.suiteId = 'other' },
    block => { block.schemaVersion = 2 },
    block => { block.revision = 'local' },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact.scenarios[0].blocks[0])
    assert.throws(() => validateArtifact(artifact, expectedIdentity))
  }
})

test('requires nonempty samples with callback intervals in every block', () => {
  const empty = validArtifact()
  empty.scenarios[0].blocks[0].samples = []
  assert.throws(() => validateArtifact(empty, expectedIdentity), /samples must not be empty/)

  const noIntervals = validArtifact()
  noIntervals.scenarios[0].blocks[0].samples.forEach(sample => {
    sample.callbackIntervalNanos = null
  })
  assert.throws(() => validateArtifact(noIntervals, expectedIdentity), /callback interval/)
})

test('rejects unsafe or negative raw integer fields', () => {
  const mutations = [
    artifact => { artifact.scenarios[0].blocks[0].samples[0].renderDurationNanos = Number.MAX_SAFE_INTEGER + 1 },
    artifact => { artifact.scenarios[0].blocks[0].samples[0].callbackIntervalNanos = -1 },
    artifact => { artifact.scenarios[0].blocks[0].workloadDurationNanos = 1.5 },
    artifact => { artifact.scenarios[0].blocks[0].environment.memoryBytes = Number.MAX_SAFE_INTEGER + 1 },
    artifact => { artifact.scenarios[0].blocks[0].protocolVersion = 0 },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact)
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /safe integer/)
  }
})

test('requires bounded, nonblank, well-formed UTF-8 metadata', () => {
  const invalidValues = ['', ' \t\n', 'é'.repeat(129), '\ud800']
  for (const value of invalidValues) {
    const artifact = validArtifact()
    artifact.scenarios[0].blocks[0].environment.cpu = value
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /cpu/)
  }
})

test('requires optional metadata to be null or bounded nonblank UTF-8', () => {
  for (const [field, value] of [
    ['runnerImage', ''],
    ['runnerImageVersion', 'é'.repeat(129)],
  ]) {
    const artifact = validArtifact()
    artifact.scenarios[0].blocks[0].environment[field] = value
    assert.throws(() => validateArtifact(artifact, expectedIdentity), new RegExp(field))
  }
})

test('requires Metal, exact 1280x720, and positive environment numbers', () => {
  const mutations = [
    environment => { environment.renderApi = 'OPENGL' },
    environment => { environment.framebufferWidth = 1279 },
    environment => { environment.framebufferHeight = 721 },
    environment => { environment.memoryBytes = 0 },
    environment => { environment.contentScale = 0 },
    environment => { environment.refreshRateHz = 0 },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact.scenarios[0].blocks[0].environment)
    assert.throws(() => validateArtifact(artifact, expectedIdentity))
  }
})

test('rejects a supplied render or interval summary inconsistent with raw blocks', () => {
  for (const field of ['headRender', 'headInterval', 'baseRender', 'baseInterval']) {
    const artifact = validArtifact()
    artifact.scenarios[0][field].p95Nanos += 1
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /summary mismatch/)
  }
})

test('rejects supplied count, percentage, variation, and noise tampering', () => {
  const mutations = [
    metric => { metric.sampleCount += 1 },
    metric => { metric.above16MillisCount += 1 },
    metric => { metric.above33MillisPercent += 0.01 },
    metric => { metric.robustVariationPercent += 0.01 },
    metric => { metric.noisy = !metric.noisy },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact.scenarios[0].headRender)
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /summary mismatch/)
  }
})

test('recomputes exact budget boundaries independently for render and interval data', () => {
  const boundarySamples = [
    { renderDurationNanos: 16_666_667, callbackIntervalNanos: null },
    { renderDurationNanos: 16_666_668, callbackIntervalNanos: 16_666_667 },
    { renderDurationNanos: 33_333_333, callbackIntervalNanos: 16_666_668 },
    { renderDurationNanos: 33_333_334, callbackIntervalNanos: 33_333_333 },
    { renderDurationNanos: 10_000_000, callbackIntervalNanos: 33_333_334 },
  ]
  const artifact = validArtifact({ headOnly: true, samples: () => boundarySamples })
  const validated = validateArtifact(artifact, expectedIdentity)
  const summary = validated.scenarios[0]

  assert.equal(summary.headRender.above16MillisCount, 18)
  assert.equal(summary.headRender.above33MillisCount, 6)
  assert.equal(summary.headInterval.above16MillisCount, 18)
  assert.equal(summary.headInterval.above33MillisCount, 6)
})

test('rejects protocol fields and comparability inconsistent with raw blocks', () => {
  const mutations = [
    scenario => { scenario.baseProtocolVersion = 2 },
    scenario => { scenario.headProtocolVersion = 2 },
    scenario => { scenario.comparable = false },
  ]

  for (const mutate of mutations) {
    const artifact = validArtifact()
    mutate(artifact.scenarios[0])
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /protocol|comparability/)
  }
})

test('rejects mixed protocol versions within a revision', () => {
  const artifact = validArtifact()
  artifact.scenarios[0].blocks.find(block => block.revision === 'head').protocolVersion = 2

  assert.throws(() => validateArtifact(artifact, expectedIdentity), /mixes head protocol/)
})

test('rejects paired render and interval delta tampering', () => {
  for (const field of ['renderPairedDeltaPercent', 'intervalPairedDeltaPercent']) {
    const artifact = validArtifact()
    artifact.scenarios[0][field] += 0.01
    assert.throws(() => validateArtifact(artifact, expectedIdentity), /paired delta mismatch/)
  }
})

test('rejects a non-finite delta and a zero base block median', () => {
  const nonFinite = validArtifact()
  nonFinite.scenarios[0].renderPairedDeltaPercent = Number.POSITIVE_INFINITY
  assert.throws(() => validateArtifact(nonFinite, expectedIdentity), /finite/)

  const zeroBase = validArtifact({
    valueFor({ revision, metric }) {
      if (revision === 'base') return 0
      return metric === 'render' ? 11_000_000 : 17_000_000
    },
  })
  zeroBase.scenarios.forEach(scenario => {
    scenario.renderPairedDeltaPercent = 0
    scenario.intervalPairedDeltaPercent = 0
  })
  assert.throws(() => validateArtifact(zeroBase, expectedIdentity), /positive base/)
})

test('complete status requires a null diagnostic', () => {
  const artifact = validArtifact()
  artifact.diagnostic = 'unexpected'

  assert.throws(() => validateArtifact(artifact, expectedIdentity), /complete.*null diagnostic/)
})

test('failed status requires no scenarios and a nonblank diagnostic', () => {
  const withScenario = failedArtifact('failure')
  withScenario.scenarios = validArtifact().scenarios
  assert.throws(() => validateArtifact(withScenario, expectedIdentity), /failed.*no scenarios/)

  for (const diagnostic of [null, '', ' \n\t']) {
    assert.throws(
      () => validateArtifact(failedArtifact(diagnostic), expectedIdentity),
      /failed.*diagnostic/,
    )
  }
})

test('rejects diagnostics over 2,048 UTF-8 bytes', () => {
  assert.throws(
    () => validateArtifact(failedArtifact('é'.repeat(1_025)), expectedIdentity),
    /diagnostic/,
  )
})

test('renders escaped infrastructure failures without a metrics table', () => {
  const artifact = validateArtifact(
    failedArtifact('bad <script> | row\n## injected --> [link](evil)'),
    expectedIdentity,
  )
  const body = renderComment(artifact, runUrl)

  assert.match(body, /Infrastructure failure/)
  assert.doesNotMatch(body, /<script>/)
  assert.doesNotMatch(body, /\n## injected/)
  assert.doesNotMatch(body, /\| Scenario \|/)
  assert.match(body, /Workflow run/)
})

test('escapes every artifact metadata string used in the fixed comment', () => {
  const environment = benchmarkEnvironment({
    cpu: 'M1 | [fake]\n## injected -->',
    runnerImage: 'macos-26 `spoof`',
  })
  const body = renderComment(
    validateArtifact(validArtifact({ environment }), expectedIdentity),
    runUrl,
  )

  assert.doesNotMatch(body, /\n## injected/)
  assert.doesNotMatch(body, /\| \[fake\]/)
  assert.doesNotMatch(body, /`spoof`/)
  assert.match(body, /M1 \\| \\\[fake\\\]\\n\\#\\# injected/)
})

test('renderComment accepts only validator-produced artifacts and a trusted run URL', () => {
  assert.throws(() => renderComment(validArtifact(), runUrl), /validated artifact/)
  const artifact = validateArtifact(validArtifact(), expectedIdentity)
  assert.throws(() => renderComment(artifact, 'https://evil.example/run'), /trusted workflow run URL/)
})

function validArtifact(options = {}) {
  const scenarioOptions = {
    headOnly: options.headOnly ?? false,
    protocols: options.protocols ?? {},
    valueFor: options.valueFor,
    samples: options.samples,
    environment: options.environment,
  }
  return {
    schemaVersion: 1,
    suiteId: 'glass',
    repository: expectedIdentity.repository,
    baseSha: expectedIdentity.baseSha,
    headSha: expectedIdentity.headSha,
    scenarios: [
      makeScenario('playground_drag', scenarioOptions),
      makeScenario('pointer_sweep', scenarioOptions),
    ],
    status: 'complete',
    diagnostic: null,
  }
}

function failedArtifact(diagnostic) {
  return {
    schemaVersion: 1,
    suiteId: 'glass',
    repository: expectedIdentity.repository,
    baseSha: expectedIdentity.baseSha,
    headSha: expectedIdentity.headSha,
    scenarios: [],
    status: 'failed',
    diagnostic,
  }
}

function makeScenario(id, options = {}) {
  const blocks = makeBlocks(id, options)
  const baseBlocks = blocks.filter(block => block.revision === 'base')
  const headBlocks = blocks.filter(block => block.revision === 'head')
  const baseProtocolVersion = baseBlocks[0]?.protocolVersion ?? null
  const headProtocolVersion = headBlocks[0].protocolVersion
  const comparable = baseBlocks.length > 0 && baseProtocolVersion === headProtocolVersion
  return {
    id,
    baseProtocolVersion,
    headProtocolVersion,
    comparable,
    baseRender: baseBlocks.length ? metricSummary(baseBlocks, renderValues) : null,
    headRender: metricSummary(headBlocks, renderValues),
    baseInterval: baseBlocks.length ? metricSummary(baseBlocks, intervalValues) : null,
    headInterval: metricSummary(headBlocks, intervalValues),
    renderPairedDeltaPercent: comparable
      ? pairedDelta(baseBlocks, headBlocks, renderValues)
      : null,
    intervalPairedDeltaPercent: comparable
      ? pairedDelta(baseBlocks, headBlocks, intervalValues)
      : null,
    blocks,
  }
}

function makeBlocks(id, options) {
  const protocols = options.protocols?.[id] ?? { base: 1, head: 1 }
  const blocks = []
  for (let round = 0; round < 3; round += 1) {
    const slots = options.headOnly
      ? [['head', 1], ['head', 2]]
      : [['base', 0], ['head', 1], ['head', 2], ['base', 3]]
    for (const [revision, order] of slots) {
      const render = options.valueFor?.({ id, revision, round, order, metric: 'render' })
        ?? (revision === 'base' ? 10_000_000 : 11_000_000)
      const interval = options.valueFor?.({ id, revision, round, order, metric: 'interval' })
        ?? (revision === 'base' ? 16_000_000 : 17_600_000)
      const samples = options.samples?.({ id, revision, round, order, render, interval }) ?? [
        { renderDurationNanos: render, callbackIntervalNanos: null },
        { renderDurationNanos: render, callbackIntervalNanos: interval },
      ]
      blocks.push({
        schemaVersion: 1,
        suiteId: 'glass',
        scenarioId: id,
        protocolVersion: protocols[revision] ?? 1,
        revision,
        round,
        order,
        environment: structuredClone(options.environment ?? benchmarkEnvironment()),
        workloadDurationNanos: id === 'pointer_sweep' ? 4_000_000_000 : 6_000_000_000,
        samples: structuredClone(samples),
      })
    }
  }
  return blocks
}

function benchmarkEnvironment(overrides = {}) {
  return {
    osName: 'Mac OS X',
    osVersion: '26.0',
    architecture: 'aarch64',
    cpu: 'Apple M1',
    memoryBytes: 7_000_000_000,
    javaVendor: 'Azul Systems, Inc.',
    javaVersion: '21',
    composeVersion: '1.11.1',
    skikoVersion: '0.144.6',
    renderApi: 'METAL',
    framebufferWidth: 1280,
    framebufferHeight: 720,
    contentScale: 2,
    refreshRateHz: 60,
    runnerImage: 'macos-26',
    runnerImageVersion: 'test',
    ...overrides,
  }
}

function metricSummary(blocks, extractValues) {
  const allValues = blocks.flatMap(extractValues)
  const blockMedians = blocks.map(block => median(extractValues(block)))
  const variation = robustVariation(blockMedians)
  const above16MillisCount = allValues.filter(value => value > 16_666_667).length
  const above33MillisCount = allValues.filter(value => value > 33_333_333).length
  return {
    sampleCount: allValues.length,
    p50Nanos: nearestRank(allValues, 0.50),
    p95Nanos: nearestRank(allValues, 0.95),
    p99Nanos: nearestRank(allValues, 0.99),
    above16MillisCount,
    above16MillisPercent: above16MillisCount / allValues.length * 100,
    above33MillisCount,
    above33MillisPercent: above33MillisCount / allValues.length * 100,
    robustVariationPercent: variation,
    noisy: variation > 10,
  }
}

function pairedDelta(baseBlocks, headBlocks, extractValues) {
  const base = baseBlocks.map(block => median(extractValues(block)))
  const head = headBlocks.map(block => median(extractValues(block)))
  return median([0, 1, 2].map(round => {
    const baseMedian = median(base.slice(round * 2, round * 2 + 2))
    const headMedian = median(head.slice(round * 2, round * 2 + 2))
    return (headMedian / baseMedian - 1) * 100
  }))
}

function renderValues(block) {
  return block.samples.map(sample => sample.renderDurationNanos)
}

function intervalValues(block) {
  return block.samples
    .map(sample => sample.callbackIntervalNanos)
    .filter(value => value !== null)
}

function nearestRank(values, percentile) {
  const sorted = [...values].sort((left, right) => left - right)
  const rank = Math.min(sorted.length, Math.max(1, Math.ceil(percentile * sorted.length)))
  return sorted[rank - 1]
}

function robustVariation(values) {
  const center = median(values)
  if (center === 0) return values.every(value => value === 0) ? 0 : 100
  return median(values.map(value => Math.abs(value - center))) / Math.abs(center) * 100
}

function median(values) {
  const sorted = [...values].sort((left, right) => left - right)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1
    ? sorted[middle]
    : sorted[middle - 1] / 2 + sorted[middle] / 2
}
